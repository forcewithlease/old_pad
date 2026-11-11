package com.oldpad.app

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.oldpad.app.data.RecentStore
import com.oldpad.app.data.VideoItem
import com.oldpad.app.data.VideoRepository
import com.oldpad.app.databinding.ActivitySeriesBinding
import com.oldpad.app.ui.EpisodeAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeriesActivity : BaseActivity() {

    companion object {
        const val EXTRA_KEY = "folder_key"
        const val EXTRA_NAME = "name"
    }

    private lateinit var binding: ActivitySeriesBinding
    private lateinit var adapter: EpisodeAdapter
    private var folderKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        folderKey = intent.getStringExtra(EXTRA_KEY) ?: run {
            finish()
            return
        }
        binding.title.text = intent.getStringExtra(EXTRA_NAME) ?: ""

        adapter = EpisodeAdapter { video -> play(video) }
        binding.episodeList.layoutManager = LinearLayoutManager(this)
        binding.episodeList.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            val group = withContext(Dispatchers.IO) {
                VideoRepository.groupFor(this@SeriesActivity, folderKey)
            }
            if (group == null) {
                finish()
                return@launch
            }
            binding.title.text = group.name
            binding.subtitle.text = getString(R.string.series_count, group.size)
            adapter.submit(group.videos)
        }
    }

    private fun play(video: VideoItem) {
        RecentStore.touch(this, "vg:$folderKey")
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_VIDEO_ID, video.id)
                .putExtra(PlayerActivity.EXTRA_FOLDER_KEY, folderKey)
        )
    }
}
