package com.machiav3lli.fdroid.data.index

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import com.machiav3lli.fdroid.data.content.Cache
import com.machiav3lli.fdroid.data.database.entity.IndexProduct
import com.machiav3lli.fdroid.data.database.entity.Release
import com.machiav3lli.fdroid.data.database.entity.Repository
import com.machiav3lli.fdroid.data.index.RepositoryUpdater.ErrorType
import com.machiav3lli.fdroid.data.index.RepositoryUpdater.Stage
import com.machiav3lli.fdroid.data.index.RepositoryUpdater.UpdateException
import com.machiav3lli.fdroid.manager.network.Downloader
import com.machiav3lli.fdroid.manager.network.HttpResponseException
import com.machiav3lli.fdroid.utils.extension.android.Android
import com.machiav3lli.fdroid.utils.extension.android.signerSHA256Signatures
import com.machiav3lli.fdroid.utils.extension.android.versionCodeCompat
import com.machiav3lli.fdroid.utils.extension.text.hex
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.Instant

@Serializable
private class GhAsset(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val url: String = "",
)

@Serializable
private class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

object GithubSource {
    private val json = Json { ignoreUnknownKeys = true }

    class Fetched(
        val product: IndexProduct,
        val label: String,
        val description: String,
        val tag: String,
        val timestamp: Long,
    )

