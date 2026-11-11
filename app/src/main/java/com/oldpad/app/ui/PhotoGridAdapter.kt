package com.oldpad.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oldpad.app.data.PhotoItem
import com.oldpad.app.databinding.ItemPhotoBinding

class PhotoGridAdapter(
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<PhotoGridAdapter.Holder>() {

    private val items = ArrayList<PhotoItem>()

    fun submit(list: List<PhotoItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPhotoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.photo.clipToOutline = true
        PhotoLoader.load(holder.binding.photo, items[position], 420)
        holder.binding.photo.setOnClickListener { onClick(position) }
    }
}
