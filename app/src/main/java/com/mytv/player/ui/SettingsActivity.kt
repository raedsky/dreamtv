package com.mytv.player.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mytv.player.App
import com.mytv.player.PanelConfig
import com.mytv.player.R
import com.mytv.player.data.ContentType
import com.mytv.player.data.Panel
import com.mytv.player.data.Prefs
import com.mytv.player.data.Themes
import kotlinx.coroutines.launch

/** شاشة الإعدادات: شبكة بطاقات مثل Dream TV، وتحتها MAC ومفتاح الجهاز. */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val RESULT_PLAYLISTS = Activity.RESULT_FIRST_USER + 1
    }

    private val app get() = App.instance
    private lateinit var adapter: Adapter

    private class Option(
        @DrawableRes val icon: Int,
        val label: String,
        val value: () -> String? = { null },
        val action: () -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val root = findViewById<View>(android.R.id.content)
        ThemeUi.apply(root)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvSettings)
        val cols = if (resources.configuration.screenWidthDp >= 900) 4 else 3
        rv.layoutManager = GridLayoutManager(this, cols)
        adapter = Adapter(options())
        rv.adapter = adapter
        rv.post { rv.getChildAt(0)?.requestFocus() }

        if (PanelConfig.enabled) {
            val mac = Panel.deviceIds(this).first
            findViewById<TextView>(R.id.tvDevice).text =
                getString(R.string.mac_address, mac)
        }
    }

    private fun refresh() = adapter.notifyDataSetChanged()

    private fun options(): List<Option> {
        val p = app.prefs
        return listOf(
            Option(R.drawable.ic_list, getString(R.string.change_playlist)) {
                setResult(RESULT_PLAYLISTS); finish()
            },
            Option(R.drawable.ic_playlist_add, getString(R.string.add_playlist)) { LoginActivity.add(this) },
            Option(R.drawable.ic_translate, getString(R.string.language), { currentLanguageName() }) { chooseLanguage() },
            Option(R.drawable.ic_palette, getString(R.string.theme), { Themes.current.name }) { chooseTheme() },
            Option(R.drawable.ic_eye_off, getString(R.string.hide_live)) { manageHidden(ContentType.LIVE, getString(R.string.live)) },
            Option(R.drawable.ic_eye_off, getString(R.string.hide_movies)) { manageHidden(ContentType.MOVIE, getString(R.string.movies)) },
            Option(R.drawable.ic_eye_off, getString(R.string.hide_series)) { manageHidden(ContentType.SERIES, getString(R.string.series)) },
            Option(R.drawable.ic_clear_list, getString(R.string.clear_live_history)) { clearHistory(ContentType.LIVE) },
            Option(R.drawable.ic_clear_list, getString(R.string.clear_movies_history)) { clearHistory(ContentType.MOVIE) },
            Option(R.drawable.ic_clear_list, getString(R.string.clear_series_history)) { clearHistory(ContentType.SERIES) },
            Option(R.drawable.ic_sort_az, getString(R.string.sort_live), { sortLabel(p.sortMode(ContentType.LIVE)) }) { chooseLiveSort() },
            Option(R.drawable.ic_tv_format, getString(R.string.live_format_title),
                { if (p.liveFormat == "m3u8") "HLS (m3u8)" else "TS" }) {
                p.liveFormat = if (p.liveFormat == "m3u8") "ts" else "m3u8"
                app.repo.clear()
                refresh()
            },
            Option(R.drawable.ic_play_circle, getString(R.string.players),
                { getString(if (p.playerMode == Prefs.PLAYER_EXTERNAL) R.string.player_external else R.string.player_internal) }) {
                choosePlayer()
            },
            Option(R.drawable.ic_hd, getString(R.string.sd_only_title), { getString(if (p.sdOnly) R.string.on else R.string.off) }) {
                p.sdOnly = !p.sdOnly
                refresh()
            },
            Option(R.drawable.ic_download, getString(R.string.update_now)) {
                app.repo.clear()
                Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ---------------- الخيارات ----------------

    private fun clearHistory(type: ContentType) {
        app.groups.clearRecents(type)
        Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun sortLabel(mode: String) = getString(when (mode) {
        "added" -> R.string.sort_added
        "name" -> R.string.sort_name
        else -> R.string.sort_default
    })

    private fun chooseLiveSort() {
        val modes = arrayOf("default", "added", "name")
        AlertDialog.Builder(this)
            .setTitle(R.string.sort_live)
            .setSingleChoiceItems(modes.map { sortLabel(it) }.toTypedArray(),
                modes.indexOf(app.prefs.sortMode(ContentType.LIVE)).coerceAtLeast(0)) { d, which ->
                app.prefs.setSortMode(ContentType.LIVE, modes[which])
                d.dismiss()
                refresh()
            }
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
                refresh()
            }
            .show()
    }

    private fun chooseTheme() {
        val names = Themes.all.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(names, Themes.all.indexOf(Themes.current)) { d, which ->
                d.dismiss()
                if (Themes.set(this, Themes.all[which].id)) recreate()
            }
            .show()
    }

    // ---------------- اللغة ----------------

    private val languages = listOf(
        "" to null, "en" to "English", "ar" to "العربية", "fr" to "Français",
        "tr" to "Türkçe", "es" to "Español"
    )

    private fun currentTag() = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        .substringBefore(',').substringBefore('-')

    private fun currentLanguageName(): String {
        val tag = currentTag()
        return languages.firstOrNull { it.first == tag && tag.isNotEmpty() }?.second ?: getString(R.string.language_system)
    }

    private fun chooseLanguage() {
        val names = languages.map { it.second ?: getString(R.string.language_system) }.toTypedArray()
        val checked = languages.indexOfFirst { it.first == currentTag() }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(names, checked) { d, which ->
                d.dismiss()
                val code = languages[which].first
                AppCompatDelegate.setApplicationLocales(
                    if (code.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(code))
            }
            .show()
    }

    // ---------------- الإخفاء ----------------

    /** كل الباقات + العناصر المخفية: المؤشَّر = مخفي */
    private fun manageHidden(type: ContentType, title: String) {
        if (app.prefs.profile == null) {
            Toast.makeText(this, R.string.no_playlist, Toast.LENGTH_LONG).show()
            return
        }
        val wait = Toast.makeText(this, R.string.refreshing, Toast.LENGTH_SHORT)
        wait.show()
        lifecycleScope.launch {
            val content = runCatching { app.repo.load(type, false) }.getOrNull()
            wait.cancel()
            val g = app.groups
            val hc = g.hiddenCategories(type)
            val hi = g.hiddenItems(type)
            data class Entry(val isCat: Boolean, val id: String, val name: String)
            val entries = ArrayList<Entry>()
            content?.categories?.forEach { entries += Entry(true, it.id, it.name) }
            hc.forEach { (id, n) -> if (entries.none { it.isCat && it.id == id }) entries += Entry(true, id, n) }
            hi.forEach { (k, n) -> entries += Entry(false, k, n) }
            if (entries.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.empty_list, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = entries.map { (if (it.isCat) "📁  " else "▸  ") + it.name }.toTypedArray()
            val checked = BooleanArray(entries.size) { i ->
                val e = entries[i]; if (e.isCat) e.id in hc else e.id in hi
            }
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle(getString(R.string.hide_title, title))
                .setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
                .setPositiveButton(R.string.save) { _, _ ->
                    entries.forEachIndexed { i, e ->
                        if (e.isCat) g.setCategoryHidden(type, e.id, e.name, checked[i])
                        else g.setItemHidden(type, e.id, e.name, checked[i])
                    }
                    Toast.makeText(this@SettingsActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    // ---------------- الشبكة ----------------

    private class Adapter(val items: List<Option>) : RecyclerView.Adapter<Adapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.ivIcon)
            val label: TextView = v.findViewById(R.id.tvLabel)
            val value: TextView = v.findViewById(R.id.tvValue)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_setting, parent, false)
            ThemeUi.apply(v)
            v.setOnFocusChangeListener { view, has ->
                val s = if (has) 1.04f else 1f
                view.animate().scaleX(s).scaleY(s).setDuration(110).start()
            }
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val o = items[position]
            h.icon.setImageResource(o.icon)
            h.label.text = o.label
            val v = o.value()
            h.value.isVisible = !v.isNullOrBlank()
            h.value.text = v
            h.itemView.setOnClickListener { o.action() }
        }

        override fun getItemCount() = items.size
    }
}
