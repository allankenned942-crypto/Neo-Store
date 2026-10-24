package com.machiav3lli.fdroid.data.index

import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.network.IHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AuroraOkHttp : IHttpClient {
    private val ok = OkHttpClient()
    private val _code = MutableStateFlow(100)
    override val responseCode: StateFlow<Int> get() = _code.asStateFlow()

    private val ua = "com.aurora.store-4.8.1-70"

    private fun exec(req: Request): PlayResponse {
        _code.value = 0
        ok.newCall(req).execute().use { r ->
            val bytes = r.body.bytes()
            _code.value = r.code
            return PlayResponse(
                isSuccessful = r.isSuccessful,
                code = r.code,
                responseBytes = bytes,
                errorString = if (!r.isSuccessful) r.message else ""
            )
        }
    }

    private fun buildUrl(url: String, params: Map<String, String>): HttpUrl {
        val b = url.toHttpUrl().newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    override fun post(url: String, headers: Map<String, String>, params: Map<String, String>): PlayResponse =
        exec(Request.Builder().url(buildUrl(url, params)).headers(headers.toHeaders()).method("POST", "".toRequestBody(null)).build())

    override fun postAuth(url: String, body: ByteArray): PlayResponse =
        exec(
            Request.Builder().url(url.toHttpUrl())
                .headers(mapOf("User-Agent" to ua).toHeaders())
                .method("POST", body.toRequestBody("application/json".toMediaType(), 0, body.size))
                .build()
        )

    override fun post(url: String, headers: Map<String, String>, body: ByteArray): PlayResponse =
        exec(Request.Builder().url(url.toHttpUrl()).headers(headers.toHeaders()).method("POST", body.toRequestBody()).build())

    override fun get(url: String, headers: Map<String, String>): PlayResponse =
        get(url, headers, mapOf())

    override fun get(url: String, headers: Map<String, String>, params: Map<String, String>): PlayResponse =
        exec(Request.Builder().url(buildUrl(url, params)).headers(headers.toHeaders()).method("GET", null).build())

    override fun getAuth(url: String): PlayResponse =
        exec(Request.Builder().url(url.toHttpUrl()).headers(mapOf("User-Agent" to ua).toHeaders()).method("GET", null).build())

    override fun get(url: String, headers: Map<String, String>, paramString: String): PlayResponse =
        exec(Request.Builder().url("$url$paramString".toHttpUrl()).headers(headers.toHeaders()).method("GET", null).build())
}
