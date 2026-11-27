package com.mytv.player.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mytv.player.App
import com.mytv.player.R
import com.mytv.player.PanelConfig
import com.mytv.player.data.ContentType
import com.mytv.player.data.Panel
import com.mytv.player.data.Prefs
import com.mytv.player.data.Profile
import com.mytv.player.data.Weather
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** الشاشة الرئيسية — أول شاشة تفتح في التطبيق. */
class HomeActivity : AppCompatActivity() {

    private val app get() = App.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val tileLive = findViewById<View>(R.id.tileLive)
        tileLive.setOnClickListener { openSection(ContentType.LIVE) }
        findViewById<View>(R.id.tileMovies).setOnClickListener { openSection(ContentType.MOVIE) }
        findViewById<View>(R.id.tileSeries).setOnClickListener { openSection(ContentType.SERIES) }

        findViewById<View>(R.id.btnPlaylists).setOnClickListener { showPlaylists() }
        findViewById<View>(R.id.btnReload).setOnClickListener {
            app.repo.clear()
            Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<View>(R.id.btnExit).setOnClickListener { finishAffinity() }
        findViewById<View>(R.id.btnAccount).setOnClickListener { showAccount() }

        // تكبير بسيط للعنصر المحدد بالريموت
        for (id in intArrayOf(R.id.tileLive, R.id.tileMovies, R.id.tileSeries,
                R.id.btnPlaylists, R.id.btnAccount, R.id.btnReload, R.id.btnSettings, R.id.btnExit)) {
            findViewById<View>(id).setOnFocusChangeListener { v, has ->
                val s = if (has) (if (v.id == R.id.tileLive || v.id == R.id.tileMovies || v.id == R.id.tileSeries) 1.06f else 1.15f) else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            }
        }
        tileLive.requestFocus()
        // في نظام 24 ساعة نخفي ص/م
        findViewById<View>(R.id.tvAmPm).visibility =
            if (android.text.format.DateFormat.is24HourFormat(this)) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        refreshInfo()
        loadWeather()
        syncPanel()
    }

    // ---------------- لوحة التحكم ----------------

