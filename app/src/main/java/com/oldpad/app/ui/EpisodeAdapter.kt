package com.oldpad.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oldpad.app.R
import com.oldpad.app.data.PlaybackStore
import com.oldpad.app.data.VideoItem
import com.oldpad.app.databinding.ItemEpisodeBinding
import com.oldpad.app.util.episodeNumber
import com.oldpad.app.util.formatHuman
import java.util.Locale

class EpisodeAdapter(
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.Holder>() {

    private val items = ArrayList<VideoItem>()

    fun submit(list: List<VideoItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemEpisodeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemEpisodeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val video = items[position]
        val binding = holder.binding
        val context = binding.root.context

        val number = episodeNumber(video.title) ?: (position + 1).toLong()
        binding.number.text = String.format(Locale.US, "%02d", number)
        binding.title.text = video.title

        val percent = PlaybackStore.percent(context, video.id, video.durationMs)
        binding.progressText.text = when {
            percent >= 100 -> context.getString(R.string.episode_watched)
            percent > 0 -> context.getString(R.string.episode_progress, percent)
            else -> formatHuman(video.durationMs)
        }
        binding.progressText.setTextColor(
            if (percent in 1..99) context.getColor(R.color.accent) else context.getColor(R.color.text_dim)
        )

        binding.root.setOnClickListener { onClick(video) }
    }
}
