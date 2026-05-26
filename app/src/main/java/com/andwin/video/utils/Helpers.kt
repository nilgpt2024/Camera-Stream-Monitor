package com.andwin.video.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.File

object PermissionHelper {

    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getMissingPermissions(context: Context): Array<String> {
        return REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }
}

object StorageHelper {

    private const val APP_DIR_NAME = "VideoMonitor"
    private const val RECORDINGS_DIR = "Recordings"
    private const val PHOTOS_DIR = "Photos"

    fun getAppDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), APP_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getRecordingsDirectory(context: Context): File {
        val dir = File(getAppDirectory(context), RECORDINGS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPhotosDirectory(context: Context): File {
        val dir = File(getAppDirectory(context), PHOTOS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun generateVideoFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "video_${timestamp}.mp4"
    }

    fun generatePhotoFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "photo_${timestamp}.jpg"
    }
}
