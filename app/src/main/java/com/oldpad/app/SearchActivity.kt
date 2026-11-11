package com.oldpad.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.oldpad.app.data.FuzzySearch
import com.oldpad.app.data.RecentStore
import com.oldpad.app.data.VideoItem
import com.oldpad.app.data.VideoRepository
import com.oldpad.app.databinding.ActivitySearchBinding
import com.oldpad.app.ui.SearchAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: SearchAdapter

    private var allVideos: List<VideoItem> = emptyList()
    private var searchJob: Job? = null
    private var recognizer: SpeechRecognizer? = null

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoice()
            else toast(getString(R.string.need_mic))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        adapter = SearchAdapter { video -> play(video) }
        binding.results.layoutManager = LinearLayoutManager(this)
        binding.results.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnVoice.setOnClickListener { askMicAndListen() }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                scheduleSearch(s?.toString().orEmpty(), 220)
            }
        })

        lifecycleScope.launch {
            allVideos = withContext(Dispatchers.IO) {
                VideoRepository.videos(this@SearchActivity)
            }
            scheduleSearch(binding.searchInput.text?.toString().orEmpty(), 0)
        }
    }

    private fun scheduleSearch(query: String, delayMs: Long) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            val hits = withContext(Dispatchers.Default) {
                FuzzySearch.search(allVideos, query)
            }
            adapter.submit(hits.map { it.item })
            binding.emptyHint.visibility = if (hits.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun play(video: VideoItem) {
        val group = VideoRepository.groups(this).firstOrNull { g ->
            g.videos.any { it.id == video.id }
        }
        val folderKey = group?.key ?: "__single__${video.id}"
        RecentStore.touch(this, "vg:$folderKey")

        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_VIDEO_ID, video.id)
                .putExtra(PlayerActivity.EXTRA_FOLDER_KEY, folderKey)
        )
    }

    private fun askMicAndListen() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast(getString(R.string.voice_unavailable))
            return
        }
        recognizer?.destroy()
        val engine = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = engine
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.voiceHint.visibility = View.VISIBLE
                binding.voiceHint.text = getString(R.string.listening)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                binding.voiceHint.visibility = View.GONE
                toast("没有听清，请再说一次")
            }

            override fun onResults(results: Bundle?) {
                binding.voiceHint.visibility = View.GONE
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    binding.searchInput.setText(text)
                    binding.searchInput.setSelection(text.length)
                    scheduleSearch(text, 0)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                binding.searchInput.setText(text)
                binding.searchInput.setSelection(text.length)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        engine.startListening(intent)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
