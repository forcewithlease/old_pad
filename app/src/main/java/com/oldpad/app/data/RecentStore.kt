package com.oldpad.app.data

import android.content.Context

/** 首页最前面 3 格“最近用过”的内容。 */
object RecentStore {
    private const val PREFS = "oldpad_recent"
    private const val KEY_IDS = "ids"
    private const val KEY_ENABLED = "enabled"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun ids(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_IDS, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun touch(context: Context, id: String) {
        if (id.isBlank()) return
        val list = ids(context).toMutableList()
        list.remove(id)
        list.add(0, id)
        while (list.size > 3) list.removeAt(list.size - 1)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_IDS, list.joinToString("\n")).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_IDS).apply()
    }
}
