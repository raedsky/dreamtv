package com.mytv.player.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.gson.Gson
import com.mytv.player.App
import com.mytv.player.PanelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest

data class PanelDevice(val mac: String?, val status: String?, val allowed: Boolean, val expires_at: Long?)
data class PanelDns(val id: Long, val name: String?, val url: String?, val type: String? = null)
data class PanelPlaylist(
    val id: Long, val name: String?, val kind: String?, val url: String?,
    val server: String?, val username: String?, val password: String?
)
data class PanelMessage(val id: Long, val title: String?, val body: String?)
data class PanelState(
    val device: PanelDevice?,
    val dns: List<PanelDns>?,
    val playlists: List<PanelPlaylist>?,
    val message: PanelMessage?
)

/** الاتصال بلوحة التحكم (api.php). */
object Panel {
    private val gson = Gson()

    /**
     * رمز الجهاز (بشكل MAC) ومفتاح سري، مشتقّان من معرّف الجهاز في أندرويد
     * فيبقيان ثابتين حتى بعد حذف التطبيق وتثبيته من جديد.
     */
    @SuppressLint("HardwareIds")
    fun deviceIds(ctx: Context): Pair<String, String> {
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: fallbackId(ctx)
        val hash = MessageDigest.getInstance("SHA-256").digest("mytv:$androidId".toByteArray())
        val macBytes = hash.copyOfRange(0, 6)
        macBytes[0] = ((macBytes[0].toInt() or 0x02) and 0xFE).toByte()   // MAC محلي (ليس لشركة حقيقية)
        val mac = macBytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
        val key = hash.copyOfRange(6, 22).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return mac to key
    }

    private fun fallbackId(ctx: Context): String {
        val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return sp.getString("fallback_id", null) ?: java.util.UUID.randomUUID().toString().also {
            sp.edit().putString("fallback_id", it).apply()
        }
    }

    /** يتصل باللوحة ويحفظ النتيجة. يرجع null إذا تعذّر الاتصال. */
    suspend fun sync(ctx: Context): PanelState? = withContext(Dispatchers.IO) {
        if (!PanelConfig.enabled) return@withContext null
        val (mac, key) = deviceIds(ctx)
        val version = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: ""
        fun e(s: String) = URLEncoder.encode(s, "UTF-8")
        val url = PanelConfig.API_URL +
            (if ('?' in PanelConfig.API_URL) "&" else "?") +
            "key=${e(PanelConfig.API_KEY)}&mac=${e(mac)}&dkey=${e(key)}" +
            "&model=${e("${Build.MANUFACTURER} ${Build.MODEL}")}&v=${e(version)}"
        runCatching {
            App.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val body = r.body?.string() ?: return@use null
                if (!r.isSuccessful) return@use null
                val state = gson.fromJson(body, PanelState::class.java)
                if (state?.device != null) App.instance.prefs.panelCache = body
                state
            }
        }.getOrNull()
    }

    /** آخر نتيجة محفوظة (للعمل بدون إنترنت). */
    fun cached(): PanelState? = App.instance.prefs.panelCache?.let {
        runCatching { gson.fromJson(it, PanelState::class.java) }.getOrNull()
    }

    /** يحوّل قوائم اللوحة إلى قوائم تشغيل في التطبيق. */
    fun toProfiles(list: List<PanelPlaylist>): List<Profile> = list.mapNotNull { p ->
        val id = "panel:${p.id}"
        val name = p.name?.takeIf { it.isNotBlank() } ?: "Playlist"
        if (p.kind == "m3u") {
            val url = p.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Profile(Profile.M3U, id = id, name = name, m3uUrl = url)
        } else {
            val server = p.server?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Profile(Profile.XTREAM, id = id, name = name, server = server,
                username = p.username.orEmpty(), password = p.password.orEmpty())
        }
    }
}
