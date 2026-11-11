package com.oldpad.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.oldpad.app.data.AppSettings
import com.oldpad.app.data.RecentStore
import com.oldpad.app.databinding.ActivityGameBinding
import com.oldpad.app.game.GameModule
import com.oldpad.app.game.GameRegistry
import com.oldpad.app.ui.Dialogs

/** 通用游戏容器：所有游戏（原生或 HTML5）都从这里打开。 */
class GameActivity : BaseActivity() {

    companion object {
        const val EXTRA_GAME_ID = "game_id"
    }

    private lateinit var binding: ActivityGameBinding
    private var module: GameModule? = null
    private var gameTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        val id = intent.getStringExtra(EXTRA_GAME_ID) ?: run {
            finish()
            return
        }
        val provider = GameRegistry.find(this, id) ?: run {
            finish()
            return
        }

        when (provider.orientation) {
            "landscape" -> requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        gameTitle = provider.title
        binding.title.text = provider.title
        binding.subtitle.text = provider.subtitle

        val game = provider.createModule()
        module = game
        binding.gameContainer.addView(game.createView(this))

        RecentStore.touch(this, "g:$id")
        applyKeepAwake(AppSettings.keepAwake(this))

        binding.btnBack.setOnClickListener { confirmExit() }
        binding.btnRestart.setOnClickListener {
            Dialogs.confirm(
                this,
                getString(R.string.game_restart_title),
                getString(R.string.game_restart_message),
                getString(R.string.game_restart_short),
                getString(R.string.keep_playing)
            ) {
                module?.restart()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmExit()
            }
        })
    }

    private fun confirmExit() {
        Dialogs.confirm(
            this,
            getString(R.string.game_exit_title),
            getString(R.string.game_exit_message),
            getString(R.string.exit),
            getString(R.string.keep_playing)
        ) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        module?.onResume()
    }

    override fun onPause() {
        module?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        module?.onDestroy()
        module = null
        super.onDestroy()
    }
}
