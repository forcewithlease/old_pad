package com.oldpad.app.util

import java.util.Locale

/** 播放器里的时间：01:23 / 1:02:03 */
fun formatClock(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

/** 卡片上的时长：45分钟 / 1小时20分 */
fun formatHuman(ms: Long): String {
    if (ms <= 0) return ""
    val minutes = ms / 60_000
    if (minutes < 1) return "不到1分钟"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}小时${m}分"
        h > 0 -> "${h}小时"
        else -> "${m}分钟"
    }
}
