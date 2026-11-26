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
import com.bumptech.glide.Glide
import com.mytv.player.R
import com.mytv.player.data.ContentType
import com.mytv.player.data.Episode
import com.mytv.player.data.StreamItem

/** صف في قائمة التصنيفات الجانبية. */
data class CategoryRow(val kind: Kind, val id: String, val name: String, val count: Int) {
    enum class Kind { ALL, FAVORITES, GROUP, ADD_GROUP, SERVER }
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
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = rows[position]
        val ctx = h.itemView.context
        h.name.text = when (r.kind) {
            CategoryRow.Kind.FAVORITES -> "★  ${r.name}"
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (type == ContentType.LIVE) R.layout.item_channel else R.layout.item_poster
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.name.text = item.name
        h.fav.isVisible = item.key in favorites()
        Glide.with(h.image)
            .load(item.icon?.takeIf { it.isNotBlank() })
            .placeholder(R.drawable.ic_placeholder)
            .error(R.drawable.ic_placeholder)
            .fallback(R.drawable.ic_placeholder)
            .into(h.image)
        h.itemView.setOnClickListener {
            val pos = h.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(items[pos], pos)
        }
        h.itemView.setOnLongClickListener { onLongClick(item); true }
        h.itemView.onMenuKey { onLongClick(item) }
    }
}

class EpisodeAdapter(
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    var episodes: List<Episode> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    override fun getItemCount() = episodes.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val e = episodes[position]
        h.title.text = episodeLabel(e)
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
