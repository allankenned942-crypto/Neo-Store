package com.machiav3lli.fdroid.data.index

import android.content.Context
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.machiav3lli.fdroid.data.database.entity.IndexProduct
import com.machiav3lli.fdroid.data.database.entity.Release
import com.machiav3lli.fdroid.data.database.entity.Repository
import com.machiav3lli.fdroid.data.entity.Author
import com.machiav3lli.fdroid.data.index.RepositoryUpdater.ErrorType
import com.machiav3lli.fdroid.data.index.RepositoryUpdater.UpdateException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuroraSource {
    class Fetched(
        val product: IndexProduct,
        val label: String,
        val description: String,
        val tag: String,
        val timestamp: Long,
    )

    fun isAurora(address: String): Boolean = address.startsWith("aurora:")

    fun packageOf(address: String): String = address.removePrefix("aurora:").trim('/')

    suspend fun fetch(context: Context, repository: Repository): Fetched? =
        withContext(Dispatchers.IO) {
            val pkg = packageOf(repository.address)
            if (pkg.isEmpty() || !pkg.contains('.')) {
                throw UpdateException(ErrorType.VALIDATION, "Pacote invalido: $pkg")
            }
            val app = try {
                val auth = AuroraSession.get(context)
                AppDetailsHelper(auth).using(AuroraSession.client).getAppByPackageName(pkg)
            } catch (e: Exception) {
                throw UpdateException(ErrorType.NETWORK, "Aurora: ${e.message}", e)
            }
            if (app.displayName.isBlank() || app.versionCode.toLong() == 0L) {
                throw UpdateException(ErrorType.PARSING, "App nao encontrado no Play: $pkg")
            }
            val tag = "${app.versionCode}-m2"
            if (repository.lastModified == tag) return@withContext null

            val now = System.currentTimeMillis()
            val label = app.displayName
            val rel = Release(
                packageName = pkg,
                repositoryId = repository.id,
                selected = false,
                version = app.versionName,
                versionCode = app.versionCode.toLong(),
                added = now,
                size = app.size,
                minSdkVersion = 0,
                targetSdkVersion = 0,
                maxSdkVersion = 0,
                source = "",
                release = "aurora:$pkg",
                hash = "aurora-$tag",
                hashType = "SHA-256",
                signature = "aurora",
                obbMain = "",
                obbMainHash = "",
                obbMainHashType = "",
                obbPatch = "",
                obbPatchHash = "",
                obbPatchHashType = "",
                permissions = emptyList(),
                features = emptyList(),
                platforms = emptyList(),
                incompatibilities = emptyList(),
                isCompatible = true,
            )
            val product = IndexProduct(repository.id, pkg).apply {
                this.label = label
                this.summary = app.shortDescription.ifBlank { app.developerName.ifBlank { "Google Play" } }
                this.description = app.description.ifBlank { app.shortDescription }
                    .replace("\n", "<br/>").ifBlank { "Google Play ($pkg)" }
                this.whatsNew = app.changes
                this.icon = app.iconArtwork.url.let {
                    if (it.contains("googleusercontent.com") && !it.contains("=")) "$it=s256" else it
                }
                this.screenshots = app.screenshots.map { it.url }.filter { it.isNotBlank() }
                this.author = Author(app.developerName, app.developerEmail, app.developerWebsite)
                this.added = now
                this.updated = now
                this.releases = listOf(rel)
                this.source = "https://play.google.com/store/apps/details?id=$pkg"
                this.web = "https://play.google.com/store/apps/details?id=$pkg"
            }
            Fetched(product, label, "Google Play: $pkg", tag, now)
        }
}
