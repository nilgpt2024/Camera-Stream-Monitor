package com.andwin.video

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class VideoMonitorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val recordChannel = NotificationChannel(
                CHANNEL_ID_RECORDING,
                "视频录制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "视频录制服务通知"
                setShowBadge(false)
            }

            val streamChannel = NotificationChannel(
                CHANNEL_ID_STREAMING,
                "视频推流",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "视频推流服务通知"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(listOf(recordChannel, streamChannel))
        }
    }

    companion object {
        lateinit var instance: VideoMonitorApp
            private set

        const val CHANNEL_ID_RECORDING = "video_recording"
        const val CHANNEL_ID_STREAMING = "video_streaming"
        const val NOTIFICATION_ID_RECORDING = 1001
        const val NOTIFICATION_ID_STREAMING = 1002
    }
}
