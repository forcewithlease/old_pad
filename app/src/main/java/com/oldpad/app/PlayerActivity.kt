package com.oldpad.app

import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import com.oldpad.app.data.AppSettings
import com.oldpad.app.data.PlaybackStore
import com.oldpad.app.data.SubtitleFile
import com.oldpad.app.data.SubtitleFinder
import com.oldpad.app.data.VideoItem
import com.oldpad.app.data.VideoRepository
import com.oldpad.app.databinding.ActivityPlayerBinding
import com.oldpad.app.ui.Dialogs
import com.oldpad.app.util.formatClock
import java.util.Locale

class PlayerActivity : BaseActivity() {

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_FOLDER_KEY = "folder_key"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    private lateinit var video: VideoItem
    private var playlist: List<VideoItem> = emptyList()
    private var currentIndex = 0
    private var subtitles: List<SubtitleFile> = emptyList()

    private val speeds = floatArrayOf(1f, 0.75f, 0.5f)
    private var speedIndex = 0
    private var subtitleOn = true

    private var controlsVisible = true
    private var locked = false
    private var isDragging = false
    private var resumeWasPlaying = false
    private var showResumeHint = true
    private var firstPlay = true

    private lateinit var audioManager: AudioManager

    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hudHandler = Handler(Looper.getMainLooper())
    private val progressHandler = Handler(Looper.getMainLooper())

