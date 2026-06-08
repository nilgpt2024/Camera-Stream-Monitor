package com.andwin.video.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.andwin.video.MainActivity
import com.andwin.video.R
import com.andwin.video.VideoMonitorApp

class RecordService : Service() {

    private var isRecording = false
    private var wakeLock: PowerManager.WakeLock? = null

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
        // 获取 WakeLock，录制期间无限持有，防止 CPU 休眠导致录制中断
        acquireWakeLock()
        val notification = createNotification(getRecordingStatusText())
        startForeground(VideoMonitorApp.NOTIFICATION_ID_RECORDING, notification)

        sendBroadcast(Intent(ACTION_RECORDING_STATE_CHANGED).putExtra("is_recording", true))
    }

    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        releaseWakeLock()
        sendBroadcast(Intent(ACTION_RECORDING_STATE_CHANGED).putExtra("is_recording", false))

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * 更新通知文本（用于显示录制时长等动态信息）
     */
    fun updateNotification(text: String) {
        if (!isRecording) return
        val notification = createNotification(text)
        // 使用相同的 notificationId 更新通知
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(VideoMonitorApp.NOTIFICATION_ID_RECORDING, notification)
        } catch (e: Exception) {
            // 忽略异常，服务可能已停止
        }
    }

    private fun getRecordingStatusText(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val bgEnabled = prefs.getBoolean("background_recording", true)
        return if (bgEnabled) getString(R.string.recording_in_background) else "正在录制..."
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

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val preventSleep = prefs.getBoolean("prevent_sleep_during_recording", true)

        wakeLock = if (preventSleep) {
            // PARTIAL_WAKE_LOCK + 阻止设备休眠（CPU 保持运行）
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AndWin:RecordService"
            )
        } else {
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AndWin:RecordService"
            )
        }.apply {
            // 录制期间无限持有 WakeLock，不设超时
            // 停止录制时在 releaseWakeLock() 中释放
            acquire()
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
        const val ACTION_START_RECORDING = "com.andwin.video.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.andwin.video.action.STOP_RECORDING"
        const val ACTION_RECORDING_STATE_CHANGED = "com.andwin.video.action.RECORDING_STATE_CHANGED"
    }
}
