package com.oldpad.app.game

import android.content.Context
import android.view.View

/**
 * 一个游戏模块 = 一个可以放进首页的游戏。
 *
 * 加新游戏（原生）：
 *   1. 实现 GameModule，返回游戏的 View；
 *   2. 实现 GameProvider，在 GameRegistry.nativeProviders 里加一行；
 *   3. 不用改首页、设置、排序逻辑。
 *
 * 加新游戏（开源 HTML5）：
 *   把游戏放到 assets/games/<名字>/ 下，带上 index.html 和 game.json，
 *   会自动出现在首页和管理页，见 assets/games/README.md。
 */
interface GameModule {
    fun createView(context: Context): View
    fun restart() {}
    fun onResume() {}
    fun onPause() {}
    fun onDestroy() {}
}

interface GameProvider {
    val id: String
    val title: String
    val subtitle: String
    val emoji: String
    fun createModule(): GameModule
}
