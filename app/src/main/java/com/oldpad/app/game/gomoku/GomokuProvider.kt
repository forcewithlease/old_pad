package com.oldpad.app.game.gomoku

import android.content.Context
import android.view.View
import com.oldpad.app.game.GameModule
import com.oldpad.app.game.GameProvider

object GomokuProvider : GameProvider {
    override val id = "gomoku"
    override val title = "五子棋"
    override val subtitle = "连成五子就赢"
    override val emoji = "⚪"
    override fun createModule(): GameModule = GomokuModule()
}

class GomokuModule : GameModule {
    private var view: GomokuView? = null

    override fun createView(context: Context): View {
        val v = GomokuView(context)
        view = v
        return v
    }

    override fun restart() {
        view?.restart()
    }

    override fun onDestroy() {
        view = null
    }
}
