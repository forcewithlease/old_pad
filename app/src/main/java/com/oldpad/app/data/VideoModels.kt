package com.oldpad.app.data

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val folder: String,
    val folderKey: String,
    val durationMs: Long,
    val dateAdded: Long,
    val path: String?,
    val width: Int = 0,
    val height: Int = 0
)

data class VideoGroup(
    val key: String,
    val name: String,
    val videos: List<VideoItem>
) {
    val isSeries: Boolean get() = videos.size > 1
    val size: Int get() = videos.size
    val coverUri: Uri get() = videos.first().uri
}
