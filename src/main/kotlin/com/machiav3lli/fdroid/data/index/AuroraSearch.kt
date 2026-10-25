package com.machiav3lli.fdroid.data.index

import android.content.Context
import com.aurora.gplayapi.helpers.SearchHelper
import com.aurora.gplayapi.helpers.contracts.SearchContract
import com.aurora.gplayapi.helpers.web.WebSearchHelper
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuroraSearch {
    class Hit(val pkg: String, val name: String, val icon: String = "")

    suspend fun search(context: Context, query: String): List<Hit> =
        withContext(Dispatchers.IO) {
            var last: Exception? = null
            val tries = listOf<() -> SearchContract>(
                { WebSearchHelper().using(AuroraSession.client).with(Locale.getDefault()) },
                { SearchHelper(AuroraSession.get(context)).using(AuroraSession.client) },
            )
            for (t in tries) {
                try {
                    val list = t().searchResults(query, "")
                        .streamClusters.values
                        .flatMap { it.clusterAppList }
                        .filter { it.packageName.isNotBlank() }
                        .distinctBy { it.packageName }
                        .map { Hit(it.packageName, it.displayName, it.iconArtwork.url.let { u -> if (u.contains("googleusercontent.com") && !u.contains("=")) "$u=s128" else u }) }
                    if (list.isNotEmpty()) return@withContext list
                } catch (e: Exception) {
                    last = e
                }
            }
            last?.let { throw it }
            emptyList()
        }
}
