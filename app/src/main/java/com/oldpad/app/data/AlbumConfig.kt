package com.oldpad.app.data

import android.content.Context

/**
 * 相册默认全部关闭，必须由家人手动勾选后才会出现在首页，
 * 避免把截图、相机、下载图片等私人内容直接展示出来。
 */
object AlbumConfig {
    private const val PREFS = "oldpad_albums"
    private const val KEY_FOLDERS = "folders"
    private const val KEY_ALL = "all_photos"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabledFolders(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_FOLDERS, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }.toSet()
    }

    fun setEnabledFolders(context: Context, keys: Set<String>) {
        prefs(context).edit()
            .putString(KEY_FOLDERS, keys.joinToString("\n"))
            .apply()
    }

    fun allPhotosEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALL, false)

    fun setAllPhotosEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALL, enabled).apply()
    }
}
