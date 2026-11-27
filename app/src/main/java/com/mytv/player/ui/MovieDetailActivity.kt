package com.mytv.player.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.mytv.player.App
import com.mytv.player.R
import com.mytv.player.data.ContentType
import com.mytv.player.data.MediaInfo
import com.mytv.player.data.StreamItem
import kotlinx.coroutines.launch

/** صفحة الفيلم مثل IBO: خلفية الفيلم، البوستر، التقييم، القصة، تشغيل / مفضلة / إعلان. */
class MovieDetailActivity : AppCompatActivity() {

    companion object {
        private var pending: StreamItem? = null
        fun open(ctx: Context, item: StreamItem) {
            pending = item
            ctx.startActivity(Intent(ctx, MovieDetailActivity::class.java))
        }
    }

    private val app get() = App.instance
    private lateinit var item: StreamItem
    private lateinit var btnFav: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = pending ?: run { finish(); return }
        setContentView(R.layout.activity_movie_detail)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvTitle).text = item.name
        Images.load(findViewById(R.id.ivPoster), item.icon)
        Images.load(findViewById(R.id.ivBackdrop), item.icon)
        showRating(item.rating)
        findViewById<TextView>(R.id.tvMeta).text = addedLine().orEmpty()

        val btnPlay = findViewById<TextView>(R.id.btnPlay)
        btnPlay.setOnClickListener {
            val url = item.url ?: return@setOnClickListener
            app.groups.addRecent(ContentType.MOVIE, item.key)
            Playback.play(this, listOf(PlayQueue.Entry(item.name, url, key = item.key)), 0, live = false)
        }
        btnFav = findViewById(R.id.btnFav)
        btnFav.setOnClickListener {
            app.groups.toggleFavorite(ContentType.MOVIE, item.key)
            updateFav()
        }
        updateFav()
        for (id in intArrayOf(R.id.btnPlay, R.id.btnFav, R.id.btnTrailer, R.id.btnBack)) {
            findViewById<View>(id).setOnFocusChangeListener { v, has ->
                val s = if (has) 1.06f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            }
        }
        btnPlay.requestFocus()

        val progress = findViewById<ProgressBar>(R.id.progress)
        lifecycleScope.launch {
            val info = app.repo.vodInfo(item)
            progress.isVisible = false
            if (info != null) bind(info)
        }
    }

    private fun bind(info: MediaInfo) {
        info.backdrop?.let { Images.load(findViewById(R.id.ivBackdrop), it) }
        if (item.icon.isNullOrBlank()) info.cover?.let { Images.load(findViewById<ImageView>(R.id.ivPoster), it) }
        showRating(info.rating ?: item.rating)
        val year = info.release?.take(4)?.takeIf { it.all(Char::isDigit) }
        findViewById<TextView>(R.id.tvMeta).text =
            (listOfNotNull(year, info.duration, info.genre).filter { it.isNotBlank() } + listOfNotNull(addedLine()))
                .joinToString("   •   ")
        findViewById<TextView>(R.id.tvPlot).text = info.plot.orEmpty()
        findViewById<TextView>(R.id.tvDirector).apply {
            isVisible = !info.director.isNullOrBlank()
            text = getString(R.string.director, info.director)
        }
        findViewById<TextView>(R.id.tvCast).apply {
            isVisible = !info.cast.isNullOrBlank()
            text = getString(R.string.cast, info.cast)
        }
        val trailer = info.trailer?.trim()?.takeIf { it.isNotEmpty() }
        findViewById<TextView>(R.id.btnTrailer).apply {
            isVisible = trailer != null
            text = "🎬  " + getString(R.string.trailer)
            setOnClickListener {
                val url = if (trailer!!.startsWith("http")) trailer else "https://www.youtube.com/watch?v=$trailer"
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // زر التشغيل يبين "متابعة من 00:45:10" إذا شاهدت جزءاً من الفيلم
        val pos = app.watch.resumePosition(item.key)
        findViewById<TextView>(R.id.btnPlay).text = "▶  " + if (pos != null)
            getString(R.string.resume_from, com.mytv.player.data.WatchStore.format(pos)) else getString(R.string.play)
        val pb = findViewById<ProgressBar>(R.id.watchProgress)
        val prog = app.watch.get(item.key)
        pb.isVisible = pos != null && prog != null && prog.duration > 0
        if (prog != null) pb.progress = prog.percent
    }

    private fun addedLine(): String? {
        if (item.added <= 0) return null
        val d = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(item.added * 1000))
        return getString(R.string.added_on, d)
    }

    private fun showRating(r: String?) {
        findViewById<TextView>(R.id.tvRating).apply {
            isVisible = !r.isNullOrBlank()
            text = "★ $r"
        }
    }

    private fun updateFav() {
        val fav = item.key in app.groups.favorites(ContentType.MOVIE)
        btnFav.text = (if (fav) "♥  " else "♡  ") + getString(if (fav) R.string.remove_favorite else R.string.add_favorite)
    }
}
