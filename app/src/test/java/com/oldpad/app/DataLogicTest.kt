package com.oldpad.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.oldpad.app.data.FuzzySearch
import com.oldpad.app.data.HomeConfig
import com.oldpad.app.data.SubtitleFinder
import com.oldpad.app.data.Tile
import com.oldpad.app.data.TileType
import com.oldpad.app.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DataLogicTest {

    private fun video(
        id: Long,
        title: String,
        folder: String = "西游记",
        path: String? = null
    ) = VideoItem(
        id = id,
        uri = Uri.parse("content://test/video/$id"),
        title = title,
        folder = folder,
        folderKey = folder,
        durationMs = 0,
        dateAdded = 0,
        path = path
    )

    @Test
    fun findsSameNameSubtitlesAndPrefersChinese() {
        val dir = Files.createTempDirectory("oldpad-sub").toFile()
        try {
            File(dir, "第01集.mp4").writeBytes(ByteArray(0))
            File(dir, "第01集.srt").writeText("1\n00:00:00,000 --> 00:00:01,000\n你好\n")
            File(dir, "第01集.chs.ass").writeText("[Script Info]\n")
            File(dir, "第02集.srt").writeText("")

            val tracks = SubtitleFinder.find(
                video(1, "第01集", path = File(dir, "第01集.mp4").absolutePath)
            )

            assertEquals(2, tracks.size)
            assertEquals("简体中文", tracks.first().label)
            assertEquals("zh", tracks.first().language)
            assertTrue(tracks.any { it.file.name == "第01集.srt" })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun subtitleFinderIgnoresMissingFile() {
        assertTrue(SubtitleFinder.find(video(2, "第01集", path = null)).isEmpty())
    }

    @Test
    fun convertsGbkSubtitleToUtf8() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = Files.createTempDirectory("oldpad-gbk").toFile()
        try {
            val videoFile = File(dir, "第01集.mp4").apply { writeBytes(ByteArray(0)) }
            File(dir, "第01集.srt").writeText(
                "1\n00:00:00,000 --> 00:00:01,000\n你好，世界\n",
                Charset.forName("GBK")
            )

            val track = SubtitleFinder.find(
                video(3, "第01集", path = videoFile.absolutePath)
            ).single()
            val prepared = SubtitleFinder.prepareUtf8(context, track)

            assertTrue(prepared != track.file)
            assertTrue(prepared.readText(Charsets.UTF_8).contains("你好，世界"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun fuzzySearchMatchesCharactersInOrder() {
        val items = listOf(
            video(1, "西游记 第01集"),
            video(2, "西游记 第10集"),
            video(3, "西游记之三打白骨精", folder = "电影"),
            video(4, "三国演义 第01集", folder = "三国演义")
        )

        val hits = FuzzySearch.search(items, "西游三打")
        assertEquals(1, hits.size)
        assertEquals(3L, hits.first().item.id)

        assertEquals(3, FuzzySearch.search(items, "西游记").size)
        assertEquals(1, FuzzySearch.search(items, "三国").size)
    }

    @Test
    fun fuzzySearchMatchesFolderName() {
        val items = listOf(
            video(1, "第01集", folder = "西游记"),
            video(2, "第01集", folder = "三国演义")
        )
        val hits = FuzzySearch.search(items, "西游")
        assertEquals(1, hits.size)
        assertEquals("西游记", hits.first().item.folder)
    }

    @Test
    fun homeOrderAndHiddenAreRespected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HomeConfig.reset(context)

        val a = Tile("a", TileType.GAME, "A", "", null, "🎮", "a")
        val b = Tile("b", TileType.GAME, "B", "", null, "🎮", "b")
        val c = Tile("c", TileType.GAME, "C", "", null, "🎮", "c")

        assertEquals(listOf("a", "b", "c"), HomeConfig.ordered(context, listOf(a, b, c)).map { it.id })

        HomeConfig.saveOrder(context, listOf("c", "a", "b"))
        HomeConfig.setHidden(context, setOf("b"))
        val ordered = HomeConfig.ordered(context, listOf(a, b, c))
        assertEquals(listOf("c", "a"), ordered.map { it.id })
        assertFalse(ordered.any { it.id == "b" })

        HomeConfig.reset(context)
    }
}
