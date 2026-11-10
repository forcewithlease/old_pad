package com.oldpad.app.data

import android.content.Context
import com.oldpad.app.R
import com.oldpad.app.game.GameRegistry
import com.oldpad.app.util.formatHuman

/** 把视频、相册、游戏统一变成首页上的“大格子”。 */
object TileRepository {

    fun all(context: Context): List<Tile> {
        val result = ArrayList<Tile>()

        VideoRepository.groups(context).forEach { g ->
            val subtitle = if (g.isSeries) {
                context.getString(R.string.series_count, g.size)
            } else {
                formatHuman(g.videos.first().durationMs)
            }
            result += Tile(
                id = "vg:" + g.key,
                type = TileType.VIDEO,
                title = g.name,
                subtitle = subtitle,
                cover = g.coverUri,
                emoji = null,
                payload = g.key,
                count = g.size
            )
        }

        PhotoRepository.albums(context).forEach { a ->
            result += Tile(
                id = "al:" + a.key,
                type = TileType.ALBUM,
                title = a.name,
                subtitle = context.getString(R.string.photo_count, a.count),
                cover = a.cover,
                emoji = null,
                payload = a.key,
                count = a.count
            )
        }

        GameRegistry.providers(context).forEach { p ->
            result += Tile(
                id = "g:" + p.id,
                type = TileType.GAME,
                title = p.title,
                subtitle = p.subtitle,
                cover = null,
                emoji = p.emoji,
                payload = p.id
            )
        }

        return result
    }

    /** 用户在设置里排过序就按设置，没排过就自动排序。 */
    fun visible(context: Context): List<Tile> = HomeConfig.ordered(context, all(context))

    /** 首页 = 最近用过的 3 个 + 其余按顺序。 */
    fun withRecent(context: Context, all: List<Tile>): List<Tile> {
        val hidden = HomeConfig.hidden(context)
        val recent = if (RecentStore.enabled(context)) {
            RecentStore.ids(context)
                .mapNotNull { id -> all.firstOrNull { it.id == id } }
                .filter { it.id !in hidden }
                .distinctBy { it.id }
                .take(3)
                .map { it.copy(isRecent = true) }
        } else {
            emptyList()
        }

        val recentIds = recent.map { it.id }.toSet()
        val rest = HomeConfig.ordered(context, all).filter { it.id !in recentIds }
        return recent + rest
    }
}
