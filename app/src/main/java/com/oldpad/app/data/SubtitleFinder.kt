package com.oldpad.app.data

import android.media.MediaMetadataRetriever
import androidx.media3.common.MimeTypes
import java.io.File

data class SubtitleFile(
    val file: File,
    val label: String,
    val language: String?,
    val mimeType: String
)

/**
 * 找视频旁边的外挂字幕：同目录、同文件名、支持 .srt/.ass/.ssa/.vtt，
 * 也支持“名字.zh.srt / 名字.chs.ass”这类语言后缀。
 */
object SubtitleFinder {

    private val extensions = mapOf(
        "srt" to MimeTypes.APPLICATION_SUBRIP,
        "vtt" to MimeTypes.TEXT_VTT,
        "ass" to MimeTypes.TEXT_SSA,
        "ssa" to MimeTypes.TEXT_SSA
    )

    fun find(video: VideoItem): List<SubtitleFile> {
        val path = video.path ?: return emptyList()
        val file = File(path)
        val dir = file.parentFile ?: return emptyList()
        val stem = file.nameWithoutExtension
        if (stem.isBlank()) return emptyList()

        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        return files.asSequence()
            .filter { it.isFile && it.canRead() }
            .filter { f ->
                val ext = f.extension.lowercase()
                val base = f.nameWithoutExtension
                extensions.containsKey(ext) &&
                    (base.equals(stem, ignoreCase = true) ||
                        base.startsWith(stem + ".", ignoreCase = true) ||
                        base.startsWith(stem + "_", ignoreCase = true) ||
                        base.startsWith(stem + "-", ignoreCase = true))
            }
            .map { f ->
                val ext = f.extension.lowercase()
                val suffix = f.nameWithoutExtension.removePrefix(stem).lowercase()
                val (language, label) = detect(suffix)
                SubtitleFile(
                    file = f,
                    label = label,
                    language = language,
                    mimeType = extensions.getValue(ext)
                )
            }
            .sortedWith(compareBy({ languageRank(it.language) }, { it.file.name }))
            .toList()
    }

    private fun detect(suffix: String): Pair<String?, String> = when {
        suffix.contains("chs") || suffix.contains("zh-cn") || suffix.contains("zh_hans") ||
            suffix.contains("zh-hans") || suffix.contains("sc") -> "zh" to "简体中文"
        suffix.contains("cht") || suffix.contains("zh-tw") || suffix.contains("zh_hant") ||
            suffix.contains("zh-hant") || suffix.contains("tc") -> "zh" to "繁体中文"
        suffix.contains("zh") || suffix.contains("cn") || suffix.contains("chinese") ->
            "zh" to "中文字幕"
        suffix.contains("en") -> "en" to "English"
        suffix.contains("ja") || suffix.contains("jp") -> "ja" to "日本語"
        else -> null to "字幕"
    }

    private fun languageRank(language: String?): Int = when (language) {
        "zh" -> 0
        null -> 1
        "en" -> 2
        else -> 3
    }

    /** 兼容部分老设备：从视频里读时长（MediaStore 有时给 0）。 */
    fun readDurationMs(video: VideoItem, context: android.content.Context): Long {
        if (video.durationMs > 0) return video.durationMs
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, video.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }
    }
}
