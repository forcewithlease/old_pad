package com.oldpad.app.game

import android.content.Context
import android.graphics.Color
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/** 首页/管理页看到的全部游戏：原生模块 + assets 里的 HTML5 游戏。 */
object GameRegistry {

    private val nativeProviders: List<GameProvider> = listOf(
        com.oldpad.app.game.gomoku.GomokuProvider
        // 在这里加新的原生游戏，一行一个
    )

    fun providers(context: Context): List<GameProvider> {
        val web = discoverWebGames(context)
        return nativeProviders + web
    }

    fun find(context: Context, id: String): GameProvider? =
        providers(context).firstOrNull { it.id == id }

    private fun discoverWebGames(context: Context): List<GameProvider> {
        val result = ArrayList<GameProvider>()
        val assetManager = context.assets
        val dirs = try {
            assetManager.list("games") ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
        for (dir in dirs.sorted()) {
            val manifest = try {
                assetManager.open("games/$dir/game.json").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                continue
            }
            val json = try {
                JSONObject(manifest)
            } catch (e: Exception) {
                continue
            }
            val title = json.optString("title", dir)
            val subtitle = json.optString("subtitle", "小游戏")
            val emoji = json.optString("emoji", "🎮")
            result += WebGameProvider(
                id = "web_" + dir.replace(Regex("[^A-Za-z0-9_-]"), "_"),
                title = title,
                subtitle = subtitle,
                emoji = emoji,
                assetsDir = dir
            )
        }
        return result
    }
}

/** HTML5 游戏：完全离线，从 assets 读取，禁止联网。 */
class WebGameProvider(
    override val id: String,
    override val title: String,
    override val subtitle: String,
    override val emoji: String,
    private val assetsDir: String
) : GameProvider {
    override fun createModule(): GameModule = WebGameModule(assetsDir)
}

class WebGameModule(private val assetsDir: String) : GameModule {

    private var webView: WebView? = null

    override fun createView(context: Context): WebView {
        val wv = WebView(context)
        wv.setBackgroundColor(Color.BLACK)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            blockNetworkLoads = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 150
        }
        wv.webViewClient = WebViewClient()
        wv.loadUrl("file:///android_asset/games/$assetsDir/index.html")
        webView = wv
        return wv
    }

    override fun restart() {
        webView?.reload()
    }

    override fun onResume() {
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
    }
}
