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

class RecordService : Service() {

    private var isRecording = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (isRecording) return

        isRecording = true
        val notification = createNotification("正在录制...")
        startForeground(VideoMonitorApp.NOTIFICATION_ID_RECORDING, notification)

        sendBroadcast(Intent(ACTION_RECORDING_STATE_CHANGED).putExtra("is_recording", true))
    }

    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        sendBroadcast(Intent(ACTION_RECORDING_STATE_CHANGED).putExtra("is_recording", false))

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

        return NotificationCompat.Builder(this, VideoMonitorApp.CHANNEL_ID_RECORDING)
            .setContentTitle("视频监控")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_RECORDING = "com.andwin.video.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.andwin.video.action.STOP_RECORDING"
        const val ACTION_RECORDING_STATE_CHANGED = "com.andwin.video.action.RECORDING_STATE_CHANGED"
    }
}
