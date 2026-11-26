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
import com.mytv.player.data.ContentType
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

        // تكبير بسيط للعنصر المحدد بالريموت
        for (id in intArrayOf(R.id.tileLive, R.id.tileMovies, R.id.tileSeries,
                R.id.btnPlaylists, R.id.btnReload, R.id.btnSettings, R.id.btnExit)) {
            findViewById<View>(id).setOnFocusChangeListener { v, has ->
                val s = if (has) 1.05f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            }
        }
        tileLive.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refreshInfo()
        loadWeather()
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
        if (p == null) {
            tvAccount.setText(R.string.no_playlist)
            tvExpiry.text = ""
            return
        }
        val user = if (p.kind == Profile.XTREAM) "  •  ${p.username}" else ""
        tvAccount.text = getString(R.string.active_playlist, p.name.ifBlank { Prefs.defaultName(p) }) + user
        tvExpiry.text = if (p.kind == Profile.XTREAM) {
            val exp = p.expiry
            val text = if (exp != null) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(exp * 1000))
                       else getString(R.string.unlimited)
            getString(R.string.expiry, text)
        } else ""
    }

    private fun loadWeather() {
        val tv = findViewById<TextView>(R.id.tvWeather)
        lifecycleScope.launch {
            val w = Weather.get() ?: return@launch
            tv.text = listOfNotNull(
                w.place.takeIf { it.isNotBlank() },
                w.tempC?.let { "$it°C" }
            ).joinToString("  •  ")
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
