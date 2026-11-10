package com.oldpad.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.oldpad.app.data.Album
import com.oldpad.app.data.AppSettings
import com.oldpad.app.data.PhotoRepository
import com.oldpad.app.databinding.ActivityPhotoBinding
import com.oldpad.app.ui.PhotoLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoActivity : BaseActivity() {

    companion object {
        const val EXTRA_KEY = "album_key"
        const val EXTRA_INDEX = "photo_index"
    }

    private lateinit var binding: ActivityPhotoBinding
    private var album: Album? = null
    private var index = 0
    private var slideshow = false

    private val handler = Handler(Looper.getMainLooper())
    private val slideshowRunnable = object : Runnable {
        override fun run() {
            if (!slideshow) return
            show((index + 1) % (album?.count ?: 1))
            handler.postDelayed(this, 4000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyKeepAwake(AppSettings.keepAwake(this))

        val key = intent.getStringExtra(EXTRA_KEY) ?: run {
            finish()
            return
        }
        index = intent.getIntExtra(EXTRA_INDEX, 0)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPrev.setOnClickListener { step(-1) }
        binding.btnNext.setOnClickListener { step(1) }
        binding.btnSlideshow.setOnClickListener { toggleSlideshow() }

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                PhotoRepository.albumFor(this@PhotoActivity, key)
            }
            if (loaded == null || loaded.photos.isEmpty()) {
                finish()
                return@launch
            }
            album = loaded
            show(index)
        }
    }

    private fun step(delta: Int) {
        val count = album?.count ?: return
        if (count <= 1) return
        show((index + delta + count) % count)
    }

    private fun show(newIndex: Int) {
        val album = album ?: return
        if (album.photos.isEmpty()) return
        index = newIndex.coerceIn(0, album.count - 1)

        binding.counter.text = getString(R.string.photo_position, index + 1, album.count)
        binding.loading.visibility = View.VISIBLE
        PhotoLoader.load(binding.photoImage, album.photos[index], 2048) {
            binding.loading.visibility = View.GONE
        }
    }

    private fun toggleSlideshow() {
        slideshow = !slideshow
        if (slideshow) {
            binding.btnSlideshow.text = getString(R.string.slideshow_stop)
            handler.postDelayed(slideshowRunnable, 4000)
        } else {
            stopSlideshow()
        }
    }

    private fun stopSlideshow() {
        slideshow = false
        handler.removeCallbacks(slideshowRunnable)
        binding.btnSlideshow.text = getString(R.string.slideshow)
    }

    override fun onStop() {
        stopSlideshow()
        super.onStop()
    }
}