    private fun syncPanel() {
        if (!PanelConfig.enabled) return
        lifecycleScope.launch {
            val st = Panel.sync(this@HomeActivity) ?: Panel.cached() ?: return@launch
            val device = st.device ?: return@launch
            if (!device.allowed) {
                ActivationActivity.start(this@HomeActivity, device.status)
                finish()
                return@launch
            }
            // قوائم التشغيل المضافة من اللوحة لهذا الجهاز
            st.playlists?.let { pls ->
                if (app.prefs.syncPanelPlaylists(Panel.toProfiles(pls))) app.repo.clear()
            }
            // تاريخ انتهاء اشتراك Xtream للقوائم القادمة من اللوحة
            app.prefs.profile?.takeIf {
                it.id.startsWith("panel:") && it.kind == Profile.XTREAM && it.expiry == null && expiryChecked.add(it.id)
            }?.let { p -> app.repo.fetchExpiry(p)?.let { app.prefs.saveProfile(p.copy(expiry = it)) } }
            panelDevice = device
            refreshInfo()
            // رسالة المدير (مرة واحدة لكل رسالة)
            st.message?.let { m ->
                if (m.id > app.prefs.seenMessageId && !m.body.isNullOrBlank()) {
                    app.prefs.seenMessageId = m.id
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle(m.title?.takeIf { it.isNotBlank() } ?: getString(R.string.message))
                        .setMessage(m.body)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private var panelDevice: com.mytv.player.data.PanelDevice? = null

    companion object {
        /** قوائم تم فحص تاريخ انتهائها في هذه الجلسة */
        private val expiryChecked = mutableSetOf<String>()
    }

    private fun openSection(type: ContentType) {
        if (app.prefs.profile == null) {
            Toast.makeText(this, R.string.no_playlist, Toast.LENGTH_LONG).show()
            LoginActivity.add(this)
            return
        }
        BrowseActivity.start(this, type)
    }

    private fun refreshInfo() {
        val p = app.prefs.profile
        val tvAccount = findViewById<TextView>(R.id.tvAccount)
        val tvExpiry = findViewById<TextView>(R.id.tvExpiry)
        findViewById<TextView>(R.id.tvMac).text = deviceLine().trim()
        if (p == null) {
            tvAccount.text = getString(R.string.no_playlist)
            tvExpiry.text = getString(R.string.expiry, "—")
            return
        }
        val user = if (p.kind == Profile.XTREAM) "  •  ${p.username}" else ""
        tvAccount.text = getString(R.string.active_playlist, p.name.ifBlank { Prefs.defaultName(p) }) + user
        tvExpiry.text = if (p.kind == Profile.XTREAM) {
            val exp = p.expiry
            val text = if (exp != null) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(exp * 1000))
                       else getString(R.string.unlimited)
            getString(R.string.expiry, text)
        } else getString(R.string.expiry, "—")
    }

    /** "MAC: ...  •  مفعّل حتى ..." عند ربط اللوحة */
    private fun deviceLine(): String {
        if (!PanelConfig.enabled) return ""
        val mac = Panel.deviceIds(this).first
        val exp = panelDevice?.expires_at
        val until = if (exp != null) "  •  " + getString(R.string.device_until,
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(exp * 1000))) else ""
        return "MAC: $mac$until"
    }

    /** معلومات الحساب والجهاز */
    private fun showAccount() {
        val p = app.prefs.profile
        val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val lines = mutableListOf<String>()
        if (PanelConfig.enabled) {
            lines += "MAC: " + Panel.deviceIds(this).first
            panelDevice?.expires_at?.let { lines += getString(R.string.device_until, df.format(Date(it * 1000))) }
            lines += ""
        }
        if (p == null) lines += getString(R.string.no_playlist)
        else {
            lines += getString(R.string.active_playlist, p.name.ifBlank { Prefs.defaultName(p) })
            if (p.kind == Profile.XTREAM) {
                lines += getString(R.string.username) + ": " + p.username
                lines += getString(R.string.expiry, p.expiry?.let { df.format(Date(it * 1000)) } ?: getString(R.string.unlimited))
            }
        }
        lines += ""
        lines += "v" + runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        AlertDialog.Builder(this)
            .setTitle(R.string.account_info)
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun loadWeather() {
        val tvPlace = findViewById<TextView>(R.id.tvWeather)
        val tvTemp = findViewById<TextView>(R.id.tvTemp)
        val icon = findViewById<android.widget.ImageView>(R.id.ivWeather)
        lifecycleScope.launch {
            val w = Weather.get() ?: return@launch
            tvPlace.text = w.place
            tvTemp.text = w.tempC?.let { "$it°C" } ?: ""
            // رموز WMO: 0-1 صحو، 2-48 غيوم/ضباب، 51+ مطر/ثلج/عواصف
            val c = w.code ?: 0
            icon.setImageResource(when {
                c <= 1 -> R.drawable.ic_sun
                c <= 48 -> R.drawable.ic_cloud
                else -> R.drawable.ic_rain
            })
        }
    }

    // ---------------- قوائم التشغيل ----------------

    private fun showPlaylists() {
        val list = app.prefs.profiles()
        val activeId = app.prefs.profile?.id
        val names = list.map { (if (it.id == activeId) "✓  " else "     ") + it.name.ifBlank { Prefs.defaultName(it) } } +
            ("＋  " + getString(R.string.add_playlist))
        AlertDialog.Builder(this)
            .setTitle(R.string.playlists)
            .setItems(names.toTypedArray()) { _, which ->
                if (which >= list.size) LoginActivity.add(this) else playlistOptions(list[which])
            }
            .show()
    }

    private fun playlistOptions(p: Profile) {
        val name = p.name.ifBlank { Prefs.defaultName(p) }
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf(getString(R.string.use), getString(R.string.edit), getString(R.string.delete))) { _, which ->
                when (which) {
                    0 -> {
                        app.prefs.setActive(p.id)
                        app.repo.clear()
                        refreshInfo()
                    }
                    1 -> LoginActivity.edit(this, p.id)
                    2 -> AlertDialog.Builder(this)
                        .setMessage(getString(R.string.confirm_delete_playlist, name))
                        .setPositiveButton(R.string.delete) { _, _ ->
                            app.prefs.deleteProfile(p.id)
                            app.repo.clear()
                            refreshInfo()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    // ---------------- الإعدادات ----------------

    private fun showSettings() {
        val prefs = app.prefs
        val playerName = getString(
            if (prefs.playerMode == Prefs.PLAYER_EXTERNAL) R.string.player_external else R.string.player_internal)
        val formatName = if (prefs.liveFormat == "m3u8") "HLS (m3u8)" else "TS"
        val sdName = getString(if (prefs.sdOnly) R.string.on else R.string.off)
        val items = arrayOf(
            getString(R.string.player_setting, playerName),
            getString(R.string.live_format, formatName),
            getString(R.string.sd_only, sdName)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> choosePlayer()
                    1 -> {
                        prefs.liveFormat = if (prefs.liveFormat == "m3u8") "ts" else "m3u8"
                        app.repo.clear()
                        showSettings()
                    }
                    2 -> {
                        prefs.sdOnly = !prefs.sdOnly
                        showSettings()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun choosePlayer() {
        val prefs = app.prefs
        val options = arrayOf(getString(R.string.player_internal), getString(R.string.player_external))
        val checked = if (prefs.playerMode == Prefs.PLAYER_EXTERNAL) 1 else 0
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_player)
            .setSingleChoiceItems(options, checked) { d, which ->
                prefs.playerMode = if (which == 1) Prefs.PLAYER_EXTERNAL else Prefs.PLAYER_INTERNAL
                d.dismiss()
                showSettings()
            }
            .show()
    }
}
