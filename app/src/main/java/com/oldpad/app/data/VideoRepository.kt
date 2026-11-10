package com.oldpad.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.oldpad.app.util.episodeComparator
import com.oldpad.app.util.naturalCompare
import java.io.File

/** 通过 MediaStore 读取本地视频，并按文件夹聚合成“剧集”。全程离线。 */
object VideoRepository {

    private var cached: List<VideoItem>? = null

    @Synchronized
    fun videos(context: Context, force: Boolean = false): List<VideoItem> {
        if (!force) cached?.let { return it }
        val list = query(context)
        cached = list
        return list
    }

    @Synchronized
    fun invalidate() {
        cached = null
    }

    private val rootFolderNames = setOf(
        "movies", "movie", "dcim", "download", "downloads", "video", "videos",
        "camera", "pictures", "视频", "电影", "相机", "影片"
    )

    /**
     * 一个子文件夹 = 一部电视剧；直接放在 Movies / DCIM / Download 根目录里的
     * 视频各自单独一格（例如零散的比赛录像）。
     */
    fun buildGroups(items: List<VideoItem>): List<VideoGroup> {
        val map = LinkedHashMap<String, MutableList<VideoItem>>()
        for (v in items) {
            map.getOrPut(v.folderKey) { mutableListOf() }.add(v)
        }
        val groups = ArrayList<VideoGroup>()
        for ((key, list) in map) {
            val sorted = list.sortedWith(compareBy(episodeComparator()) { it.title })
            val inRootFolder = sorted.all { isRootMediaFolder(it) }
            when {
                sorted.size == 1 -> {
                    val v = sorted.first()
                    groups += VideoGroup("__single__${v.id}", v.title, listOf(v))
                }
                inRootFolder -> {
                    sorted.forEach { v ->
                        groups += VideoGroup("__single__${v.id}", v.title, listOf(v))
                    }
                }
                else -> groups += VideoGroup(key, sorted.first().folder, sorted)
            }
        }
        return groups.sortedWith { a, b -> naturalCompare(a.name, b.name) }
    }

    private fun isRootMediaFolder(video: VideoItem): Boolean {
        if (video.folder.trim().lowercase() in rootFolderNames) return true
        val parent = video.path?.let { File(it).parentFile } ?: return false
        val grandParent = parent.parentFile ?: return false
        val gp = grandParent.absolutePath
        return gp == android.os.Environment.getExternalStorageDirectory().absolutePath ||
            Regex("^/storage/[^/]+$").matches(gp)
    }

    fun isSingleKey(key: String): Boolean = key.startsWith("__single__")

    fun groups(context: Context, force: Boolean = false): List<VideoGroup> =
        buildGroups(videos(context, force))

    fun groupFor(context: Context, key: String): VideoGroup? =
        groups(context).firstOrNull { it.key == key }

    fun videoById(context: Context, id: Long): VideoItem? =
        videos(context).firstOrNull { it.id == id }

    fun playlist(context: Context, folderKey: String): List<VideoItem> =
        videos(context)
            .filter { it.folderKey == folderKey }
            .sortedWith(compareBy(episodeComparator()) { it.title })

    fun next(context: Context, current: VideoItem): VideoItem? {
        val list = playlist(context, current.folderKey)
        val index = list.indexOfFirst { it.id == current.id }
        return if (index >= 0) list.getOrNull(index + 1) else null
    }

    private fun query(context: Context): List<VideoItem> {
        val result = ArrayList<VideoItem>()
        val resolver = context.contentResolver
        val base = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED
        )

        var cursor = try {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                base + MediaStore.Video.Media.DATA,
                null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )
        } catch (e: Exception) {
            null
        }
        if (cursor == null) {
            cursor = try {
                resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    base + "bucket_display_name",
                    null, null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )
            } catch (e: Exception) {
                null
            }
        }

        cursor?.use { c ->
            val idIdx = c.getColumnIndex(MediaStore.Video.Media._ID)
            val nameIdx = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            val durIdx = c.getColumnIndex(MediaStore.Video.Media.DURATION)
            val dateIdx = c.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
            val dataIdx = c.getColumnIndex(MediaStore.Video.Media.DATA)
            val bucketIdx = c.getColumnIndex("bucket_display_name")
            if (idIdx < 0 || nameIdx < 0) return result

            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val rawName = c.getString(nameIdx) ?: continue
                if (rawName.startsWith(".")) continue

                val path = if (dataIdx >= 0) c.getString(dataIdx) else null
                if (path != null && path.contains("/.thumbnails/")) continue

                val fromPath = path?.let { File(it).parentFile?.name }?.takeIf { it.isNotBlank() }
                val fromBucket = if (bucketIdx >= 0) c.getString(bucketIdx) else null
                val folder = fromPath ?: fromBucket ?: "其他视频"
                val folderKey = if (path != null && fromPath != null) {
                    File(path).parentFile?.absolutePath ?: "__single__$id"
                } else {
                    "__bucket__$folder"
                }

                val title = rawName.substringBeforeLast('.', rawName)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                result += VideoItem(
                    id = id,
                    uri = uri,
                    title = title,
                    folder = folder,
                    folderKey = folderKey,
                    durationMs = if (durIdx >= 0) c.getLong(durIdx) else 0L,
                    dateAdded = if (dateIdx >= 0) c.getLong(dateIdx) else 0L,
                    path = path
                )
            }
        }
        return result
    }
}
