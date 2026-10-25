package com.machiav3lli.fdroid.data.index

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object ForgeSearch {
    class Hit(
        val name: String,
        val url: String,
        val desc: String,
        val stars: Int,
        val host: String,
        val icon: String = "",
    )

    private val ok = OkHttpClient()

    private fun get(url: HttpUrl): String? =
        ok.newCall(
            Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "LojaDeLojas")
                .build()
        ).execute().use { if (it.isSuccessful) it.body.string() else null }

    private fun str(o: JSONObject, k: String): String =
        if (o.isNull(k)) "" else o.optString(k)

    private fun avatar(o: JSONObject): String =
        o.optJSONObject("owner")?.let { str(it, "avatar_url") }.orEmpty()

    suspend fun search(q: String): List<Hit> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Hit>()
        runCatching {
            val u = "https://api.github.com/search/repositories".toHttpUrl().newBuilder()
                .addQueryParameter("q", "$q in:name,description")
                .addQueryParameter("per_page", "10").build()
            get(u)?.let { t ->
                val a = JSONObject(t).optJSONArray("items")
                if (a != null) for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    out.add(Hit(str(o, "full_name"), str(o, "html_url"), str(o, "description"), o.optInt("stargazers_count"), "GitHub", avatar(o)))
                }
            }
        }
        runCatching {
            val u = "https://codeberg.org/api/v1/repos/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("limit", "10").build()
            get(u)?.let { t ->
                val a = JSONObject(t).optJSONArray("data")
                if (a != null) for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    out.add(Hit(str(o, "full_name"), str(o, "html_url"), str(o, "description"), o.optInt("stars_count"), "Codeberg", avatar(o)))
                }
            }
        }
        out.filter { it.url.isNotBlank() }.sortedByDescending { it.stars }
    }
}
