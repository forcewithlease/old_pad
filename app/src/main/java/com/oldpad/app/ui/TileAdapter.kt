package com.oldpad.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oldpad.app.data.Tile
import com.oldpad.app.databinding.ItemTileBinding

class TileAdapter(
    private val onClick: (Tile) -> Unit
) : RecyclerView.Adapter<TileAdapter.Holder>() {

    private val items = ArrayList<Tile>()

    fun submit(list: List<Tile>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemTileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemTileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val tile = items[position]
        val binding = holder.binding

        binding.title.text = tile.title
        binding.subtitle.text = tile.subtitle
        binding.badge.visibility = if (tile.isRecent) View.VISIBLE else View.GONE

        if (tile.cover != null) {
            ThumbnailLoader.load(binding.cover, tile.cover, 720)
        } else {
            binding.cover.tag = null
            binding.cover.setImageDrawable(null)
        }

        if (tile.emoji != null) {
            binding.emoji.text = tile.emoji
            binding.emoji.visibility = View.VISIBLE
        } else {
            binding.emoji.visibility = View.GONE
        }

        binding.root.setOnClickListener { onClick(tile) }
    }
}
