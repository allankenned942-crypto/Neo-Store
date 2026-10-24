package com.machiav3lli.fdroid.data.index

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.network.IHttpClient
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.Properties
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object AuroraTest {
    private const val DISPENSER = "https://auroraoss.com/api/auth"
    private const val UA = "com.aurora.store-4.8.1-70"

    fun maybeRun(context: Context, address: String) {
        if (!address.startsWith("aurora:")) return
        val pkg = address.removePrefix("aurora:").trim('/')
        val app = context.applicationContext
        thread {
            val msg = try {
                run(app, pkg)
            } catch (t: Throwable) {
                "Aurora ERRO: ${t.javaClass.simpleName}: ${t.message}"
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun run(context: Context, pkg: String): String {
        val client = AuroraHttp()
        val props = deviceProperties(context)
        val map = props.stringPropertyNames().associateWith { props.getProperty(it) }
        val resp = client.postAuth(DISPENSER, JSONObject(map).toString().toByteArray())
        if (!resp.isSuccessful) return "Aurora ERRO login: HTTP ${resp.code}"
        val json = JSONObject(String(resp.responseBytes))
        val email = json.getString("email")
        val token = json.getString("authToken")
        AuthHelper.using(client)
        val authData: AuthData = AuthHelper.build(
            email = email,
            token = token,
            tokenType = AuthHelper.Token.AUTH,
            isAnonymous = true,
            properties = props,
            locale = Locale.getDefault()
        )
        val app = AppDetailsHelper(authData).using(client).getAppByPackageName(pkg)
        return "Aurora OK: ${app.displayName} ${app.versionName} (${app.versionCode})"
    }

    private fun deviceProperties(context: Context): Properties = Properties().apply {
        setProperty("UserReadableName", "${Build.MANUFACTURER} ${Build.MODEL}")
        setProperty("Build.HARDWARE", Build.HARDWARE)
        setProperty("Build.RADIO", Build.getRadioVersion() ?: "unknown")
        setProperty("Build.FINGERPRINT", Build.FINGERPRINT)
        setProperty("Build.BRAND", Build.BRAND)
        setProperty("Build.DEVICE", Build.DEVICE)
        setProperty("Build.VERSION.SDK_INT", "${Build.VERSION.SDK_INT}")
        setProperty("Build.VERSION.RELEASE", Build.VERSION.RELEASE)
        setProperty("Build.MODEL", Build.MODEL)
        setProperty("Build.MANUFACTURER", Build.MANUFACTURER)
        setProperty("Build.PRODUCT", Build.PRODUCT)
        setProperty("Build.ID", Build.ID)
        setProperty("Build.BOOTLOADER", Build.BOOTLOADER)
        val config = context.resources.configuration
        setProperty("TouchScreen", "${config.touchscreen}")
        setProperty("Keyboard", "${config.keyboard}")
        setProperty("Navigation", "${config.navigation}")
        setProperty("ScreenLayout", "${config.screenLayout and 15}")
        setProperty("HasHardKeyboard", "${config.keyboard == Configuration.KEYBOARD_QWERTY}")
        setProperty("HasFiveWayNavigation", "${config.navigation == Configuration.NAVIGATIONHIDDEN_YES}")
        val m = context.resources.displayMetrics
        setProperty("Screen.Density", "${m.densityDpi}")
        setProperty("Screen.Width", "${m.widthPixels}")
        setProperty("Screen.Height", "${m.heightPixels}")
        setProperty("Platforms", Build.SUPPORTED_ABIS.joinToString(","))
        setProperty("Features", context.packageManager.systemAvailableFeatures.mapNotNull { it.name }.joinToString(","))
        setProperty("Locales", context.assets.locales.map { it.replace("-", "_") }.joinToString(","))
        setProperty("SharedLibraries", (context.packageManager.systemSharedLibraryNames?.toList() ?: emptyList()).joinToString(","))
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        setProperty("GL.Version", am.deviceConfigurationInfo.reqGlEsVersion.toString())
        setProperty("GL.Extensions", "GL_OES_texture_float,GL_OES_egl_image,GL_EXT_texture_filter_anisotropic")
        setProperty("Client", "android-google")
        setProperty("GSF.version", "203019037")
        setProperty("Vending.version", "82151710")
        setProperty("Vending.versionString", "21.5.17-21 [0] [PR] 326734551")
        setProperty("Roaming", "mobile-notroaming")
        setProperty("TimeZone", "UTC-10")
        setProperty("CellOperator", "310")
        setProperty("SimOperator", "38")
    }

    private class AuroraHttp : IHttpClient {
        private val _code = MutableStateFlow(100)
        override val responseCode: StateFlow<Int> get() = _code.asStateFlow()

        private fun req(method: String, url: String, headers: Map<String, String>, body: ByteArray?): PlayResponse {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 20000
            c.readTimeout = 30000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val ok = code in 200..299
            val bytes = (if (ok) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
            _code.value = code
            return PlayResponse(
                isSuccessful = ok,
                code = code,
                responseBytes = bytes,
                errorString = if (ok) "" else (c.responseMessage ?: "")
            )
        }

        private fun withParams(url: String, params: Map<String, String>): String {
            if (params.isEmpty()) return url
            val b = Uri.parse(url).buildUpon()
            params.forEach { (k, v) -> b.appendQueryParameter(k, v) }
            return b.build().toString()
        }

        override fun post(url: String, headers: Map<String, String>, params: Map<String, String>): PlayResponse =
            req("POST", withParams(url, params), headers, ByteArray(0))

        override fun postAuth(url: String, body: ByteArray): PlayResponse =
            req("POST", url, mapOf("User-Agent" to UA, "Content-Type" to "application/json"), body)

        override fun post(url: String, headers: Map<String, String>, body: ByteArray): PlayResponse =
            req("POST", url, headers, body)

        override fun get(url: String, headers: Map<String, String>): PlayResponse =
            req("GET", url, headers, null)

        override fun get(url: String, headers: Map<String, String>, params: Map<String, String>): PlayResponse =
            req("GET", withParams(url, params), headers, null)

        override fun getAuth(url: String): PlayResponse =
            req("GET", url, mapOf("User-Agent" to UA), null)

        override fun get(url: String, headers: Map<String, String>, paramString: String): PlayResponse =
            req("GET", "$url$paramString", headers, null)
    }
}
