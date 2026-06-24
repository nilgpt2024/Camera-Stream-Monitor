package com.andwin.video.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.andwin.video.MainActivity
import com.andwin.video.R
import com.andwin.video.VideoMonitorApp
import com.andwin.video.utils.LocaleHelper

class StreamService : Service() {

    private var isStreaming = false
    private var streamUrl: String = ""
    private var wakeLock: PowerManager.WakeLock? = null

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(LocaleHelper.setLocale(base!!, LocaleHelper.getLocale(base)))
    }

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
        // 获取 PARTIAL_WAKE_LOCK，确保熄屏后 CPU 仍运行，推流不中断
        acquireWakeLock()
        val notification = createNotification(getString(R.string.streaming_to_url, url))
        startForeground(VideoMonitorApp.NOTIFICATION_ID_STREAMING, notification)

        sendBroadcast(Intent(ACTION_STREAMING_STATE_CHANGED).putExtra("is_streaming", true))
    }

    private fun stopStreaming() {
        if (!isStreaming) return

        isStreaming = false
        releaseWakeLock()
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
            .setContentTitle(getString(R.string.streaming_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AndWin:StreamService"
        ).apply {
            acquire(10 * 60 * 1000L) // 最长持有10分钟，超时自动释放防止泄漏
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_STREAMING = "com.andwin.video.action.START_STREAMING"
        const val ACTION_STOP_STREAMING = "com.andwin.video.action.STOP_STREAMING"
        const val ACTION_STREAMING_STATE_CHANGED = "com.andwin.video.action.STREAMING_STATE_CHANGED"
    }
}
