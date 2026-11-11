package com.oldpad.app.game.junqi

import android.content.Context
import android.view.View
import com.oldpad.app.game.GameModule
import com.oldpad.app.game.GameProvider

object JunqiProvider : GameProvider {
    override val id = "junqi"
    override val title = "军棋翻棋"
    override val subtitle = "翻开棋子，吃掉对方军旗就赢"
    override val emoji = "🚩"
    override val orientation = "landscape"
    override fun createModule(): GameModule = JunqiModule()
}

class JunqiModule : GameModule {
    private var view: JunqiView? = null

    override fun createView(context: Context): View {
        val v = JunqiView(context)
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
