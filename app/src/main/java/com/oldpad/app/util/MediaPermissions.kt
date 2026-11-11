package com.oldpad.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object MediaPermissions {

    fun required(): Array<String> {
        if (Build.VERSION.SDK_INT >= 34) {
            return arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasVideo(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            granted(context, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasImages(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            if (granted(context, Manifest.permission.READ_MEDIA_IMAGES)) return true
            // Android 14：用户可能只允许“选中的照片”
            return Build.VERSION.SDK_INT >= 34 &&
                granted(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        }
        return granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Android 11+ 读取视频旁边的 .srt 字幕需要“所有文件访问”（可选）。 */
    fun hasAllFilesAccess(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return true
        return try {
            android.os.Environment.isExternalStorageManager()
        } catch (e: Exception) {
            false
        }
    }
}
