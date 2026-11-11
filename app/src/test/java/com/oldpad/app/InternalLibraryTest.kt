package com.oldpad.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.oldpad.app.data.InternalLibrary
import com.oldpad.app.data.PhotoRepository
import com.oldpad.app.data.VideoRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InternalLibraryTest {

    @Test
    fun uploadedVideoBecomesGroupInRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val group = InternalLibrary.createGroup(context, InternalLibrary.TYPE_VIDEO, "快传测试组")
            ?: error("group create failed")
        try {
            val dir = InternalLibrary.groupDir(context, group)
            File(dir, "第01集.mp4").writeBytes(ByteArray(16))

            val groups = VideoRepository.groups(context)
            val found = groups.firstOrNull { it.key == "internal:${group.id}" }
            assertTrue(found != null)
            assertEquals("快传测试组", found!!.name)
            assertEquals(1, found.size)

            val playlist = VideoRepository.playlist(context, "internal:${group.id}")
            assertEquals(1, playlist.size)
            assertEquals("第01集", playlist.first().title)
        } finally {
            InternalLibrary.deleteGroup(context, group.id)
        }
    }

    @Test
    fun uploadedPhotoBecomesAlbum() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val group = InternalLibrary.createGroup(context, InternalLibrary.TYPE_PHOTO, "快传相册测试")
            ?: error("group create failed")
        try {
            val dir = InternalLibrary.groupDir(context, group)
            File(dir, "照片1.jpg").writeBytes(ByteArray(16))

            val albums = PhotoRepository.albums(context)
            val found = albums.firstOrNull { it.key == "internal:${group.id}" }
            assertTrue(found != null)
            assertEquals("快传相册测试", found!!.name)
            assertEquals(1, found.count)

            val album = PhotoRepository.albumFor(context, "internal:${group.id}")
            assertEquals(1, album?.count)
        } finally {
            InternalLibrary.deleteGroup(context, group.id)
        }
    }

    @Test
    fun duplicateGroupNameIsRejected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = InternalLibrary.createGroup(context, InternalLibrary.TYPE_VIDEO, "重复分组")
        try {
            assertTrue(first != null)
            assertEquals(null, InternalLibrary.createGroup(context, InternalLibrary.TYPE_VIDEO, "重复分组"))
        } finally {
            first?.let { InternalLibrary.deleteGroup(context, it.id) }
        }
    }

    @Test
    fun renameGroupUpdatesNameAndRejectsDuplicate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = InternalLibrary.createGroup(context, InternalLibrary.TYPE_VIDEO, "旧分组名")
        val second = InternalLibrary.createGroup(context, InternalLibrary.TYPE_VIDEO, "已有分组")
        try {
            assertTrue(first != null)
            assertTrue(second != null)
            assertTrue(InternalLibrary.renameGroup(context, first!!.id, "新分组名"))
            assertEquals("新分组名", InternalLibrary.group(context, first.id)?.name)
            assertFalse(InternalLibrary.renameGroup(context, first.id, "已有分组"))
            assertEquals("新分组名", InternalLibrary.group(context, first.id)?.name)
        } finally {
            first?.let { InternalLibrary.deleteGroup(context, it.id) }
            second?.let { InternalLibrary.deleteGroup(context, it.id) }
        }
    }

    @Test
    fun unsafeFileNameIsSanitized() {
        val name = InternalLibrary.safeFileName("../../evil/第01集.mp4")
        assertFalse(name.contains(".."))
        assertFalse(name.contains("/"))
        assertTrue(name.endsWith(".mp4"))
    }
}
