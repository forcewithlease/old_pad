package com.oldpad.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.oldpad.app.data.Album
import com.oldpad.app.data.PhotoRepository
import com.oldpad.app.data.RecentStore
import com.oldpad.app.databinding.ActivityAlbumBinding
import com.oldpad.app.ui.PhotoGridAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumActivity : BaseActivity() {

    companion object {
        const val EXTRA_KEY = "album_key"
        const val EXTRA_NAME = "album_name"
    }

    private lateinit var binding: ActivityAlbumBinding
    private lateinit var adapter: PhotoGridAdapter
    private var albumKey: String = ""
    private var album: Album? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlbumBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        albumKey = intent.getStringExtra(EXTRA_KEY) ?: run {
            finish()
            return
        }
        binding.title.text = intent.getStringExtra(EXTRA_NAME) ?: ""

        adapter = PhotoGridAdapter { position ->
            val album = album ?: return@PhotoGridAdapter
            startActivity(
                Intent(this, PhotoActivity::class.java)
                    .putExtra(PhotoActivity.EXTRA_KEY, albumKey)
                    .putExtra(PhotoActivity.EXTRA_INDEX, position)
            )
        }
        binding.photoGrid.layoutManager = GridLayoutManager(this, spanCount())
        binding.photoGrid.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }

        RecentStore.touch(this, "al:$albumKey")

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                PhotoRepository.albumFor(this@AlbumActivity, albumKey)
            }
            if (loaded == null || loaded.photos.isEmpty()) {
                binding.emptyHint.visibility = View.VISIBLE
                return@launch
            }
            album = loaded
            binding.title.text = loaded.name
            binding.subtitle.text = getString(R.string.photo_count, loaded.count)
            binding.emptyHint.visibility = View.GONE
            adapter.submit(loaded.photos)
        }
    }

    private fun spanCount(): Int {
        val width = resources.configuration.screenWidthDp
        return when {
            width >= 1000 -> 6
            width >= 700 -> 4
            else -> 3
        }
    }
}