    private val hideControlsRunnable = Runnable { hideControls() }
    private val hideHudRunnable = Runnable { binding.hud.visibility = View.GONE }
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            progressHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoId = intent.getLongExtra(EXTRA_VIDEO_ID, -1L)
        val found = VideoRepository.videoById(this, videoId)
        if (found == null) {
            Toast.makeText(this, R.string.video_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        video = found

        val folderKey = intent.getStringExtra(EXTRA_FOLDER_KEY)
            ?: "__single__${video.id}"
        playlist = if (folderKey.startsWith("__single__")) {
            listOf(video)
        } else {
            VideoRepository.playlist(this, folderKey).ifEmpty { listOf(video) }
        }
        currentIndex = playlist.indexOfFirst { it.id == video.id }.coerceAtLeast(0)

        speedIndex = speeds.indexOfFirst { it == PlaybackStore.speed(this) }
        if (speedIndex < 0) speedIndex = 0
        subtitleOn = AppSettings.subtitlesOn(this)

        audioManager = getSystemService(AudioManager::class.java)
        setVolumeControlStream(AudioManager.STREAM_MUSIC)
        applyKeepAwake(AppSettings.keepAwake(this))

        binding.playerView.useController = false
        binding.title.text = video.title

        binding.playerView.subtitleView?.let { subtitleView ->
            subtitleView.setStyle(
                CaptionStyleCompat(
                    Color.WHITE,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    Color.BLACK,
                    null
                )
            )
            subtitleView.setFractionalTextSize(AppSettings.subtitleScale(this))
        }

        setupButtons()
        updateSpeedLabel()
        updateSubtitleButton()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (locked) {
                    showHud(getString(R.string.locked_hint))
                } else {
                    confirmExit()
                }
            }
        })
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { confirmExit() }
        binding.btnLock.setOnClickListener { lock() }
        binding.btnPlayPause.setOnClickListener { togglePlay() }
        binding.btnSlow.setOnClickListener { cycleSpeed() }
        binding.btnRewind.setOnClickListener { seekBy(-30_000) }
        binding.btnForward.setOnClickListener { seekBy(30_000) }
        binding.btnVolDown.setOnClickListener { changeVolume(-1) }
        binding.btnVolUp.setOnClickListener { changeVolume(1) }
        binding.btnSubtitle.setOnClickListener { toggleSubtitles() }

        val toggle = View.OnClickListener { toggleControls() }
        binding.playerRoot.setOnClickListener(toggle)
        binding.playerView.setOnClickListener(toggle)

        binding.playerRoot.setOnLongClickListener {
            if (locked) {
                unlock()
                true
            } else {
                false
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser || !isDragging) return
                val duration = player?.duration ?: return
                if (duration > 0) {
                    binding.timeCurrent.text = formatClock(duration * progress / 1000)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isDragging = true
                resumeWasPlaying = player?.isPlaying == true
                player?.pause()
                controlsHandler.removeCallbacks(hideControlsRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val p = player
                if (p != null) {
                    val duration = p.duration
                    if (duration > 0) {
                        p.seekTo(duration * seekBar.progress / 1000)
                    }
                    if (resumeWasPlaying) p.play()
                }
                isDragging = false
                updateProgress()
                scheduleHide()
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (!this::video.isInitialized) return
        createPlayer()
        startPlayback(video, resume = true)
        progressHandler.post(progressRunnable)
    }

    override fun onPause() {
        savePosition()
        super.onPause()
    }

    override fun onStop() {
        savePosition()
        progressHandler.removeCallbacks(progressRunnable)
        controlsHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        binding.playerView.player = null
        super.onStop()
    }

    private fun createPlayer() {
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setSelectUndeterminedTextLanguage(true)
                    .build()
            )
        }
        val p = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()

        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )

        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onEnded()
                }
                if (playbackState == Player.STATE_READY) {
                    updateProgress()
                    updatePlayPauseIcon()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseIcon()
                if (isPlaying) {
                    scheduleHide()
                } else {
                    controlsHandler.removeCallbacks(hideControlsRunnable)
                    if (!locked) binding.controls.visibility = View.VISIBLE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                showHud(getString(R.string.video_error))
                showControls()
            }
        })

        player = p
        binding.playerView.player = p
    }

    private fun startPlayback(item: VideoItem, resume: Boolean) {
        val p = player ?: return
        video = item
        currentIndex = playlist.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        binding.title.text = item.title

        subtitles = SubtitleFinder.find(item)
        p.setMediaItem(buildMediaItem(item, subtitles))
        p.prepare()
        p.playWhenReady = true
        p.setPlaybackSpeed(speeds[speedIndex])

        var start = if (resume) PlaybackStore.position(this, item.id) else 0L
        val duration = item.durationMs
        if (start < 5_000 || (duration > 0 && start > duration - 15_000)) {
            start = 0
        }
        if (start > 0) {
            p.seekTo(start)
            if (firstPlay) {
                showHud(getString(R.string.resume_hint))
            }
        }
        firstPlay = false

        updateSubtitleButton()
        applySubtitleState()
        updatePlayPauseIcon()
        showControls()
    }

    private fun buildMediaItem(item: VideoItem, subs: List<SubtitleFile>): MediaItem {
        val builder = MediaItem.Builder().setUri(item.uri)
        if (subs.isNotEmpty()) {
            builder.setSubtitleConfigurations(
                subs.map { subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitle.file))
                        .setMimeType(subtitle.mimeType)
                        .setLanguage(subtitle.language)
                        .setLabel(subtitle.label)
                        .build()
                }
            )
        }
        return builder.build()
    }

    private fun applySubtitleState() {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitleOn || subtitles.isEmpty())
            .setSelectUndeterminedTextLanguage(true)
            .build()
    }

    private fun toggleSubtitles() {
        if (subtitles.isEmpty()) return
        subtitleOn = !subtitleOn
        AppSettings.prefs(this).edit()
            .putBoolean(AppSettings.KEY_SUBTITLE_ON, subtitleOn)
            .apply()
        applySubtitleState()
        updateSubtitleButton()
        showHud(if (subtitleOn) getString(R.string.subtitles_on) else getString(R.string.subtitles_off))
    }

    private fun updateSubtitleButton() {
        binding.btnSubtitle.visibility =
            if (subtitles.isEmpty()) View.GONE else View.VISIBLE
        binding.btnSubtitle.text = if (subtitleOn) {
            getString(R.string.subtitles_on)
        } else {
            getString(R.string.subtitles_off)
        }
    }

    private fun onEnded() {
        PlaybackStore.savePosition(this, video.id, 0)
        val next = playlist.getOrNull(currentIndex + 1)
        if (next != null) {
            showHud(getString(R.string.next_episode))
            startPlayback(next, resume = false)
        } else {
            showHud(getString(R.string.finished))
            showControls()
            updatePlayPauseIcon()
        }
    }

    private fun togglePlay() {
        val p = player ?: return
        when {
            p.playbackState == Player.STATE_ENDED -> {
                p.seekTo(0)
                p.play()
            }
            p.isPlaying -> p.pause()
            else -> p.play()
        }
        showControls()
    }

    private fun updatePlayPauseIcon() {
        val p = player ?: return
        val playing = p.isPlaying && p.playbackState != Player.STATE_ENDED
        binding.btnPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun cycleSpeed() {
        speedIndex = (speedIndex + 1) % speeds.size
        val speed = speeds[speedIndex]
        player?.setPlaybackSpeed(speed)
        PlaybackStore.saveSpeed(this, speed)
        updateSpeedLabel()
        showHud(
            if (speed == 1f) "正常速度"
            else String.format(Locale.US, "已放慢到 %.2f 倍", speed)
        )
    }

    private fun updateSpeedLabel() {
        val speed = speeds[speedIndex]
        binding.btnSlow.text = if (speed == 1f) {
            getString(R.string.speed)
        } else {
            String.format(Locale.US, "%.2f倍", speed)
        }
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration
        if (duration <= 0) return
        val target = (p.currentPosition + deltaMs).coerceIn(0, duration)
        p.seekTo(target)
        updateProgress()
        showHud(if (deltaMs < 0) "后退 30 秒" else "前进 30 秒")
        showControls()
    }

    private fun changeVolume(direction: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        val step = (max / 10).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val next = (current + direction * step).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        showHud("音量 " + (next * 100 / max) + "%")
        showControls()
    }

    private fun updateProgress() {
        val p = player ?: return
        if (isDragging) return
        val duration = p.duration
        if (duration <= 0) return
        val position = p.currentPosition.coerceIn(0, duration)
        binding.seekBar.progress = (position * 1000 / duration).toInt()
        binding.timeCurrent.text = formatClock(position)
        binding.timeTotal.text = formatClock(duration)
    }

    private fun savePosition() {
        val p = player ?: return
        val duration = p.duration
        val position = p.currentPosition
        if (duration > 0 && position > duration - 10_000) {
            PlaybackStore.savePosition(this, video.id, 0)
        } else if (position > 0) {
            PlaybackStore.savePosition(this, video.id, position)
        }
    }

    private fun toggleControls() {
        if (locked) return
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        if (locked) return
        controlsVisible = true
        binding.controls.visibility = View.VISIBLE
        scheduleHide()
    }

    private fun hideControls() {
        if (locked) return
        controlsVisible = false
        binding.controls.visibility = View.GONE
        controlsHandler.removeCallbacks(hideControlsRunnable)
    }

    private fun scheduleHide() {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        if (player?.isPlaying == true) {
            controlsHandler.postDelayed(hideControlsRunnable, 5_000)
        }
    }

    private fun lock() {
        locked = true
        controlsVisible = false
        binding.controls.visibility = View.GONE
        binding.lockedHint.visibility = View.VISIBLE
        controlsHandler.removeCallbacks(hideControlsRunnable)
    }

    private fun unlock() {
        locked = false
        binding.lockedHint.visibility = View.GONE
        showControls()
        showHud(getString(R.string.unlocked))
    }

    private fun showHud(text: String) {
        binding.hud.text = text
        binding.hud.visibility = View.VISIBLE
        hudHandler.removeCallbacks(hideHudRunnable)
        hudHandler.postDelayed(hideHudRunnable, 2_200)
    }

    private fun confirmExit() {
        if (locked) {
            showHud(getString(R.string.locked_hint))
            return
        }
        Dialogs.confirm(
            this,
            getString(R.string.exit_title),
            getString(R.string.exit_message),
            getString(R.string.exit),
            getString(R.string.stay)
        ) {
            finish()
        }
    }
}
