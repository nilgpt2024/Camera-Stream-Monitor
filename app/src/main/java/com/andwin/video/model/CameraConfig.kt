package com.andwin.video.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraConfig(
    val id: String,
    val name: String,
    val cameraId: String = "0",
    val resolution: Resolution = Resolution.HD_720P,
    val fps: Int = 30,
    val bitrate: Int = 2000000,
    val isRecording: Boolean = false,
    val isStreaming: Boolean = false,
    val streamUrl: String = "",
    val recordPath: String = ""
) : Parcelable {

    /**
     * 从 SharedPreferences 读取用户设置并应用到当前配置
     */
    fun applySettings(context: Context): CameraConfig {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val settings = prefs.readSettings()
        return this.copy(
            resolution = settings.resolution,
            fps = settings.fps,
            bitrate = settings.bitrate
        )
    }
}

enum class Resolution(val width: Int, val height: Int, val displayName: String) {
    SD_480P(854, 480, "SD (480p)"),
    HD_720P(1280, 720, "HD (720p)"),
    FULL_HD_1080P(1920, 1080, "Full HD (1080p)"),
    UHD_4K(3840, 2160, "4K");

    companion object {
        /**
         * 根据字符串解析分辨率（支持 "1280x720" 格式）
         */
        fun fromString(value: String): Resolution {
            return when (value) {
                "854x480" -> SD_480P
                "1280x720" -> HD_720P
                "1920x1080" -> FULL_HD_1080P
                "3840x2160", "38402160" -> UHD_4K  // 兼容可能的 typo
                else -> {
                    Log.w("CameraConfig", "未知分辨率值: $value，使用默认 720p")
                    HD_720P
                }
            }
        }
    }
}

/**
 * 用户视频设置数据类
 */
data class VideoSettings(
    val resolution: Resolution = Resolution.HD_720P,
    val fps: Int = 30,
    val bitrate: Int = 2000000
)

/**
 * 从 SharedPreferences 读取视频相关设置
 */
fun SharedPreferences.readSettings(): VideoSettings {
    // 分辨率
    val resolutionStr = getString("resolution", "1280x720") ?: "1280x720"
    val resolution = Resolution.fromString(resolutionStr)

    // 帧率
    val fpsStr = getString("fps", "30") ?: "30"
    val fps = fpsStr.toIntOrNull()?.coerceIn(1, 120) ?: 30

    // 码率
    val bitrateStr = getString("bitrate", "2000000") ?: "2000000"
    val bitrate = bitrateStr.toIntOrNull()?.coerceIn(100000, 50000000) ?: 2000000

    Log.i("VideoSettings", "读取设置: ${resolution.width}x${resolution.height} @ ${fps}fps | ${bitrate/1000}kbps")
    return VideoSettings(resolution, fps, bitrate)
}
