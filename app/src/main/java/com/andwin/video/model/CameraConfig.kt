package com.andwin.video.model

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
) : Parcelable

enum class Resolution(val width: Int, val height: Int, val displayName: String) {
    SD_480P(854, 480, "SD (480p)"),
    HD_720P(1280, 720, "HD (720p)"),
    FULL_HD_1080P(1920, 1080, "Full HD (1080p)"),
    UHD_4K(3840, 2160, "4K")
}

data class StreamConfig(
    val url: String,
    val protocol: StreamProtocol = StreamProtocol.RTMP,
    val bitrate: Int = 2000000,
    val enableAudio: Boolean = true,
    val enableVideo: Boolean = true
)

enum class StreamProtocol(val displayName: String) {
    RTMP("RTMP"),
    RTSP("RTSP"),
    SRT("SRT")
}
