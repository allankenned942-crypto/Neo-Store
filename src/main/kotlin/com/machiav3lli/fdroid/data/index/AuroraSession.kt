package com.machiav3lli.fdroid.data.index

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.serializers.LocaleSerializer
import com.aurora.gplayapi.data.serializers.PropertiesSerializer
import com.aurora.gplayapi.helpers.AuthHelper
import java.io.IOException
import java.util.Locale
import java.util.Properties
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.json.JSONObject

object AuroraSession {
    private const val DISPENSER = "https://auroraoss.com/api/auth"
    val client by lazy { AuroraOkHttp() }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        serializersModule = SerializersModule {
            contextual(LocaleSerializer)
            contextual(PropertiesSerializer)
        }
    }

    @Synchronized
    fun get(context: Context): AuthData {
        val prefs = context.getSharedPreferences("aurora_session", Context.MODE_PRIVATE)
        prefs.getString("auth", null)?.let { s ->
            val cached = runCatching { json.decodeFromString(AuthData.serializer(), s) }.getOrNull()
            if (cached != null &&
                runCatching { AuthHelper.using(client).isValid(cached) }.getOrDefault(false)
            ) return cached
        }
        val fresh = login(context)
        runCatching {
            prefs.edit().putString("auth", json.encodeToString(AuthData.serializer(), fresh)).apply()
        }
        return fresh
    }

    private fun login(context: Context): AuthData {
        val props = deviceProperties(context)
        val body = Json.encodeToString(PropertiesSerializer, props).toByteArray()
        val resp = client.postAuth(DISPENSER, body)
        if (!resp.isSuccessful) throw IOException("Aurora login: HTTP ${resp.code}")
        val j = JSONObject(String(resp.responseBytes))
        return AuthHelper.build(
            email = j.getString("email"),
            token = j.getString("authToken"),
            tokenType = AuthHelper.Token.AUTH,
            isAnonymous = true,
            properties = props,
            locale = Locale.getDefault()
        )
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
        setProperty("GL.Extensions", EglExtensionProvider.eglExtensions.joinToString(","))
        setProperty("Client", "android-google")
        setProperty("GSF.version", "203019037")
        setProperty("Vending.version", "82151710")
        setProperty("Vending.versionString", "21.5.17-21 [0] [PR] 326734551")
        setProperty("Roaming", "mobile-notroaming")
        setProperty("TimeZone", "UTC-10")
        setProperty("CellOperator", "310")
        setProperty("SimOperator", "38")
    }
}
