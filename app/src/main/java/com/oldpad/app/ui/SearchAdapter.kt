package com.oldpad.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oldpad.app.R
import com.oldpad.app.data.VideoItem
import com.oldpad.app.databinding.ItemSearchBinding
import com.oldpad.app.util.episodeNumber
import com.oldpad.app.util.formatHuman

class SearchAdapter(
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<SearchAdapter.Holder>() {

    private val items = ArrayList<VideoItem>()

    fun submit(list: List<VideoItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemSearchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSearchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val video = items[position]
        val binding = holder.binding

        binding.title.text = video.title
        val duration = formatHuman(video.durationMs)
        binding.meta.text = if (duration.isNotEmpty()) {
            binding.root.context.getString(R.string.search_result_from, video.folder) + " · " + duration
        } else {
            binding.root.context.getString(R.string.search_result_from, video.folder)
        }

        binding.root.setOnClickListener { onClick(video) }
    }
}
