package com.mytv.player.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.mytv.player.data.Prefs
import com.mytv.player.data.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/** إضافة قائمة تشغيل جديدة أو تعديل قائمة موجودة. */
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ID = "id"
        fun add(ctx: Context) = ctx.startActivity(Intent(ctx, LoginActivity::class.java))
        fun edit(ctx: Context, id: String) =
            ctx.startActivity(Intent(ctx, LoginActivity::class.java).putExtra(EXTRA_ID, id))
    }

    private var editingId: String? = null
    private lateinit var spDns: android.widget.Spinner
    /** سيرفرات DNS من لوحة التحكم (إن وُجدت) */
    private var dnsList: List<com.mytv.player.data.PanelDns> = emptyList()

    private lateinit var etName: EditText
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
        setContentView(R.layout.activity_login)

        etName = findViewById(R.id.etName)
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

        spDns = findViewById(R.id.spDns)
        setupDns()
        rbXtream.setOnCheckedChangeListener { _, _ -> updateMode() }
        rbM3u.setOnCheckedChangeListener { _, _ -> updateMode() }

        editingId = intent.getStringExtra(EXTRA_ID)
        editingId?.let { id ->
            App.instance.prefs.profiles().firstOrNull { it.id == id }?.let { p ->
                etName.setText(p.name)
                rbXtream.isChecked = true
                etServer.setText(p.server)
                val i = dnsList.indexOfFirst { d -> d.url?.trimEnd('/') == p.server.trimEnd('/') }
                if (i >= 0) spDns.setSelection(i)
                etUser.setText(p.username)
                etPass.setText(p.password)
                etM3u.setText(p.m3uUrl)
            }
        }
        updateMode()

        // في أجهزة البوكس: الضغط على OK فوق الخانة يفتح الكيبورد
        for (et in listOf(etName, etServer, etUser, etPass, etM3u)) {
            et.setOnClickListener { showKeyboard(it as EditText) }
        }
        etPass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { connect(); true } else false
        }
        etM3u.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { connect(); true } else false
        }

        btnConnect.setOnClickListener { connect() }
    }

    private fun showKeyboard(et: EditText) {
        et.requestFocus()
        et.setSelection(et.text.length)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun updateMode() {
        xtreamFields.isVisible = true
        m3uFields.isVisible = false
    }

    /**
     * السيرفر يأتي من اللوحة (أو الافتراضي) — المستخدم يكتب اسم المستخدم وكلمة المرور فقط.
     * إذا كان في اللوحة أكثر من سيرفر تظهر قائمة اختيار.
     */
    private fun setupDns() {
        // نخفي خانات الرابط والسيرفر واختيار M3U
        findViewById<View>(R.id.rgType).isVisible = false
        rbXtream.isChecked = true
        etServer.isVisible = false
        dnsList = com.mytv.player.data.Panel.cached()?.dns.orEmpty().filter { !it.url.isNullOrBlank() }
            .ifEmpty {
                listOf(com.mytv.player.data.PanelDns(0, com.mytv.player.PanelConfig.DEFAULT_SERVER_NAME,
                    com.mytv.player.PanelConfig.DEFAULT_SERVER))
            }
        val names = dnsList.map { it.name?.takeIf { n -> n.isNotBlank() } ?: it.url.orEmpty() }
        spDns.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spDns.isVisible = dnsList.size > 1
    }

    private fun selectedServer(): String {
        val pos = spDns.selectedItemPosition.coerceIn(0, dnsList.size - 1)
        return dnsList[pos].url.orEmpty()
    }

    private fun connect() {
        val id = editingId ?: UUID.randomUUID().toString()
        var profile = if (true) {
            val server = selectedServer()
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()
            if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                tvError.setText(R.string.fill_fields); tvError.isVisible = true; return
            }
            Profile(Profile.XTREAM, id = id, server = server, username = user, password = pass)
        } else {
            val url = etM3u.text.toString().trim()
            if (url.isEmpty()) {
                tvError.setText(R.string.fill_fields); tvError.isVisible = true; return
            }
            Profile(Profile.M3U, id = id, m3uUrl = url)
        }
        val name = etName.text.toString().trim()
        profile = profile.copy(name = name.ifEmpty { Prefs.defaultName(profile) })

        setBusy(true)
        lifecycleScope.launch {
            try {
                val saved = App.instance.repo.login(profile)
                App.instance.prefs.saveProfile(saved)
                App.instance.prefs.setActive(saved.id)
                finish()   // نرجع للرئيسية
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
}
