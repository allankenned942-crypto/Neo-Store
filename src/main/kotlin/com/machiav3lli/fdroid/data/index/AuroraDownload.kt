package com.machiav3lli.fdroid.data.index

import android.content.Context
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.machiav3lli.fdroid.data.content.Cache
import com.machiav3lli.fdroid.data.entity.DownloadTask
import com.machiav3lli.fdroid.manager.network.Downloader
import io.ktor.http.HttpStatusCode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object AuroraDownload {
    private val ok = OkHttpClient()

    suspend fun run(
        context: Context,
        task: DownloadTask,
        partial: File,
        callback: suspend (Long, Long?, Long) -> Unit,
    ): Downloader.Result = withContext(Dispatchers.IO) {
        val auth = AuroraSession.get(context)
        val client = AuroraSession.client
        val app = AppDetailsHelper(auth).using(client).getAppByPackageName(task.packageName)
        val files = PurchaseHelper(auth).using(client)
            .purchase(task.packageName, app.versionCode.toLong(), app.offerType)
        if (files.isEmpty()) throw IOException("Play nao entregou arquivos")
        if (files.any { it.type.name != "BASE" && it.type.name != "SPLIT" }) {
            throw IOException("App com OBB/patch ainda nao suportado")
        }
        val base = files.first { it.type.name == "BASE" }
        val splits = files.filter { it.type.name == "SPLIT" }
        val total = files.sumOf { it.size }
        var done = 0L

        val rel = Cache.getReleaseFile(context, task.release.cacheFileName)
        rel.parentFile?.listFiles { f -> f.name.startsWith(rel.name + ".split") }
            ?.forEach { it.delete() }

        suspend fun fetch(url: String, target: File) {
            ok.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Download Play: HTTP ${r.code}")
                r.body.byteStream().use { input ->
                    FileOutputStream(target).use { out ->
                        val buf = ByteArray(65536)
                        var n: Int
                        var last = 0L
                        while (input.read(buf).also { n = it } != -1) {
                            coroutineContext.ensureActive()
                            out.write(buf, 0, n)
                            done += n
                            if (done - last > 262144) {
                                last = done
                                callback(done, total, -1L)
                            }
                        }
                    }
                }
            }
        }

        splits.forEachIndexed { i, f ->
            fetch(f.url, Cache.getReleaseFile(context, "${task.release.cacheFileName}.split$i"))
        }
        fetch(base.url, partial)
        callback(total, total, -1L)
        Downloader.Result(HttpStatusCode.OK, "", "")
    }
}
