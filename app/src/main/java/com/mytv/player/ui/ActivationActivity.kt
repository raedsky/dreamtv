package com.mytv.player.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.mytv.player.R
import com.mytv.player.data.Panel
import kotlinx.coroutines.launch

/** تظهر عندما يكون الجهاز غير مفعّل / موقوف / منتهي في لوحة التحكم. */
class ActivationActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_STATUS = "status"
        fun start(ctx: Context, status: String?) {
            ctx.startActivity(
                Intent(ctx, ActivationActivity::class.java)
                    .putExtra(EXTRA_STATUS, status)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activation)

        showStatus(intent.getStringExtra(EXTRA_STATUS))
        findViewById<TextView>(R.id.tvMac).text = Panel.deviceIds(this).first

        findViewById<Button>(R.id.btnRetry).setOnClickListener { retry() }
        findViewById<Button>(R.id.btnExit).setOnClickListener { finishAffinity() }
        findViewById<Button>(R.id.btnRetry).requestFocus()

        // زر الرجوع يغلق التطبيق (لا يمكن تجاوز شاشة التفعيل)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishAffinity()
        })
    }

    private fun showStatus(status: String?) {
        findViewById<TextView>(R.id.tvStatus).setText(when (status) {
            "blocked" -> R.string.status_blocked
            "expired" -> R.string.status_expired
            else -> R.string.status_pending
        })
    }

    private fun retry() {
        val progress = findViewById<ProgressBar>(R.id.progress)
        progress.isVisible = true
        lifecycleScope.launch {
            val st = Panel.sync(this@ActivationActivity)
            progress.isVisible = false
            when {
                st?.device == null -> Toast.makeText(this@ActivationActivity, R.string.panel_unreachable, Toast.LENGTH_LONG).show()
                st.device.allowed -> {
                    startActivity(Intent(this@ActivationActivity, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                }
                else -> {
                    showStatus(st.device.status)
                    Toast.makeText(this@ActivationActivity, R.string.still_not_active, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
