package com.oldpad.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.oldpad.app.util.naturalCompare
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.abs

/**
 * App 内部媒体库：通过“电脑快传”上传的视频/照片。
 * 文件放在应用专属目录，不经过 MediaStore，不会出现在系统相册/图库里。
 * 分组信息保存在本地 JSON 里，网页管理端可以新建、删除分组。
 */
object InternalLibrary {

    const val TYPE_VIDEO = "video"
    const val TYPE_PHOTO = "photo"

    private const val PREFS = "oldpad_library"
    private const val KEY_GROUPS = "groups"

    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "ts", "m4v", "flv", "wmv", "3gp", "mpg", "mpeg"
    )
    private val photoExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif"
    )

    data class Group(
        val id: String,
        val name: String,
        val type: String,
        val createdAt: Long
    )

    private data class VideoMeta(val duration: Long, val width: Int, val height: Int)

    private val metaCache = HashMap<String, VideoMeta>()

    fun root(context: Context): File {
        val base = context.getExternalFilesDir("library")
            ?: File(context.filesDir, "library")
        if (!base.exists()) base.mkdirs()
        return base
    }

    fun groupDir(context: Context, group: Group): File {
        val sub = if (group.type == TYPE_VIDEO) "videos" else "photos"
        val dir = File(root(context), "$sub/${group.id}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Synchronized
    fun groups(context: Context, type: String): List<Group> =
        loadGroups(context).filter { it.type == type }.sortedBy { it.createdAt }

    @Synchronized
    fun group(context: Context, id: String): Group? =
        loadGroups(context).firstOrNull { it.id == id }

    @Synchronized
    fun createGroup(context: Context, type: String, rawName: String): Group? {
        if (type != TYPE_VIDEO && type != TYPE_PHOTO) return null
        val name = rawName.trim().take(40)
        if (name.isBlank()) return null

        val all = loadGroups(context)
        if (all.any { it.type == type && it.name.equals(name, ignoreCase = true) }) return null

        val group = Group(UUID.randomUUID().toString(), name, type, System.currentTimeMillis())
        all.add(group)
        saveGroups(context, all)
        groupDir(context, group)
        return group
    }

    @Synchronized
    fun renameGroup(context: Context, id: String, rawName: String): Boolean {
        val name = rawName.trim().take(40)
        if (name.isBlank()) return false
        val all = loadGroups(context)
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return false
        val group = all[index]
        if (all.any {
                it.type == group.type && it.id != id && it.name.equals(name, ignoreCase = true)
            }
        ) {
            return false
        }
        all[index] = group.copy(name = name)
        saveGroups(context, all)
        return true
    }

    @Synchronized
    fun deleteGroup(context: Context, id: String): Boolean {
        val all = loadGroups(context)
        val group = all.firstOrNull { it.id == id } ?: return false
        try {
            groupDir(context, group).deleteRecursively()
        } catch (ignored: Exception) {
        }
        all.removeAll { it.id == id }
        saveGroups(context, all)
        return true
    }

    fun files(context: Context, group: Group): List<File> {
        val dir = groupDir(context, group)
        val allowed = if (group.type == TYPE_VIDEO) videoExtensions else photoExtensions
        return try {
            dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in allowed }
                ?.sortedWith { a, b -> naturalCompare(a.name, b.name) }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun videoGroups(context: Context): List<VideoGroup> =
        groups(context, TYPE_VIDEO).mapNotNull { group ->
            val videos = videoItems(context, group)
            if (videos.isEmpty()) {
                null
            } else {
                VideoGroup("internal:${group.id}", group.name, videos)
            }
        }

    fun videoItems(context: Context, group: Group): List<VideoItem> =
        files(context, group).map { file ->
            val meta = meta(context, file)
            VideoItem(
                id = idFor(file),
                uri = Uri.fromFile(file),
                title = file.nameWithoutExtension,
                folder = group.name,
                folderKey = "internal:${group.id}",
                durationMs = meta.duration,
                dateAdded = file.lastModified() / 1000,
                path = file.absolutePath,
                width = meta.width,
                height = meta.height
            )
        }

    fun photoAlbums(context: Context): List<Album> =
        groups(context, TYPE_PHOTO).mapNotNull { group ->
            val photos = photoItems(context, group)
            if (photos.isEmpty()) null
            else Album("internal:${group.id}", group.name, photos)
        }

    fun photoItems(context: Context, group: Group): List<PhotoItem> =
        files(context, group).map { file ->
            PhotoItem(
                id = idFor(file),
                uri = Uri.fromFile(file),
                name = file.nameWithoutExtension,
                folder = group.name,
                folderKey = "internal:${group.id}",
                dateAdded = file.lastModified() / 1000
            )
        }

    fun allVideos(context: Context): List<VideoItem> =
        groups(context, TYPE_VIDEO).flatMap { videoItems(context, it) }

    fun findVideo(context: Context, id: Long): VideoItem? =
        allVideos(context).firstOrNull { it.id == id }

    fun findGroupByFolderKey(context: Context, folderKey: String): Group? {
        if (!folderKey.startsWith("internal:")) return null
        return group(context, folderKey.removePrefix("internal:"))
    }

    /** 生成安全的文件名，防止 ../ 路径穿越。 */
    fun safeFileName(raw: String): String {
        var name = File(raw.replace('\\', '/')).name
        name = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim()
        if (name.isBlank() || name == "." || name == "..") name = "file"
        return name.take(120)
    }

    fun uniqueTarget(dir: File, fileName: String): File {
        val safe = safeFileName(fileName)
        val base = safe.substringBeforeLast('.', safe)
        val ext = safe.substringAfterLast('.', "")
        var candidate = File(dir, safe)
        var index = 1
        while (candidate.exists()) {
            val suffix = " ($index)"
            val name = if (ext.isBlank()) base + suffix else "$base$suffix.$ext"
            candidate = File(dir, name)
            index++
        }
        return candidate
    }

    private fun idFor(file: File): Long {
        val hash = abs(file.absolutePath.hashCode().toLong())
        return -(hash % 900_000_000L) - 1
    }

    private fun meta(context: Context, file: File): VideoMeta {
        val key = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        metaCache[key]?.let { return it }

        var duration = 0L
        var width = 0
        var height = 0
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val tmp = width
                width = height
                height = tmp
            }
        } catch (e: Exception) {
            // 忽略，按未知处理
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }

        val meta = VideoMeta(duration, width, height)
        if (metaCache.size > 240) metaCache.clear()
        metaCache[key] = meta
        return meta
    }

    private fun loadGroups(context: Context): MutableList<Group> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_GROUPS, "[]") ?: "[]"
        val result = ArrayList<Group>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val name = obj.optString("name")
                val type = obj.optString("type")
                if (id.isBlank() || name.isBlank()) continue
                if (type != TYPE_VIDEO && type != TYPE_PHOTO) continue
                result += Group(id, name, type, obj.optLong("createdAt", 0L))
            }
        } catch (ignored: Exception) {
        }
        return result
    }

    private fun saveGroups(context: Context, groups: List<Group>) {
        val array = JSONArray()
        for (g in groups) {
            array.put(
                JSONObject()
                    .put("id", g.id)
                    .put("name", g.name)
                    .put("type", g.type)
                    .put("createdAt", g.createdAt)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_GROUPS, array.toString())
            .apply()
    }
}
