package com.oldpad.app.util

/**
 * 剧集排序：优先取标题里的数字（阿拉伯数字或中文数字），
 * 没有数字时用自然排序，保证“第2集”排在“第10集”前面。
 */
fun episodeNumber(title: String): Long? {
    Regex("\\d+").find(title)?.let { return it.value.toLongOrNull() }
    Regex("[零〇一二两三四五六七八九十百千]+").find(title)?.let { return chineseNumber(it.value) }
    return null
}

private fun chineseNumber(text: String): Long? {
    val digits = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
        '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )
    var section = 0L
    var number = 0L
    var seen = false
    for (c in text) {
        when {
            digits.containsKey(c) -> {
                number = digits.getValue(c).toLong()
                seen = true
            }
            c == '十' -> {
                if (number == 0L) number = 1
                section += number * 10
                number = 0
                seen = true
            }
            c == '百' -> {
                if (number == 0L) number = 1
                section += number * 100
                number = 0
                seen = true
            }
            c == '千' -> {
                if (number == 0L) number = 1
                section += number * 1000
                number = 0
                seen = true
            }
        }
    }
    return if (seen) section + number else null
}

fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var si = i
            while (si < a.length && a[si].isDigit()) si++
            var sj = j
            while (sj < b.length && b[sj].isDigit()) sj++
            val na = a.substring(i, si).trimStart('0').ifEmpty { "0" }
            val nb = b.substring(j, sj).trimStart('0').ifEmpty { "0" }
            if (na.length != nb.length) return na.length - nb.length
            val cmp = na.compareTo(nb)
            if (cmp != 0) return cmp
            i = si
            j = sj
        } else {
            val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (cmp != 0) return cmp
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

fun episodeComparator(): Comparator<String> = Comparator { a, b ->
    val na = episodeNumber(a)
    val nb = episodeNumber(b)
    when {
        na != null && nb != null && na != nb -> na.compareTo(nb)
        else -> naturalCompare(a, b)
    }
}
