package com.oldpad.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import com.oldpad.app.data.PhotoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 本地照片解码：按屏幕大小采样，处理 EXIF 方向，内存缓存。 */
object PhotoLoader {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 6).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxMemory) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    fun load(imageView: ImageView, photo: PhotoItem, maxSizePx: Int = 512) {
        val key = "${photo.id}@$maxSizePx"
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
                decode(appContext, photo.uri, maxSizePx)
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

    private fun decode(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize ||
            bounds.outHeight / (sample * 2) >= maxSize
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        var bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        if (!matrix.isIdentity) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }
}
