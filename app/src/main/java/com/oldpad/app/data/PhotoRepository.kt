package com.oldpad.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.oldpad.app.util.naturalCompare
import java.io.File

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val folder: String,
    val folderKey: String,
    val dateAdded: Long
)

data class Album(
    val key: String,
    val name: String,
    val photos: List<PhotoItem>
) {
    val cover: Uri get() = photos.first().uri
    val count: Int get() = photos.size
}

/** 通过 MediaStore 读取本地照片，并按文件夹聚合成相册。 */
object PhotoRepository {

    const val ALL_KEY = "__all__"

    private var cachedPhotos: List<PhotoItem>? = null

    @Synchronized
    fun photos(context: Context, force: Boolean = false): List<PhotoItem> {
        if (!force) cachedPhotos?.let { return it }
        val list = query(context)
        cachedPhotos = list
        return list
    }

    @Synchronized
    fun invalidate() {
        cachedPhotos = null
    }

    fun albums(context: Context, force: Boolean = false): List<Album> {
        val result = ArrayList<Album>()
        val all = allAlbums(context, force)

        if (all.isNotEmpty()) {
            val enabled = AlbumConfig.enabledFolders(context)
            val selected = all.filter { it.key in enabled }
            // “全部照片”只汇总已勾选的相册，绝不把截图/相机混进来
            if (selected.isNotEmpty() &&
                AlbumConfig.allPhotosEnabled(context) && selected.size > 1
            ) {
                result += Album(
                    ALL_KEY,
                    "全部照片",
                    selected.flatMap { it.photos }.sortedBy { it.dateAdded }
                )
            }
            result += selected
        }

        // 通过“电脑快传”明确上传的分组，默认直接显示
        result += InternalLibrary.photoAlbums(context)
        return result
    }

    /** 所有检测到的照片文件夹，仅供“管理相册”选择，不直接显示。 */
    fun allAlbums(context: Context, force: Boolean = false): List<Album> {
        val photos = photos(context, force)
        if (photos.isEmpty()) return emptyList()

        val map = LinkedHashMap<String, MutableList<PhotoItem>>()
        for (p in photos) {
            map.getOrPut(p.folderKey) { mutableListOf() }.add(p)
        }
        return map.map { (key, list) ->
            Album(key, list.first().folder, list.sortedBy { it.dateAdded })
        }.sortedWith { a, b -> naturalCompare(a.name, b.name) }
    }

    fun albumFor(context: Context, key: String): Album? {
        if (key.startsWith("internal:")) {
            val group = InternalLibrary.findGroupByFolderKey(context, key) ?: return null
            val photos = InternalLibrary.photoItems(context, group)
            if (photos.isEmpty()) return null
            return Album(key, group.name, photos)
        }
        if (key == ALL_KEY) {
            val selected = albums(context).filter { it.key != ALL_KEY }
            if (selected.isEmpty()) return null
            return Album(
                ALL_KEY,
                "全部照片",
                selected.flatMap { it.photos }.sortedBy { it.dateAdded }
            )
        }
        return allAlbums(context).firstOrNull { it.key == key }
    }

    private fun query(context: Context): List<PhotoItem> {
        val result = ArrayList<PhotoItem>()
        val resolver = context.contentResolver
        val base = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        var cursor = try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                base + MediaStore.Images.Media.DATA,
                null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
        } catch (e: Exception) {
            null
        }
        if (cursor == null) {
            cursor = try {
                resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    base + "bucket_display_name",
                    null, null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )
            } catch (e: Exception) {
                null
            }
        }

        cursor?.use { c ->
            val idIdx = c.getColumnIndex(MediaStore.Images.Media._ID)
            val nameIdx = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val dateIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
            val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
            val bucketIdx = c.getColumnIndex("bucket_display_name")
            if (idIdx < 0) return result

            val storageRoot = Environment.getExternalStorageDirectory().absolutePath

            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val rawName = if (nameIdx >= 0) c.getString(nameIdx) else null
                if (rawName != null && rawName.startsWith(".")) continue

                val path = if (dataIdx >= 0) c.getString(dataIdx) else null
                val parent = path?.let { File(it).parentFile }
                val folderName = parent?.name?.takeIf { it.isNotBlank() }
                    ?: (if (bucketIdx >= 0) c.getString(bucketIdx) else null)
                    ?: "其他照片"

                val inRoot = parent?.absolutePath == storageRoot ||
                    parent?.absolutePath == "$storageRoot/Pictures" ||
                    parent?.absolutePath == "$storageRoot/DCIM"
                val folderKey = if (parent != null && !inRoot) {
                    parent.absolutePath
                } else {
                    "__bucket__$folderName"
                }

                result += PhotoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    name = rawName?.substringBeforeLast('.', rawName) ?: "照片",
                    folder = folderName,
                    folderKey = folderKey,
                    dateAdded = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                )
            }
        }
        return result
    }
}
