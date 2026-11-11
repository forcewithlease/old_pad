package com.oldpad.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oldpad.app.data.Album
import com.oldpad.app.databinding.ItemAlbumSelectBinding

data class AlbumSelectItem(val album: Album, val selected: Boolean)

class AlbumSelectAdapter(
    private val onToggle: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<AlbumSelectAdapter.Holder>() {

    private val items = ArrayList<AlbumSelectItem>()

    fun submit(list: List<AlbumSelectItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemAlbumSelectBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAlbumSelectBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val binding = holder.binding
        val context = binding.root.context

        binding.cover.clipToOutline = true
        ThumbnailLoader.load(binding.cover, item.album.cover, 320)
        binding.name.text = item.album.name
        binding.count.text = context.getString(com.oldpad.app.R.string.photo_count, item.album.count)

        binding.switchAlbum.setOnCheckedChangeListener(null)
        binding.switchAlbum.isChecked = item.selected
        binding.switchAlbum.setOnCheckedChangeListener { _, checked ->
            onToggle(position, checked)
        }

        binding.root.alpha = if (item.selected) 1f else 0.7f
        binding.root.setOnClickListener {
            binding.switchAlbum.isChecked = !binding.switchAlbum.isChecked
        }
    }
}
