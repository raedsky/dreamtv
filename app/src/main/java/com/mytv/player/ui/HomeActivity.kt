package com.mytv.player.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mytv.player.App
import com.mytv.player.R
import com.mytv.player.data.ContentType
import com.mytv.player.data.Profile

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<TextView>(R.id.cardLive).setOnClickListener { BrowseActivity.start(this, ContentType.LIVE) }
        findViewById<TextView>(R.id.cardMovies).setOnClickListener { BrowseActivity.start(this, ContentType.MOVIE) }
        findViewById<TextView>(R.id.cardSeries).setOnClickListener { BrowseActivity.start(this, ContentType.SERIES) }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener { showSettings() }

        findViewById<TextView>(R.id.cardLive).requestFocus()
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
    }

    private fun showSettings() {
        val items = arrayOf(
            getString(R.string.reload),
            getString(R.string.edit_account),
            getString(R.string.logout)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        App.instance.repo.clear()
                        Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show()
                    }
                    1 -> LoginActivity.edit(this)
                    2 -> {
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
