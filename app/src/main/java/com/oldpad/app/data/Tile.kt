package com.oldpad.app.data

import android.net.Uri

enum class TileType { VIDEO, ALBUM, GAME }

data class Tile(
    val id: String,
    val type: TileType,
    val title: String,
    val subtitle: String,
    val cover: Uri?,
    val emoji: String?,
    val payload: String,
    val count: Int = 0,
    val isRecent: Boolean = false
)
