package com.oldpad.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 视频缩略图，内存缓存 + 后台解码，滚动不卡。 */
object ThumbnailLoader {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    fun load(imageView: ImageView, uri: Uri, widthPx: Int = 640) {
        val key = "$uri@$widthPx"
        imageView.tag = key
        imageView.setImageDrawable(null)

        val cached = cache.get(key)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }

        val appContext = imageView.context.applicationContext
        scope.launch {
            val bitmap = try {
                decode(appContext, uri, widthPx)
            } catch (e: Exception) {
                null
            }
            if (bitmap != null) cache.put(key, bitmap)
            main.post {
                if (imageView.tag == key && bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun decode(context: Context, uri: Uri, widthPx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            return context.contentResolver.loadThumbnail(
                uri,
                Size(widthPx, widthPx * 9 / 16),
                null
            )
        }
        val retriever = MediaMetadataRetriever()
        val fromVideo = try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(2_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }
        if (fromVideo != null) return fromVideo

        // 照片（API 29 以下没有 loadThumbnail）
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }
}
