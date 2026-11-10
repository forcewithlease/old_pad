package com.oldpad.app.data

import android.content.Context

/** 首页格子的显示 / 隐藏 / 自定义排序，保存在本地。 */
object HomeConfig {
    private const val PREFS = "oldpad_home"
    private const val KEY_ORDER = "order"
    private const val KEY_HIDDEN = "hidden"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun order(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_ORDER, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun saveOrder(context: Context, ids: List<String>) {
        prefs(context).edit().putString(KEY_ORDER, ids.joinToString("\n")).apply()
    }

    fun hidden(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_HIDDEN, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }.toSet()
    }

    fun setHidden(context: Context, hidden: Set<String>) {
        prefs(context).edit().putString(KEY_HIDDEN, hidden.joinToString("\n")).apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** 按用户排序过滤显示；没有排过序就保持自动顺序。 */
    fun ordered(context: Context, tiles: List<Tile>): List<Tile> {
        val hidden = hidden(context)
        val visible = tiles.filter { it.id !in hidden }
        val order = order(context)
        if (order.isEmpty()) return visible

        val remaining = LinkedHashMap<String, Tile>()
        visible.forEach { remaining[it.id] = it }

        val result = ArrayList<Tile>(visible.size)
        for (id in order) {
            remaining.remove(id)?.let { result.add(it) }
        }
        result.addAll(remaining.values)
        return result
    }
}
