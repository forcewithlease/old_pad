package com.oldpad.app.data

import com.oldpad.app.util.episodeNumber

/** 本地模糊搜索：不联网，不依赖拼音库，按“字都出现且顺序一致”匹配。 */
object FuzzySearch {

    data class Hit(val item: VideoItem, val score: Int)

    private fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString()
    }

    fun search(items: List<VideoItem>, query: String): List<Hit> {
        val q = normalize(query)
        if (q.isEmpty()) {
            return items.sortedWith(
                compareBy({ it.folder }, { episodeNumber(it.title) ?: Long.MAX_VALUE }, { it.title })
            ).map { Hit(it, 0) }
        }

        val hits = ArrayList<Hit>()
        for (item in items) {
            val title = normalize(item.title)
            val folder = normalize(item.folder)
            val score = score(q, title, folder) ?: continue
            hits += Hit(item, score)
        }
        return hits.sortedWith(
            compareByDescending<Hit> { it.score }
                .thenBy { it.item.folder }
                .thenBy { episodeNumber(it.item.title) ?: Long.MAX_VALUE }
                .thenBy { it.item.title }
        )
    }

    private fun score(q: String, title: String, folder: String): Int? {
        substringScore(q, title)?.let { return it + 300 }
        substringScore(q, folder)?.let { return it + 150 }

        val combined = title + folder
        return subsequenceScore(q, combined)
    }

    private fun substringScore(q: String, text: String): Int? {
        val index = text.indexOf(q)
        if (index < 0) return null
        return 1000 - index * 2 - (text.length - q.length).coerceAtLeast(0)
    }

    private fun subsequenceScore(q: String, text: String): Int? {
        var ti = 0
        var start = -1
        var end = -1
        var gap = 0
        var last = -1

        for (qc in q) {
            var found = -1
            while (ti < text.length) {
                if (text[ti] == qc) {
                    found = ti
                    ti++
                    break
                }
                ti++
            }
            if (found < 0) return null
            if (start < 0) start = found
            if (last >= 0) gap += found - last - 1
            last = found
            end = found
        }

        val span = end - start + 1
        return 500 - gap * 4 - span - start
    }
}
