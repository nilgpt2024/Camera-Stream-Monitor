package com.andwin.video.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.andwin.video.MainActivity
import com.andwin.video.R
import com.andwin.video.VideoMonitorApp

class StreamService : Service() {

    private var isStreaming = false
    private var streamUrl: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_STREAMING -> {
                streamUrl = intent.getStringExtra("stream_url") ?: ""
                startStreaming(streamUrl)
            }
            ACTION_STOP_STREAMING -> stopStreaming()
        }
        return START_STICKY
    }

    private fun startStreaming(url: String) {
        if (isStreaming) return

        isStreaming = true
        val notification = createNotification("正在推流至 $url")
        startForeground(VideoMonitorApp.NOTIFICATION_ID_STREAMING, notification)

        sendBroadcast(Intent(ACTION_STREAMING_STATE_CHANGED).putExtra("is_streaming", true))
    }

    private fun stopStreaming() {
        if (!isStreaming) return

        isStreaming = false
        sendBroadcast(Intent(ACTION_STREAMING_STATE_CHANGED).putExtra("is_streaming", false))

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VideoMonitorApp.CHANNEL_ID_STREAMING)
            .setContentTitle("视频推流")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_STREAMING = "com.andwin.video.action.START_STREAMING"
        const val ACTION_STOP_STREAMING = "com.andwin.video.action.STOP_STREAMING"
        const val ACTION_STREAMING_STATE_CHANGED = "com.andwin.video.action.STREAMING_STATE_CHANGED"
    }
}
