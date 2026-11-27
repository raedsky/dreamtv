package com.mytv.player.ui

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.mytv.player.R
import com.mytv.player.data.ContentType
import com.mytv.player.data.Episode
import com.mytv.player.data.StreamItem

/**
 * محمّل صور خاص (بدل Glide):
 * - كل صورة مرتبطة بخانتها؛ إذا تغيّرت الخانة (تمرير/تغيير قسم) لا تُوضع فيها صورة قديمة
 * - 3 تحميلات بالتوازي فقط
 * - الأحدث أولاً: الصور الظاهرة على الشاشة تتحمّل قبل التي تم تجاوزها بالتمرير
 * - ذاكرة مؤقتة في الرام + على القرص
 */
object Images {
    private const val MAX_SIZE = 320
    private val mem = object : android.util.LruCache<String, android.graphics.Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
    }
    private val exec = java.util.concurrent.ThreadPoolExecutor(
        3, 3, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        object : java.util.concurrent.LinkedBlockingDeque<Runnable>() {
            override fun offer(e: Runnable): Boolean = offerFirst(e)   // LIFO
        }
    )
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val client by lazy {
        com.mytv.player.App.http.newBuilder()
            .cache(okhttp3.Cache(java.io.File(com.mytv.player.App.instance.cacheDir, "images_v2"), 60L * 1024 * 1024))
            .build()
    }

    fun load(view: ImageView, rawUrl: String?) {
        val url = rawUrl?.trim()?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        view.setTag(R.id.image_url, url)
        if (url == null) { view.setImageResource(R.drawable.ic_placeholder); return }
        mem.get(url)?.let { view.setImageBitmap(it); return }
        view.setImageResource(R.drawable.ic_placeholder)
        exec.execute {
            // الخانة صارت لعنصر آخر قبل أن يبدأ التحميل؟ نتجاهل
            if (view.getTag(R.id.image_url) != url) return@execute
            val bmp = runCatching { fetch(url) }.getOrNull() ?: return@execute
            mem.put(url, bmp)
            main.post { if (view.getTag(R.id.image_url) == url) view.setImageBitmap(bmp) }
        }
    }

    private fun fetch(url: String): android.graphics.Bitmap? {
        val req = okhttp3.Request.Builder().url(url).header("Connection", "close").build()
        val bytes = client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            r.body?.bytes()
        } ?: return null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIZE && bounds.outHeight / (sample * 2) >= MAX_SIZE) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}

/** صف في قائمة التصنيفات الجانبية. */
data class CategoryRow(val kind: Kind, val id: String, val name: String, val count: Int) {
    enum class Kind { RECENT, LATEST, ALL, FAVORITES, GROUP, ADD_GROUP, SERVER }
    val key: String get() = "${kind.name}:$id"
}

/** زر القائمة في الريموت يعمل مثل الضغط المطوّل. */
private fun View.onMenuKey(action: () -> Unit) {
    setOnKeyListener { _, keyCode, event ->
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) action()
            true
        } else false
    }
}

class CategoryAdapter(
    private val onClick: (CategoryRow) -> Unit,
    private val onLongClick: (CategoryRow) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var rows: List<CategoryRow> = emptyList()
    private var selectedKey: String? = null

    init { setHasStableIds(true) }

    fun submit(list: List<CategoryRow>) {
        val sameSize = list.size == rows.size
        rows = list
        if (sameSize) notifyItemRangeChanged(0, rows.size) else notifyDataSetChanged()
    }

    fun setSelected(row: CategoryRow) {
        selectedKey = row.key
        notifyItemRangeChanged(0, rows.size)
    }

    fun indexOf(row: CategoryRow) = rows.indexOfFirst { it.key == row.key }

    override fun getItemId(position: Int) = rows[position].key.hashCode().toLong()
    override fun getItemCount() = rows.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val count: TextView = v.findViewById(R.id.tvCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false).also { ThemeUi.apply(it) })

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = rows[position]
        val ctx = h.itemView.context
        h.name.text = when (r.kind) {
            CategoryRow.Kind.RECENT -> "↺  ${r.name}"
            CategoryRow.Kind.LATEST -> "✦  ${r.name}"
            CategoryRow.Kind.FAVORITES -> "♥  ${r.name}"
            CategoryRow.Kind.GROUP -> "▣  ${r.name}"
            CategoryRow.Kind.ADD_GROUP -> "＋  ${r.name}"
            else -> r.name
        }
        h.name.setTextColor(ContextCompat.getColor(ctx,
            if (r.kind == CategoryRow.Kind.ADD_GROUP) R.color.accent else R.color.text_primary))
        h.count.text = if (r.count >= 0) r.count.toString() else ""
        h.itemView.isSelected = r.key == selectedKey
        h.itemView.setOnClickListener { onClick(r) }
        h.itemView.setOnLongClickListener { onLongClick(r); true }
        h.itemView.onMenuKey { onLongClick(r) }
    }
}

