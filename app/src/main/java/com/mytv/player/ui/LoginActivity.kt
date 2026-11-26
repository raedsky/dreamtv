package com.mytv.player.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.mytv.player.App
import com.mytv.player.R
import com.mytv.player.data.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_EDIT = "edit"
        fun edit(ctx: Context) =
            ctx.startActivity(Intent(ctx, LoginActivity::class.java).putExtra(EXTRA_EDIT, true))
    }

    private lateinit var rbXtream: RadioButton
    private lateinit var rbM3u: RadioButton
    private lateinit var xtreamFields: View
    private lateinit var m3uFields: View
    private lateinit var etServer: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etM3u: EditText
    private lateinit var btnConnect: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = App.instance
        val editing = intent.getBooleanExtra(EXTRA_EDIT, false)
        if (!editing && app.prefs.profile != null) {
            openHome()
            return
        }
        setContentView(R.layout.activity_login)

        rbXtream = findViewById(R.id.rbXtream)
        rbM3u = findViewById(R.id.rbM3u)
        xtreamFields = findViewById(R.id.xtreamFields)
        m3uFields = findViewById(R.id.m3uFields)
        etServer = findViewById(R.id.etServer)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        etM3u = findViewById(R.id.etM3u)
        btnConnect = findViewById(R.id.btnConnect)
        progress = findViewById(R.id.progress)
        tvError = findViewById(R.id.tvError)

        rbXtream.setOnCheckedChangeListener { _, _ -> updateMode() }
        rbM3u.setOnCheckedChangeListener { _, _ -> updateMode() }

        app.prefs.profile?.let { p ->
            if (p.kind == Profile.M3U) rbM3u.isChecked = true else rbXtream.isChecked = true
            etServer.setText(p.server)
            etUser.setText(p.username)
            etPass.setText(p.password)
            etM3u.setText(p.m3uUrl)
        }
        updateMode()

        btnConnect.setOnClickListener { connect() }
    }

    private fun updateMode() {
        xtreamFields.isVisible = rbXtream.isChecked
        m3uFields.isVisible = rbM3u.isChecked
    }

    private fun connect() {
        val profile = if (rbXtream.isChecked) {
            val server = etServer.text.toString().trim()
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()
            if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                tvError.setText(R.string.fill_fields); tvError.isVisible = true; return
            }
            Profile(Profile.XTREAM, server = server, username = user, password = pass)
        } else {
            val url = etM3u.text.toString().trim()
            if (url.isEmpty()) {
                tvError.setText(R.string.fill_fields); tvError.isVisible = true; return
            }
            Profile(Profile.M3U, m3uUrl = url)
        }

        setBusy(true)
        lifecycleScope.launch {
            try {
                App.instance.repo.login(profile)
                App.instance.prefs.profile = profile
                openHome()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tvError.text = getString(R.string.login_failed, e.message ?: e.javaClass.simpleName)
                tvError.isVisible = true
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        progress.isVisible = busy
        btnConnect.isEnabled = !busy
        if (busy) tvError.isVisible = false
    }

    private fun openHome() {
        startActivity(Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
