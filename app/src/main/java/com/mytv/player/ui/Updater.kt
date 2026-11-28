package com.mytv.player.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.mytv.player.App
import com.mytv.player.PanelConfig
import com.mytv.player.R
import com.mytv.player.data.Panel
import com.mytv.player.data.PanelUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/** التحديث من داخل التطبيق: يفحص آخر نسخة، يحمّلها، ويفتح شاشة التثبيت. */
object Updater {

    /** رابط احتياطي للتطبيق الرئيسي إذا لم ترسل اللوحة معلومات التحديث */
    private const val FALLBACK_URL = "https://github.com/raedsky/dreamtv/releases/latest/download/version.json"

    fun currentCode(ctx: Context): Long = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0)

    fun currentName(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()

    /** آخر نسخة: من اللوحة (يدعم تطبيقات الموزعين) وإلا من GitHub */
    suspend fun latest(ctx: Context, refresh: Boolean): PanelUpdate? {
        var u: PanelUpdate? = null
        if (PanelConfig.enabled) {
            val st = if (refresh) (Panel.sync(ctx) ?: Panel.cached()) else Panel.cached()
            u = st?.update
        }
        if (u == null && PanelConfig.RESELLER.isBlank()) {
            u = withContext(Dispatchers.IO) {
                runCatching {
                    App.http.newCall(Request.Builder().url(FALLBACK_URL).build()).execute().use { r ->
                        if (!r.isSuccessful) null else Gson().fromJson(r.body?.string(), PanelUpdate::class.java)
                    }
                }.getOrNull()
            }
        }
        return u?.takeIf { !it.url.isNullOrBlank() }
    }

    fun isNewer(ctx: Context, u: PanelUpdate?) = u != null && u.code > currentCode(ctx)

    /** يحمّل النسخة الجديدة ثم يفتح شاشة التثبيت */
    fun downloadAndInstall(act: Activity, u: PanelUpdate) {
        // أندرويد 8+: لازم السماح للتطبيق بتثبيت التطبيقات مرة وحدة
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(act)
                .setTitle(R.string.update_app)
                .setMessage(R.string.update_allow_install)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    runCatching {
                        act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.packageName)))
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        val pad = (20 * act.resources.displayMetrics.density).toInt()
        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val label = TextView(act).apply { text = act.getString(R.string.update_downloading, 0) }
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad / 2)
            addView(label)
            addView(bar)
        }
        val dlg = AlertDialog.Builder(act).setTitle(R.string.update_app).setView(box).setCancelable(false).show()

        Thread {
            val result = runCatching {
                val dir = File(act.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "update.apk")
                App.http.newCall(Request.Builder().url(u.url!!).build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    val body = r.body ?: error("empty")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var last = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != last) {
                                        last = pct
                                        act.runOnUiThread {
                                            bar.progress = pct
                                            label.text = act.getString(R.string.update_downloading, pct)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                file
            }
            act.runOnUiThread {
                runCatching { dlg.dismiss() }
                result.onSuccess { install(act, it) }
                result.onFailure {
                    Toast.makeText(act, act.getString(R.string.update_failed, it.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun install(act: Activity, file: File) {
        val uri = FileProvider.getUriForFile(act, act.packageName + ".updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { act.startActivity(intent) }.onFailure {
            Toast.makeText(act, act.getString(R.string.update_failed, it.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    /** نافذة "يوجد تحديث" */
    fun offer(act: Activity, u: PanelUpdate) {
        AlertDialog.Builder(act)
            .setTitle(R.string.update_available)
            .setMessage(act.getString(R.string.update_available_msg, u.name ?: u.code.toString(), currentName(act)))
            .setPositiveButton(R.string.update_now_btn) { _, _ -> downloadAndInstall(act, u) }
            .setNegativeButton(R.string.later, null)
            .show()
    }
}
