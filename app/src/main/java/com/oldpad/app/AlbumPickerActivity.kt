package com.oldpad.app

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.oldpad.app.data.Album
import com.oldpad.app.data.AlbumConfig
import com.oldpad.app.data.PhotoRepository
import com.oldpad.app.databinding.ActivityAlbumPickerBinding
import com.oldpad.app.ui.AlbumSelectAdapter
import com.oldpad.app.ui.AlbumSelectItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 让家人自己决定哪些照片文件夹可以出现在首页。 */
class AlbumPickerActivity : BaseActivity() {

    private lateinit var binding: ActivityAlbumPickerBinding
    private lateinit var adapter: AlbumSelectAdapter

    private val albums = mutableListOf<Album>()
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlbumPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        binding.btnBack.setOnClickListener { finish() }

        adapter = AlbumSelectAdapter { position, checked ->
            val album = albums.getOrNull(position) ?: return@AlbumSelectAdapter
            if (checked) selected.add(album.key) else selected.remove(album.key)
            AlbumConfig.setEnabledFolders(this, selected)
            adapter.submit(buildItems())
        }
        binding.albumList.layoutManager = LinearLayoutManager(this)
        binding.albumList.adapter = adapter

        binding.switchAllPhotos.isChecked = AlbumConfig.allPhotosEnabled(this)
        binding.switchAllPhotos.setOnCheckedChangeListener { _, checked ->
            AlbumConfig.setAllPhotosEnabled(this, checked)
        }
    }

    override fun onResume() {
        super.onResume()
        loadAlbums()
    }

    private fun loadAlbums() {
        lifecycleScope.launch {
            val detected = withContext(Dispatchers.IO) {
                PhotoRepository.allAlbums(this@AlbumPickerActivity, force = true)
            }
            albums.clear()
            albums.addAll(detected)
            selected.clear()
            selected.addAll(AlbumConfig.enabledFolders(this@AlbumPickerActivity))

            binding.emptyHint.visibility =
                if (albums.isEmpty()) View.VISIBLE else View.GONE
            adapter.submit(buildItems())
        }
    }

    private fun buildItems(): List<AlbumSelectItem> =
        albums.map { AlbumSelectItem(it, it.key in selected) }
}
