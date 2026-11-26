package com.mytv.player.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.mytv.player.App
import com.mytv.player.R
import com.mytv.player.data.StreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SeriesDetailActivity : AppCompatActivity() {

    companion object {
        private var pending: StreamItem? = null
        fun open(ctx: Context, item: StreamItem) {
            pending = item
            ctx.startActivity(Intent(ctx, SeriesDetailActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val item = pending ?: run { finish(); return }
        setContentView(R.layout.activity_series_detail)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvTitle).text = item.name
        Glide.with(this).load(item.icon).placeholder(R.drawable.ic_placeholder)
            .error(R.drawable.ic_placeholder).into(findViewById<ImageView>(R.id.ivCover))

        val progress = findViewById<ProgressBar>(R.id.progress)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val rv = findViewById<RecyclerView>(R.id.rvEpisodes)
        val adapter = EpisodeAdapter { index ->
            val eps = (rv.adapter as EpisodeAdapter).episodes
            PlayQueue.set(
                eps.map { PlayQueue.Entry("${item.name} - ${EpisodeAdapter.episodeLabel(it)}", it.url) },
                index, live = false
            )
            PlayerActivity.start(this)
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        lifecycleScope.launch {
            try {
                val eps = App.instance.repo.episodes(item)
                adapter.episodes = eps
                tvEmpty.isVisible = eps.isEmpty()
                tvEmpty.setText(R.string.no_episodes)
                rv.post { rv.getChildAt(0)?.requestFocus() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tvEmpty.text = getString(R.string.load_error, e.message ?: e.javaClass.simpleName)
                tvEmpty.isVisible = true
            } finally {
                progress.isVisible = false
            }
        }
    }
}
