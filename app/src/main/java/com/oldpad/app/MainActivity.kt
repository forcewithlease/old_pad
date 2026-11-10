package com.oldpad.app

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.oldpad.app.data.PhotoRepository
import com.oldpad.app.data.RecentStore
import com.oldpad.app.data.Tile
import com.oldpad.app.data.TileRepository
import com.oldpad.app.data.TileType
import com.oldpad.app.data.VideoRepository
import com.oldpad.app.databinding.ActivityMainBinding
import com.oldpad.app.ui.TileAdapter
import com.oldpad.app.util.MediaPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: TileAdapter

    private var allTiles: List<Tile> = emptyList()
    private var reloadScheduled = false
    private var permissionRequested = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasAnyPermission()) {
                loadTiles(force = true)
            } else {
                showPermissionState()
            }
        }

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (reloadScheduled) return
            reloadScheduled = true
            Handler(Looper.getMainLooper()).postDelayed({
                reloadScheduled = false
                VideoRepository.invalidate()
                PhotoRepository.invalidate()
                loadTiles(force = false)
            }, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = TileAdapter { tile -> openTile(tile) }
        binding.tileList.layoutManager = GridLayoutManager(this, spanCount())
        binding.tileList.adapter = adapter

        binding.btnSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.permissionBanner.setOnClickListener {
            if (hasAnyPermission()) {
                loadTiles(force = true)
            } else {
                permissionRequested = true
                permissionLauncher.launch(MediaPermissions.required())
            }
        }

        if (!hasAnyPermission() && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(MediaPermissions.required())
        }
        applyKeepAwake(true)
    }

    override fun onStart() {
        super.onStart()
        try {
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver
            )
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver
            )
        } catch (e: Exception) {
            // 没有权限时忽略
        }
    }

    override fun onStop() {
        try {
            contentResolver.unregisterContentObserver(mediaObserver)
        } catch (e: Exception) {
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        loadTiles(force = false)
    }

    private fun hasAnyPermission(): Boolean =
        MediaPermissions.hasVideo(this) || MediaPermissions.hasImages(this)

    private fun loadTiles(force: Boolean) {
        lifecycleScope.launch {
            binding.loading.visibility = View.VISIBLE
            binding.stateBox.visibility = View.GONE

            val tiles = withContext(Dispatchers.IO) {
                if (force) {
                    VideoRepository.invalidate()
                    PhotoRepository.invalidate()
                }
                TileRepository.all(this@MainActivity)
            }
            allTiles = tiles

            val shown = withContext(Dispatchers.Default) {
                TileRepository.withRecent(this@MainActivity, tiles)
            }
            adapter.submit(shown)

            binding.loading.visibility = View.GONE
            val hasMedia = shown.any { it.type != TileType.GAME }
            updateBanner(hasMedia)

            if (shown.isEmpty()) {
                if (!hasAnyPermission()) showPermissionState() else showEmptyState()
            } else {
                binding.stateBox.visibility = View.GONE
            }
        }
    }

    private fun updateBanner(hasMedia: Boolean) {
        if (!hasAnyPermission()) {
            binding.permissionBanner.visibility = View.VISIBLE
            binding.permissionBanner.text = getString(R.string.permission_banner)
        } else if (!hasMedia) {
            binding.permissionBanner.visibility = View.VISIBLE
            binding.permissionBanner.text = getString(R.string.rescan_banner)
        } else {
            binding.permissionBanner.visibility = View.GONE
        }
    }

    private fun showPermissionState() {
        binding.loading.visibility = View.GONE
        showState(
            title = getString(R.string.permission_title),
            hint = getString(R.string.permission_hint),
            button = getString(R.string.grant)
        ) {
            permissionRequested = true
            permissionLauncher.launch(MediaPermissions.required())
        }
    }

    private fun showEmptyState() {
        showState(
            title = getString(R.string.empty_title),
            hint = getString(R.string.empty_hint),
            button = getString(R.string.rescan)
        ) {
            loadTiles(force = true)
        }
    }

    private fun showState(title: String, hint: String, button: String, action: () -> Unit) {
        binding.stateBox.visibility = View.VISIBLE
        binding.stateTitle.text = title
        binding.stateHint.text = hint
        binding.stateButton.text = button
        binding.stateButton.setOnClickListener { action() }
    }

    private fun openTile(tile: Tile) {
        when (tile.type) {
            TileType.GAME -> {
                RecentStore.touch(this, tile.id)
                startActivity(
                    Intent(this, GameActivity::class.java)
                        .putExtra(GameActivity.EXTRA_GAME_ID, tile.payload)
                )
            }

            TileType.ALBUM -> {
                RecentStore.touch(this, tile.id)
                startActivity(
                    Intent(this, AlbumActivity::class.java)
                        .putExtra(AlbumActivity.EXTRA_KEY, tile.payload)
                        .putExtra(AlbumActivity.EXTRA_NAME, tile.title)
                )
            }

            TileType.VIDEO -> openVideoTile(tile)
        }
    }

    private fun openVideoTile(tile: Tile) {
        RecentStore.touch(this, tile.id)

        if (tile.count > 1) {
            startActivity(
                Intent(this, SeriesActivity::class.java)
                    .putExtra(SeriesActivity.EXTRA_KEY, tile.payload)
                    .putExtra(SeriesActivity.EXTRA_NAME, tile.title)
            )
            return
        }

        val id = tile.payload.removePrefix("__single__").toLongOrNull()
        val video = if (id != null) VideoRepository.videoById(this, id) else null
        if (video == null) {
            Toast.makeText(this, R.string.video_error, Toast.LENGTH_LONG).show()
            loadTiles(force = true)
            return
        }
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_VIDEO_ID, video.id)
                .putExtra(PlayerActivity.EXTRA_FOLDER_KEY, tile.payload)
        )
    }

    private fun spanCount(): Int {
        val width = resources.configuration.screenWidthDp
        return when {
            width >= 1100 -> 5
            width >= 820 -> 4
            width >= 600 -> 3
            else -> 2
        }
    }
}
