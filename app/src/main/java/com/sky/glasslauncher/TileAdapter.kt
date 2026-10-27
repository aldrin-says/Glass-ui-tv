package com.sky.glasslauncher

import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

enum class Kind { APP, IPTV, GAME, ADD, MORE_GAMES }

class Tile(
    val kind: Kind,
    val pkg: String?,
    val label: String,
    val art: Drawable?,
    val isBanner: Boolean
)

object Ui {
    /** Gentle zoom when a view gets D-pad focus (the white ring comes from the drawable). */
    fun focusScale(v: View, scale: Float = 1.08f) {
        v.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            val s = if (hasFocus) scale else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(140).start()
        }
    }
}

class TileAdapter(
    private val onClick: (Tile) -> Unit,
    private val onLongClick: (Tile) -> Unit
) : RecyclerView.Adapter<TileAdapter.VH>() {

    private var tiles: List<Tile> = emptyList()

    fun submit(list: List<Tile>) {
        tiles = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val art: ImageView = v.findViewById(R.id.art)
        val label: TextView = v.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false)
        val vh = VH(v)

        // Smooth rounded corners for the artwork, sitting just inside the glass ring.
        val radius = parent.resources.displayMetrics.density * 15f
        vh.art.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        vh.art.clipToOutline = true

        Ui.focusScale(v)
        return vh
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = tiles[position]
        val d = h.itemView.resources.displayMetrics.density

        h.art.setImageDrawable(t.art)
        if (t.isBanner) {
            h.art.scaleType = ImageView.ScaleType.CENTER_CROP
            h.art.setPadding(0, 0, 0, 0)
            h.label.visibility = View.GONE
        } else {
            h.art.scaleType = ImageView.ScaleType.FIT_CENTER
            val side = (58 * d).toInt()
            h.art.setPadding(side, (12 * d).toInt(), side, (30 * d).toInt())
            h.label.visibility = View.VISIBLE
            h.label.text = t.label
        }

        val s = if (h.itemView.isFocused) 1.08f else 1f
        h.itemView.scaleX = s
        h.itemView.scaleY = s
        h.itemView.contentDescription = t.label
        h.itemView.setOnClickListener { onClick(t) }
        h.itemView.setOnLongClickListener {
            onLongClick(t)
            true
        }
    }

    override fun getItemCount() = tiles.size
}
