package com.oldpad.app.data

import android.content.Context

object AppSettings {
    private const val PREFS = "oldpad_settings"

    const val KEY_AUTO_START = "auto_start"
    const val KEY_LAUNCHER = "launcher_mode"
    const val KEY_KEEP_AWAKE = "keep_awake"
    const val KEY_SUBTITLE_SCALE = "subtitle_scale"
    const val KEY_SUBTITLE_ON = "subtitle_on"
    const val KEY_VIDEO_ALL_FOLDERS = "video_all_folders"
    const val KEY_AUTO_LANDSCAPE = "auto_landscape"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoStart(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_START, true)

    fun launcherMode(context: Context): Boolean = prefs(context).getBoolean(KEY_LAUNCHER, false)

    fun keepAwake(context: Context): Boolean = prefs(context).getBoolean(KEY_KEEP_AWAKE, true)

    fun subtitleScale(context: Context): Float = prefs(context).getFloat(KEY_SUBTITLE_SCALE, 0.11f)

    fun subtitlesOn(context: Context): Boolean = prefs(context).getBoolean(KEY_SUBTITLE_ON, true)

    /** 默认只扫描 Movies，避免把相机、录屏、下载里的私人视频摊到首页。 */
    fun videoAllFolders(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIDEO_ALL_FOLDERS, false)

    fun autoLandscape(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_LANDSCAPE, true)
}
