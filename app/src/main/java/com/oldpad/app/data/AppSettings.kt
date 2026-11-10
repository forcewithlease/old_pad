package com.oldpad.app.data

import android.content.Context

object AppSettings {
    private const val PREFS = "oldpad_settings"

    const val KEY_AUTO_START = "auto_start"
    const val KEY_LAUNCHER = "launcher_mode"
    const val KEY_KEEP_AWAKE = "keep_awake"
    const val KEY_SUBTITLE_SCALE = "subtitle_scale"
    const val KEY_SUBTITLE_ON = "subtitle_on"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoStart(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_START, true)

    fun launcherMode(context: Context): Boolean = prefs(context).getBoolean(KEY_LAUNCHER, false)

    fun keepAwake(context: Context): Boolean = prefs(context).getBoolean(KEY_KEEP_AWAKE, true)

    fun subtitleScale(context: Context): Float = prefs(context).getFloat(KEY_SUBTITLE_SCALE, 0.08f)

    fun subtitlesOn(context: Context): Boolean = prefs(context).getBoolean(KEY_SUBTITLE_ON, true)
}
