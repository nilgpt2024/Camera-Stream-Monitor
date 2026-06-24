package com.andwin.video

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.andwin.video.utils.LocaleHelper

class VideoMonitorApp : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(LocaleHelper.setLocale(base!!, LocaleHelper.getLocale(base)))
    }

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
                getString(R.string.notification_channel_recording),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_recording_desc)
                setShowBadge(false)
            }

            val streamChannel = NotificationChannel(
                CHANNEL_ID_STREAMING,
                getString(R.string.notification_channel_streaming),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_streaming_desc)
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
