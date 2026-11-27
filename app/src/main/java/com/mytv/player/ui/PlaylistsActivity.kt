package com.mytv.player.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mytv.player.App
import com.mytv.player.PanelConfig
import com.mytv.player.R
import com.mytv.player.data.Panel
import com.mytv.player.data.Prefs
import com.mytv.player.data.Profile
import java.text.DateFormat
import java.util.Date

/** شاشة القوائم مثل Dream TV: بطاقات القوائم + "إضافة قائمة تشغيل"، وعلى اليمين الشعار و MAC. */
class PlaylistsActivity : AppCompatActivity() {

    companion object {
        fun open(ctx: Context) = ctx.startActivity(Intent(ctx, PlaylistsActivity::class.java))
    }

    private val app get() = App.instance
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlists)
        ThemeUi.apply(findViewById(android.R.id.content))
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvPlaylists)
        rv.layoutManager = GridLayoutManager(this, if (resources.configuration.screenWidthDp >= 900) 3 else 2)
        adapter = Adapter()
        rv.adapter = adapter

        if (PanelConfig.enabled) {
            findViewById<TextView>(R.id.tvMac).text = Panel.deviceIds(this).first.lowercase()
            val d = Panel.cached()?.device
            val status = when {
                d == null -> "—"
                d.expires_at != null -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(d.expires_at * 1000))
                d.allowed -> getString(R.string.unlimited)
                else -> getString(R.string.status_expired)
            }
            findViewById<TextView>(R.id.tvStatus).text = status
        } else {
            findViewById<View>(R.id.tvExpiryLabel).isVisible = false
        }
        findViewById<TextView>(R.id.tvVersion).text =
            "v" + runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
    }

    override fun onResume() {
        super.onResume()
        adapter.reload()
        findViewById<RecyclerView>(R.id.rvPlaylists).post {
            findViewById<RecyclerView>(R.id.rvPlaylists).getChildAt(0)?.requestFocus()
        }
    }

    private fun use(p: Profile) {
        app.prefs.setActive(p.id)
        app.repo.clear()
        finish()
    }

    private fun options(p: Profile) {
        val name = p.name.ifBlank { Prefs.defaultName(p) }
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf(getString(R.string.use), getString(R.string.edit), getString(R.string.delete))) { _, which ->
                when (which) {
                    0 -> use(p)
                    1 -> LoginActivity.edit(this, p.id)
                    2 -> AlertDialog.Builder(this)
                        .setMessage(getString(R.string.confirm_delete_playlist, name))
                        .setPositiveButton(R.string.delete) { _, _ ->
                            app.prefs.deleteProfile(p.id)
                            app.repo.clear()
                            adapter.reload()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        var list: List<Profile> = emptyList()

        fun reload() {
            list = app.prefs.profiles()
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: TextView = v.findViewById(R.id.tvIcon)
            val name: TextView = v.findViewById(R.id.tvName)
            val sub: TextView = v.findViewById(R.id.tvSub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_card, parent, false)
            ThemeUi.apply(v)
            v.setOnFocusChangeListener { view, has ->
                val s = if (has) 1.05f else 1f
                view.animate().scaleX(s).scaleY(s).setDuration(110).start()
            }
            return VH(v)
        }

        override fun getItemCount() = list.size + 1

        override fun onBindViewHolder(h: VH, position: Int) {
            if (position == 0) {
                h.icon.text = "+"
                h.name.setText(R.string.add_playlist)
                h.sub.isVisible = false
                h.itemView.setOnClickListener { LoginActivity.add(this@PlaylistsActivity) }
                h.itemView.setOnLongClickListener(null)
                h.itemView.setOnKeyListener(null)
                return
            }
            val p = list[position - 1]
            val active = p.id == app.prefs.profile?.id
            h.icon.text = if (active) "✓" else "▶"
            h.name.text = p.name.ifBlank { Prefs.defaultName(p) }
            h.sub.isVisible = active
            h.sub.setText(R.string.active_now)
            h.itemView.setOnClickListener { use(p) }
            h.itemView.setOnLongClickListener { options(p); true }
            h.itemView.setOnKeyListener { _, code, ev ->
                if (code == KeyEvent.KEYCODE_MENU && ev.action == KeyEvent.ACTION_UP) { options(p); true } else false
            }
        }
    }
}