    private fun parse(address: String): Pair<String, String>? {
        val uri = runCatching { address.toUri() }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "github.com" && host != "www.github.com") return null
        val seg = uri.pathSegments.filter { it.isNotBlank() }
        if (seg.size < 2) return null
        // aceita github.com/dono/repo e github.com/dono/repo/releases...
        // (outros caminhos podem ser repositorio F-Droid hospedado no GitHub)
        if (seg.size > 2 && seg[2] != "releases") return null
        return seg[0] to seg[1].removeSuffix(".git")
    }

    fun isGithub(address: String): Boolean = parse(address) != null

    /**
     * Retorna null se nada mudou desde a ultima sincronizacao.
     */
    suspend fun fetch(
        context: Context,
        repository: Repository,
        callback: (Stage, Long, Long?) -> Unit,
    ): Fetched? {
        val (owner, repo) = parse(repository.address)
            ?: throw UpdateException(ErrorType.VALIDATION, "Endereco GitHub invalido")

        val release = fetchLatestRelease(context, owner, repo)
        if (release.tagName.isNotEmpty() && repository.lastModified == release.tagName) return null

        val asset = pickAsset(release.assets)
            ?: throw UpdateException(ErrorType.PARSING, "Nenhum .apk na release ${release.tagName}")

        val apk = Cache.getTemporaryFile(context)
        try {
            val result = try {
                Downloader.download(
                    url = asset.url,
                    target = apk,
                    lastModified = "",
                    entityTag = "",
                    authentication = "",
                    rated = true,
                ) { read, total, _ -> callback(Stage.DOWNLOAD, read, total) }
            } catch (e: Exception) {
                throw mapException(e)
            }
            if (!result.success) {
                throw UpdateException(ErrorType.HTTP, "Download do APK: HTTP ${result.statusCode}")
            }

            val hash = MessageDigest.getInstance("SHA-256").let { md ->
                apk.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    generateSequence { input.read(buffer) }.takeWhile { it != -1 }
                        .forEach { md.update(buffer, 0, it) }
                }
                md.digest().hex()
            }

            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(
                apk.path,
                PackageManager.GET_PERMISSIONS or Android.PackageManager.signaturesFlag
            ) ?: throw UpdateException(ErrorType.PARSING, "Nao consegui ler o APK ${asset.name}")
            val appInfo = info.applicationInfo?.apply {
                sourceDir = apk.path
                publicSourceDir = apk.path
            }
            val signature = info.signerSHA256Signatures.lastOrNull()?.takeIf { it.isNotEmpty() }
                ?: throw UpdateException(ErrorType.PARSING, "APK sem assinatura")

            val label = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: repo
            val body = release.body.orEmpty()
            val timestamp = release.publishedAt
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: System.currentTimeMillis()
            val summary = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?.take(120) ?: "$owner/$repo"
            val versionName = info.versionName?.takeIf { it.isNotEmpty() } ?: release.tagName

            val rel = Release(
                packageName = info.packageName,
                repositoryId = repository.id,
                selected = false,
                version = versionName,
                versionCode = info.versionCodeCompat,
                added = timestamp,
                size = apk.length(),
                minSdkVersion = appInfo?.minSdkVersion ?: 0,
                targetSdkVersion = appInfo?.targetSdkVersion ?: 0,
                maxSdkVersion = 0,
                source = "",
                release = asset.url,
                hash = hash,
                hashType = "SHA-256",
                signature = signature,
                obbMain = "",
                obbMainHash = "",
                obbMainHashType = "",
                obbPatch = "",
                obbPatchHash = "",
                obbPatchHashType = "",
                permissions = info.requestedPermissions?.toList().orEmpty(),
                features = emptyList(),
                platforms = emptyList(),
                incompatibilities = emptyList(),
                isCompatible = true,
            )

            val product = IndexProduct(repository.id, info.packageName).apply {
                this.label = label
                this.summary = summary
                this.description = body
                this.added = timestamp
                this.updated = timestamp
                this.releases = listOf(rel)
                this.source = "https://github.com/$owner/$repo"
                this.web = release.htmlUrl
            }

            return Fetched(
                product = product,
                label = label,
                description = "$owner/$repo",
                tag = release.tagName,
                timestamp = timestamp,
            )
        } finally {
            apk.delete()
        }
    }

    private fun pickAsset(assets: List<GhAsset>): GhAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) && it.url.isNotEmpty() }
        if (apks.isEmpty()) return null
        for (abi in Build.SUPPORTED_ABIS) {
            apks.firstOrNull {
                val n = it.name.lowercase()
                n.contains(abi.lowercase()) && !(abi == "x86" && n.contains("x86_64"))
            }?.let { return it }
        }
        return apks.firstOrNull { it.name.contains("universal", ignoreCase = true) } ?: apks.first()
    }

    private suspend fun fetchLatestRelease(context: Context, owner: String, repo: String): GhRelease {
        val base = "https://api.github.com/repos/$owner/$repo"
        val (status, text) = getText(context, "$base/releases/latest")
        if (status.value in 200..299) {
            return runCatching { json.decodeFromString<GhRelease>(text) }.getOrElse {
                throw UpdateException(ErrorType.PARSING, "Resposta do GitHub invalida", it)
            }
        }
        if (status != HttpStatusCode.NotFound) {
            throw UpdateException(ErrorType.HTTP, "GitHub API: HTTP $status (limite de requisicoes?)")
        }
        // so existe pre-release (ou nenhuma release)
        val (status2, text2) = getText(context, "$base/releases?per_page=1")
        if (status2.value !in 200..299) {
            throw UpdateException(ErrorType.HTTP, "GitHub API: HTTP $status2")
        }
        val list = runCatching { json.decodeFromString<List<GhRelease>>(text2) }.getOrElse {
            throw UpdateException(ErrorType.PARSING, "Resposta do GitHub invalida", it)
        }
        return list.firstOrNull()
            ?: throw UpdateException(ErrorType.HTTP, "Esse repositorio nao tem releases")
    }

    private suspend fun getText(context: Context, url: String): Pair<HttpStatusCode, String> {
        val file: File = Cache.getTemporaryFile(context)
        try {
            val result = try {
                Downloader.download(
                    url = url,
                    target = file,
                    lastModified = "",
                    entityTag = "",
                    authentication = "",
                    rated = false,
                ) { _, _, _ -> }
            } catch (e: Exception) {
                throw mapException(e)
            }
            val text = if (result.success && file.exists()) file.readText() else ""
            return result.statusCode to text
        } finally {
            file.delete()
        }
    }

    private fun mapException(e: Exception): UpdateException =
        if (e is UpdateException) e
        else if (e is HttpResponseException) UpdateException(
            ErrorType.HTTP,
            "Invalid response ${e.responseStatus.value}: ${e.responseStatus.description}.\n${e.message}",
            e
        ) else UpdateException(
            ErrorType.NETWORK,
            "Network error: ${e.message}\n${e.cause?.message}",
            e
        )
}
