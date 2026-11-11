package com.oldpad.app

import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 冒烟测试：保证主要页面能正常创建、布局不炸。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreensSmokeTest {

    @Test
    fun mainScreenInflates() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get().findViewById(R.id.tileList))
        val list = controller.get().findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.tileList)
        val deadline = System.currentTimeMillis() + 5_000
        while ((list.adapter?.itemCount ?: 0) == 0 && System.currentTimeMillis() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(30)
        }
        org.junit.Assert.assertTrue(
            "首页至少应该有五子棋格子",
            (list.adapter?.itemCount ?: 0) > 0
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun settingsScreenInflates() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        assertNotNull(controller.get().findViewById(R.id.btnRescan))
        assertNotNull(controller.get().findViewById(R.id.switchAutoStart))
        controller.pause().stop().destroy()
    }

    @Test
    fun searchScreenInflates() {
        val controller = Robolectric.buildActivity(SearchActivity::class.java).setup()
        assertNotNull(controller.get().findViewById(R.id.searchInput))
        controller.pause().stop().destroy()
    }

    @Test
    fun gomokuGameStarts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, GameActivity::class.java)
            .putExtra(GameActivity.EXTRA_GAME_ID, "gomoku")
        val controller = Robolectric.buildActivity(GameActivity::class.java, intent).setup()
        assertNotNull(controller.get().findViewById(R.id.gameContainer))
        val title = controller.get().findViewById<TextView>(R.id.title)
        assertEquals("五子棋", title.text.toString())
        controller.pause().stop().destroy()
    }

    @Test
    fun manageHomeScreenInflates() {
        val controller = Robolectric.buildActivity(ManageHomeActivity::class.java).setup()
        assertNotNull(controller.get().findViewById(R.id.manageList))
        assertNotNull(controller.get().findViewById(R.id.btnReset))
        controller.pause().stop().destroy()
    }

    @Test
    fun albumScreenInflates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, AlbumActivity::class.java)
            .putExtra(AlbumActivity.EXTRA_KEY, "missing")
        val controller = Robolectric.buildActivity(AlbumActivity::class.java, intent).setup()
        assertNotNull(controller.get().findViewById(R.id.photoGrid))
        controller.pause().stop().destroy()
    }

    @Test
    fun playerFinishesGracefullyWhenVideoMissing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_VIDEO_ID, 12345L)
        val controller = Robolectric.buildActivity(PlayerActivity::class.java, intent).setup()
        assertNotNull(controller.get().findViewById(R.id.playerView))
        controller.pause().stop().destroy()
    }
}
