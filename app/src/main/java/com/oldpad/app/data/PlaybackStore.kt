package com.oldpad.app.data

import android.content.Context

/** 每个视频的播放进度和全局倍速，全部保存在本地。 */
object PlaybackStore {
    private const val PREFS = "oldpad_playback"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun position(context: Context, videoId: Long): Long =
        prefs(context).getLong("pos_$videoId", 0L)

    fun savePosition(context: Context, videoId: Long, position: Long) {
        prefs(context).edit().putLong("pos_$videoId", position).apply()
    }

    fun speed(context: Context): Float = prefs(context).getFloat("speed", 1f)

    fun saveSpeed(context: Context, speed: Float) {
        prefs(context).edit().putFloat("speed", speed).apply()
    }

    /** 0..100，用于剧集列表上的“已看”标记 */
    fun percent(context: Context, videoId: Long, durationMs: Long): Int {
        if (durationMs <= 0) return 0
        val pos = position(context, videoId)
        if (pos <= 0) return 0
        if (durationMs - pos < 15_000) return 100
        return ((pos * 100) / durationMs).toInt().coerceIn(0, 100)
    }
}
