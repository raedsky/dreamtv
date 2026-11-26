package com.mytv.player.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mytv.player.App
import com.mytv.player.R
import com.mytv.player.data.ContentType
import com.mytv.player.data.Profile
import java.text.DateFormat
import java.util.Date

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val tileLive = findViewById<View>(R.id.tileLive)
        tileLive.setOnClickListener { BrowseActivity.start(this, ContentType.LIVE) }
        findViewById<View>(R.id.tileMovies).setOnClickListener { BrowseActivity.start(this, ContentType.MOVIE) }
        findViewById<View>(R.id.tileSeries).setOnClickListener { BrowseActivity.start(this, ContentType.SERIES) }

        findViewById<View>(R.id.btnAccount).setOnClickListener { LoginActivity.edit(this) }
        findViewById<View>(R.id.btnReload).setOnClickListener {
            App.instance.repo.clear()
            Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<View>(R.id.btnExit).setOnClickListener { finishAffinity() }

        // تكبير بسيط للعنصر المحدد بالريموت
        for (id in intArrayOf(R.id.tileLive, R.id.tileMovies, R.id.tileSeries,
                R.id.btnAccount, R.id.btnReload, R.id.btnSettings, R.id.btnExit)) {
            findViewById<View>(id).setOnFocusChangeListener { v, has ->
                val s = if (has) 1.05f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            }
        }
        tileLive.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        val p = App.instance.prefs.profile
        val label = when {
            p == null -> ""
            p.kind == Profile.XTREAM -> p.username
            else -> "M3U"
        }
        findViewById<TextView>(R.id.tvAccount).text = getString(R.string.account, label)

        val exp = p?.expiry
        val expText = if (exp != null) {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(exp * 1000))
        } else {
            getString(R.string.unlimited)
        }
        findViewById<TextView>(R.id.tvExpiry).text =
            if (p?.kind == Profile.XTREAM) getString(R.string.expiry, expText) else ""
    }

    private fun showSettings() {
        val items = arrayOf(getString(R.string.edit_account), getString(R.string.logout))
        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> LoginActivity.edit(this)
                    1 -> {
                        App.instance.prefs.profile = null
                        App.instance.repo.clear()
                        startActivity(Intent(this, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                }
            }
            .show()
    }
}
