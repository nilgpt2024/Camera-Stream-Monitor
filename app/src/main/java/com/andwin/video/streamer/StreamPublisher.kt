package com.andwin.video.streamer

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.andwin.video.R
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.AndroidViewFilterRender
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.library.rtmp.RtmpCamera1

class StreamPublisher(
    private val context: Context,
    private val surfaceView: SurfaceView
) {

    private var rtmpCamera: RtmpCamera1? = null

    // 时间水印相关 (使用 RootEncoder ViewSurfaceFilterRender)
    private var timeWatermarkView: TimeWatermarkView? = null
    private var watermarkFilter: BaseFilterRender? = null
    private var isWatermarkEnabled = false

    // 视频配置（可从外部设置）
    var videoWidth: Int = 640
    var videoHeight: Int = 480
    var fps: Int = 30
    var bitrate: Int = 1200 * 1000

    var onConnectionFailed: ((String) -> Unit)? = null
    var onConnectionStarted: (() -> Unit)? = null
    var onConnectionSuccess: (() -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null
    var onNewBitrate: ((Long) -> Unit)? = null

    companion object {
        private const val TAG = "StreamPublisher"

        // 默认值（用于兼容未调用 configureVideo 的情况）
        const val DEFAULT_VIDEO_WIDTH = 640
        const val DEFAULT_VIDEO_HEIGHT = 480
        const val DEFAULT_FPS = 30
        const val DEFAULT_BITRATE = 1200 * 1000

        const val AUDIO_BITRATE = 128 * 1000
        const val AUDIO_SAMPLE_RATE = 32000
        const val AUDIO_IS_STEREO = true
    }

    /**
     * 配置视频参数（在 initializeWithCamera 之前调用）
     */
    fun configureVideo(width: Int, height: Int, fps: Int, bitrate: Int) {
        this.videoWidth = width
        this.videoHeight = height
        this.fps = fps
        this.bitrate = bitrate
        Log.i(TAG, "视频配置已更新: ${width}x${height} @ ${fps}fps | ${bitrate/1000}kbps")
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        onLog?.invoke(msg)
    }

    private fun logError(msg: String, e: Exception? = null) {
        Log.e(TAG, msg, e)
        onLog?.invoke("❌ $msg")
    }

    private val connectChecker = object : ConnectChecker {
        
        override fun onConnectionFailed(reason: String) {
            log("❌ RTMP connection failed: $reason")
            onConnectionFailed?.invoke(reason)
        }

        override fun onConnectionStarted(url: String) {
            log("🔄 RTMP connection started: $url")
            onConnectionStarted?.invoke()
        }

        override fun onConnectionSuccess() {
            log("✅ RTMP connection success!")
            onConnectionSuccess?.invoke()
        }

        override fun onDisconnect() {
            log("RTMP disconnected")
            onDisconnect?.invoke()
        }
        
        override fun onAuthError() {
            log("❌ RTMP auth error")
            onError?.invoke(context.getString(R.string.error_auth_failed))
        }

        override fun onAuthSuccess() {
            log("✅ RTMP auth success")
        }

        override fun onNewBitrate(bitrate: Long) {
            onNewBitrate?.invoke(bitrate)
        }
    }

    /**
     * 启用时间水印（必须在 initialize 之前调用）
     * 使用 RootEncoder ViewSurfaceFilterRender 将 TimeWatermarkView 叠加到视频流上
     */
    fun enableTimeWatermark(
        textColor: Int = Color.WHITE,
        textSizePx: Float = 36f,
        position: TimeWatermarkView.WatermarkPosition = TimeWatermarkView.WatermarkPosition.BOTTOM_RIGHT
    ): TimeWatermarkView? {
        return try {
            log("📝 启用时间水印 (RootEncoder ViewSurfaceFilterRender)...")
            
            timeWatermarkView = TimeWatermarkView(context).apply {
                this.textColor = textColor
                this.textSizePx = textSizePx
                this.position = position
                this.showDate = true
                this.showTime = true
            }
            
            isWatermarkEnabled = true
            log("✅ 时间水印已配置 (位置=${position.name})")
            
            timeWatermarkView
        } catch (e: Exception) {
            logError("启用时间水印失败: ${e.message}")
            null
        }
    }

    fun disableTimeWatermark() {
        isWatermarkEnabled = false
        timeWatermarkView?.stop()
        timeWatermarkView = null
        log("时间水印已禁用")
    }

    /**
     * 设置外部创建的 TimeWatermarkView（已添加到布局中）
     * 这样 AndroidViewFilterRender 才能正确渲染到视频流
     */
    fun setTimeWatermarkView(view: TimeWatermarkView) {
        try {
            timeWatermarkView = view
            isWatermarkEnabled = true
            log("✅ 已设置外部时间水印视图 (已attach到window)")
        } catch (e: Exception) {
            logError("设置时间水印视图失败: ${e.message}")
        }
    }

    fun getTimeWatermarkView(): TimeWatermarkView? = timeWatermarkView

    /**
     * 设置水印滤镜 - 使用 RootEncoder 2.4.6 正确的 API
     * 注意：仅 OpenGL 模式支持 FilterRender，Camera1 非OpenGL模式不支持
     */
    private fun setupWatermarkFilter() {
        try {
            val view = timeWatermarkView ?: return

            // 检查是否支持 OpenGL
            val glInterface = rtmpCamera?.glInterface
            if (glInterface == null) {
                log("⚠️ 当前使用 Camera1 非OpenGL模式，无法使用 FilterRender 嵌入水印")
                log("💡 UI 层面水印仍会显示，但推流视频中将不包含水印")
                log("   如需推流水印，请改用 OpenGL 模式或使用 MediaCodec 后处理")
                return
            }

            // 创建 AndroidViewFilterRender 并绑定 TimeWatermarkView (2.4.6 可用)
            watermarkFilter = AndroidViewFilterRender().apply {
                setView(view)
            }

            // 使用 glInterface.setFilter() 添加滤镜（2.4.6 正确API）
            glInterface.setFilter(watermarkFilter!!)

            log("✅ 时间水印滤镜已添加到渲染管线 (AndroidViewFilterRender + OpenGL)")
        } catch (e: Exception) {
            logError("设置水印滤镜失败: ${e.message}", e)
        }
    }

    fun initialize(isPortrait: Boolean = true): Boolean {
        return try {
            if (rtmpCamera == null) {
                val rotation = if (isPortrait) 90 else 0
                
                log("========================================")
                log("🔧 Initializing RTMP Publisher")
                log("   Video: ${videoWidth}x${videoHeight}@$fps ${bitrate/1024}kbps")
                log("   Audio: ${AUDIO_BITRATE/1024}kbps @ ${AUDIO_SAMPLE_RATE}Hz")
                log("   Rotation: $rotation° (${if (isPortrait) "竖屏" else "横屏"})")
                log("   水印: ${if (isWatermarkEnabled) "✅ 已启用" else "❌ 未启用"}")
                log("========================================")
                
                rtmpCamera = RtmpCamera1(surfaceView, connectChecker)
                
                rtmpCamera?.let { camera ->
                    log("[1/2] Preparing video encoder...")
                    camera.prepareVideo(videoWidth, videoHeight, fps, bitrate, rotation)
                    log("✅ Video prepared OK")
                    
                    log("[2/2] Preparing audio encoder...")
                    camera.prepareAudio(AUDIO_BITRATE, AUDIO_SAMPLE_RATE, AUDIO_IS_STEREO)
                    log("✅ Audio prepared OK")
                    
                    // 如果启用了水印，设置滤镜
                    if (isWatermarkEnabled && timeWatermarkView != null) {
                        setupWatermarkFilter()
                    }
                    
                    log("")
                    log("✅ StreamPublisher initialized successfully!")
                    return true
                } ?: run {
                    log("❌ Failed to create RtmpCamera1 instance")
                    return false
                }
            } else {
                log("RtmpCamera already initialized")
                true
            }
        } catch (e: Exception) {
            log("❌ Initialize exception: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun initializeWithCamera(cameraId: String = "0", isPortrait: Boolean = true): Boolean {
        return initialize(isPortrait)
    }

    fun startPreview(facing: Int = 0): Boolean {
        return try {
            val camera = rtmpCamera
            if (camera == null) {
                log("⚠️ Not initialized yet")
                return false
            }
            
            if (camera.isOnPreview) {
                log("Preview already started")
                return true
            }
            
            log("Starting camera preview... (facing=$facing)")
            camera.startPreview(facing)
            
            // 启动水印计时
            timeWatermarkView?.start()
            
            log("✅ Preview started successfully")
            true
        } catch (e: Exception) {
            log("❌ Start preview failed: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun stopPreview() {
        try {
            log("Stopping preview...")
            timeWatermarkView?.stop()
            rtmpCamera?.stopPreview()
            log("Preview stopped")
        } catch (e: Exception) {
            log("Stop preview failed: ${e.message}")
        }
    }

    fun startStream(rtmpUrl: String): Boolean {
        val camera = rtmpCamera
        
        if (camera == null) {
            log("Not initialized")
            return false
        }

        if (camera.isStreaming) {
            log("Already streaming")
            return false
        }

        return try {
            log("")
            log("========================================")
            log("🚀 Starting RTMP stream to:")
            log("   URL: $rtmpUrl")
            log("========================================")
            
            if (!camera.isOnPreview) {
                log("Auto-starting preview before stream...")
                startPreview()
            }
            
            // 确保水印在推流前启动
            if (isWatermarkEnabled && timeWatermarkView != null && !timeWatermarkView!!.isRunning) {
                timeWatermarkView?.start()
            }
            
            camera.startStream(rtmpUrl)
            
            log("✅ Stream command sent successfully!")
            true
        } catch (e: Exception) {
            log("❌ Error starting stream: ${e.message}")
            e.printStackTrace()
            onError?.invoke(context.getString(R.string.error_start_failed, e.message ?: ""))
            false
        }
    }

    fun stopStream() {
        try {
            val camera = rtmpCamera
            if (camera != null && camera.isStreaming) {
                log("Stopping RTMP stream...")
                camera.stopStream()
                log("Stream stopped")
            }
        } catch (e: Exception) {
            log("Error stopping stream: ${e.message}")
        }
    }

    fun release() {
        try {
            stopStream()
            stopPreview()
            
            // 清除水印滤镜
            watermarkFilter?.let {
                try { rtmpCamera?.glInterface?.clearFilters() } catch (_: Exception) {}
            }
            watermarkFilter = null
            
            timeWatermarkView?.stop()
            timeWatermarkView = null
            
            rtmpCamera = null
            log("Released all resources")
        } catch (e: Exception) {
            log("Error releasing: ${e.message}")
        }
    }

    fun isCurrentlyStreaming(): Boolean = rtmpCamera?.isStreaming == true
    fun isPrepared(): Boolean = rtmpCamera != null
    fun isOnPreview(): Boolean = rtmpCamera?.isOnPreview == true

    fun switchCamera() {
        try {
            log("Switching camera...")
            rtmpCamera?.switchCamera()
            log("Camera switched")
        } catch (e: Exception) {
            log("Switch camera failed: ${e.message}")
        }
    }

    fun enableAudio(enable: Boolean) {
        try {
            if (enable) {
                rtmpCamera?.enableAudio()
                log("Audio enabled")
            } else {
                rtmpCamera?.disableAudio()
                log("Audio disabled")
            }
        } catch (e: Exception) {
            log("Enable audio error: ${e.message}")
        }
    }

    fun setVideoBitrate(bitrate: Int) {
        try {
            rtmpCamera?.setVideoBitrateOnFly(bitrate)
        } catch (e: Exception) {
            log("Set bitrate error: ${e.message}")
        }
    }
    
    fun getGlInterface() = rtmpCamera?.glInterface
}