class ItemAdapter(
    private val type: ContentType,
    private val favorites: () -> Set<String>,
    private val onClick: (StreamItem, Int) -> Unit,
    private val onLongClick: (StreamItem) -> Unit
) : RecyclerView.Adapter<ItemAdapter.VH>() {

    var items: List<StreamItem> = emptyList()
        private set

    /** القناة المعروضة في المعاينة (تظهر بلون الثيم) */
    var selectedKey: String? = null
        set(v) {
            val old = field
            field = v
            items.indexOfFirst { it.key == old }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
            items.indexOfFirst { it.key == v }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        }

    fun submit(list: List<StreamItem>) {
        items = list
        notifyDataSetChanged()
    }

    /** يحدّث النجمة بدون إعادة بناء القائمة (يحافظ على التركيز في الريموت). */
    fun refreshMarks() = notifyItemRangeChanged(0, items.size)

    override fun getItemCount() = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(R.id.ivImage)
        val name: TextView = v.findViewById(R.id.tvName)
        val fav: TextView = v.findViewById(R.id.tvFav)
        val num: TextView? = v.findViewById(R.id.tvNum)
        val rating: TextView? = v.findViewById(R.id.tvRating)
        val watch: android.widget.ProgressBar? = v.findViewById(R.id.pbWatch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (type == ContentType.LIVE) R.layout.item_channel else R.layout.item_poster
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        ThemeUi.apply(v)
        if (type != ContentType.LIVE) {
            // تكبير البوستر المحدد مثل IBO
            v.setOnFocusChangeListener { view, has ->
                val s = if (has) 1.07f else 1f
                view.animate().scaleX(s).scaleY(s).setDuration(120).start()
                view.translationZ = if (has) 8f else 0f
            }
        }
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.name.text = item.name
        h.fav.isVisible = item.key in favorites()
        h.num?.text = (if (item.num > 0) item.num else position + 1).toString()
        h.itemView.isSelected = item.key == selectedKey
        h.rating?.let { r ->
            r.isVisible = !item.rating.isNullOrBlank()
            r.text = "★ ${item.rating}"
        }
        h.watch?.let { pb ->
            val w = com.mytv.player.App.instance.watch
            val prog = if (w.resumePosition(item.key) != null) w.get(item.key) else null
            pb.isVisible = prog != null && prog.duration > 0
            if (prog != null) pb.progress = prog.percent
        }
        Images.load(h.image, item.icon)
        h.itemView.setOnClickListener {
            val pos = h.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(items[pos], pos)
        }
        h.itemView.setOnLongClickListener { onLongClick(item); true }
        h.itemView.onMenuKey { onLongClick(item) }
    }
}

class EpisodeAdapter(
    private val fallbackImage: () -> String? = { null },
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    var episodes: List<Episode> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    override fun getItemCount() = episodes.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
        val sub: TextView = v.findViewById(R.id.tvSub)
        val thumb: ImageView = v.findViewById(R.id.ivThumb)
        val watch: android.widget.ProgressBar = v.findViewById(R.id.pbWatch)
        val done: TextView = v.findViewById(R.id.tvWatched)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false).also { ThemeUi.apply(it) })

    override fun onBindViewHolder(h: VH, position: Int) {
        val e = episodes[position]
        h.title.text = episodeLabel(e)
        val sub = listOfNotNull(e.duration?.takeIf { it.isNotBlank() }, e.plot?.takeIf { it.isNotBlank() }).joinToString("  •  ")
        h.sub.text = sub
        h.sub.isVisible = sub.isNotEmpty()
        Images.load(h.thumb, e.icon ?: fallbackImage())
        val w = com.mytv.player.App.instance.watch
        val key = com.mytv.player.data.WatchStore.episodeKey(e)
        val prog = if (w.resumePosition(key) != null) w.get(key) else null
        h.watch.isVisible = prog != null && prog.duration > 0
        if (prog != null) h.watch.progress = prog.percent
        h.done.isVisible = prog == null && w.isWatched(key)
        h.itemView.setOnClickListener {
            val pos = h.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(pos)
        }
    }

    companion object {
        fun episodeLabel(e: Episode): String {
            val code = String.format(java.util.Locale.US, "S%02d E%02d", e.season, e.number)
            // في M3U العنوان يحتوي الرقم أصلاً، فلا نكرّره
            return if (e.title.isBlank() || com.mytv.player.data.parseEpisode(e.title) != null) code
                   else "$code  •  ${e.title}"
        }
    }
}
