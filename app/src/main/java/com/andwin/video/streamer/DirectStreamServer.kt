package com.andwin.video.streamer

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.AndroidViewFilterRender
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.rtspserver.RtspServerCamera1
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.coroutines.resume

class DirectStreamServer(
    private val context: Context,
    private val surfaceView: SurfaceView
) {

    private var rtspServer: RtspServerCamera1? = null
    
    // 时间水印相关 (使用 RootEncoder ViewSurfaceFilterRender)
    private var timeWatermarkView: TimeWatermarkView? = null
    private var watermarkFilter: BaseFilterRender? = null
    private var isWatermarkEnabled = false
    
    private var isPrepared = false
    private var isSurfaceReady = false
    private var videoOnlyMode = false
    private var usingFrontCamera = false

    private var surfaceWaitContinuation: kotlinx.coroutines.CancellableContinuation<Boolean>? = null

    var clientConnectedCallback: ((String) -> Unit)? = null
    var clientDisconnectedCallback: (() -> Unit)? = null
    var errorCallback: ((String) -> Unit)? = null
    var readyCallback: ((String) -> Unit)? = null
    var logCallback: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "DirectStream"
        const val RTSP_PORT = 8554
        const val STREAM_NAME = "live/stream"

        const val VIDEO_WIDTH = 640
        const val VIDEO_HEIGHT = 480
        const val FPS = 30
        const val BITRATE = 1200 * 1000
        
        const val AUDIO_BITRATE = 128 * 1000
        const val AUDIO_SAMPLE_RATE = 32000
        const val AUDIO_IS_STEREO = true
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logCallback?.invoke(msg)
    }

    private fun logError(msg: String, e: Exception? = null) {
        Log.e(TAG, msg, e)
        logCallback?.invoke("❌ $msg")
    }

    private fun postToMain(action: () -> Unit) {
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post(action)
        } catch (ex: Exception) {
            logError("UI thread error: ${ex.message}")
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            log("✅ Surface created! Size: ${surfaceView.width}x${surfaceView.height}")
            isSurfaceReady = true
            holder.setFormat(PixelFormat.TRANSLUCENT)

            surfaceWaitContinuation?.resume(true) {}
            surfaceWaitContinuation = null
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            log("Surface changed: ${width}x${height}, format=$format")
            if (width > 0 && height > 0) {
                isSurfaceReady = true
                log("✅ Surface ready with valid size")
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            log("Surface destroyed")
            isSurfaceReady = false
        }
    }

    init {
        surfaceView.holder.addCallback(surfaceCallback)
        surfaceView.holder.setFormat(PixelFormat.RGBA_8888)
        surfaceView.isFocusable = true
        surfaceView.isFocusableInTouchMode = true
    }

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionFailed(reason: String) {
            log("❌ Connection failed: $reason")
            postToMain { this@DirectStreamServer.errorCallback?.invoke(reason) }
        }

        override fun onConnectionStarted(url: String) {
            log("🔄 Client connecting: $url")
            postToMain { this@DirectStreamServer.clientConnectedCallback?.invoke(url) }
        }

        override fun onConnectionSuccess() {
            log("✅ Client connected!")
        }

        override fun onDisconnect() {
            log("⚠️ Client disconnected")
            postToMain { clientDisconnectedCallback?.invoke() }
        }

        override fun onAuthError() { log("❌ Auth error") }
        override fun onAuthSuccess() { log("✅ Auth success") }
        override fun onNewBitrate(bitrate: Long) {}
    }

    /**
     * 启用时间水印（必须在 initialize 之前调用）
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

    /**
     * 设置水印滤镜 - 注意：仅 OpenGL 模式支持 FilterRender
     */
    private fun setupWatermarkFilter() {
        try {
            val view = timeWatermarkView ?: return

            // 检查是否支持 OpenGL
            val glInterface = rtspServer?.glInterface
            if (glInterface == null) {
                log("⚠️ 当前使用 Camera1 非OpenGL模式，无法使用 FilterRender 嵌入水印")
                log("💡 UI 层面水印仍会显示，但推流视频中将不包含水印")
                return
            }

            watermarkFilter = AndroidViewFilterRender().apply {
                setView(view)
            }

            glInterface.setFilter(watermarkFilter!!)

            log("✅ 时间水印滤镜已添加到渲染管线 (AndroidViewFilterRender + OpenGL)")
        } catch (e: Exception) {
            logError("设置水印滤镜失败: ${e.message}", e)
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

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address && !address.isLinkLocalAddress) {
                            return address.hostAddress ?: ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logError("Error getting IP: ${e.message}")
        }
        return "127.0.0.1"
    }

    fun getRtspUrl(): String {
        val ip = getLocalIpAddress()
        return "rtsp://$ip:$RTSP_PORT/$STREAM_NAME"
    }

    fun isSurfaceReady(): Boolean = isSurfaceReady && surfaceView.width > 0 && surfaceView.height > 0

    suspend fun waitForSurface(timeoutMs: Long = 10000): Boolean {
        if (isSurfaceReady && surfaceView.width > 0 && surfaceView.height > 0) {
            log("✅ Surface already ready! Size: ${surfaceView.width}x${surfaceView.height}")
            return true
        }

        log("⏳ Waiting for Surface (non-blocking)...")
        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    surfaceWaitContinuation = continuation
                    continuation.invokeOnCancellation {
                        surfaceWaitContinuation = null
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            logError("Surface wait timeout after ${timeoutMs}ms! Size: ${surfaceView.width}x${surfaceView.height}")
            false
        }
    }

    fun initialize(isPortrait: Boolean = true): Boolean {
        return try {
            val rotation = if (isPortrait) 90 else 0
            
            log("═══════════════════════════════════════")
            log("🎬 DirectStream Server 初始化")
            log("   配置: ${VIDEO_WIDTH}x${VIDEO_HEIGHT}@$FPS @${BITRATE/1024}kbps")
            log("   Rotation: $rotation° (${if (isPortrait) "竖屏" else "横屏"})")
            log("   水印: ${if (isWatermarkEnabled) "✅ 已启用" else "❌ 未启用"}")
            log("═══════════════════════════════════════")

            log("[1/5] 创建 RtspServerCamera1...")
            rtspServer = RtspServerCamera1(surfaceView, connectChecker, RTSP_PORT)

            if (rtspServer == null) {
                logError("创建 RtspServerCamera1 失败!")
                return false
            }
            log("✅ RtspServerCamera1 创建成功")

            log("[2/5] 准备视频编码器...")
            val videoOk = prepareVideoEncoder(rotation)
            if (!videoOk) {
                logError("视频编码器初始化失败!")
                return false
            }
            log("✅ 视频编码器就绪")

            log("[3/5] 准备音频编码器...")
            val audioOk = tryPrepareAudio()
            if (audioOk) {
                log("✅ 音频编码器就绪")
                videoOnlyMode = false
            } else {
                log("⚠️ 音频不可用 → 纯视频模式")
                videoOnlyMode = true
            }

            // 如果启用了水印，设置滤镜
            if (isWatermarkEnabled && timeWatermarkView != null) {
                setupWatermarkFilter()
            }

            isPrepared = true

            val url = getRtspUrl()
            log("")
            log("═══════════════════════════════════════")
            log("✅ 初始化完成!")
            log("   模式: ${if (videoOnlyMode) "纯视频" else "视频+音频"}")
            log("   地址: $url")
            log("═══════════════════════════════════════")

            readyCallback?.invoke(url)
            true
        } catch (e: Exception) {
            logError("初始化异常: ${e.javaClass.simpleName} - ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    fun startPreviewAndWait(useFrontCamera: Boolean = false): Boolean {
        return try {
            if (!isPrepared) {
                logError("未初始化")
                return false
            }

            log("[4/5] 启动摄像头预览...")
            log("   Surface 状态: Ready=$isSurfaceReady, Size=${surfaceView.width}x${surfaceView.height}")
            // Camera1 API facing: 0=后置(CAMERA_FACING_BACK), 1=前置(CAMERA_FACING_FRONT)
            val facing = if (useFrontCamera) 1 else 0
            log("   请求摄像头: ${if (useFrontCamera) "前置(facing=1)" else "后置(facing=0)"}")

            usingFrontCamera = useFrontCamera
            
            rtspServer?.startPreview(facing)
            
            // 启动水印计时
            timeWatermarkView?.start()
            
            log("✅ 预览已启动 (直接使用目标摄像头，无需切换)")

            true
        } catch (e: Exception) {
            logError("启动预览失败: ${e.message}", e)
            false
        }
    }

    private fun prepareVideoEncoder(rotation: Int): Boolean {
        return try {
            rtspServer?.prepareVideo(
                VIDEO_WIDTH, VIDEO_HEIGHT, FPS, BITRATE, rotation
            ) ?: false
        } catch (e: Exception) {
            logError("视频编码异常: ${e.message}", e)
            false
        }
    }

    private fun tryPrepareAudio(): Boolean {
        val configs = listOf(
            Triple(AUDIO_BITRATE, AUDIO_SAMPLE_RATE, AUDIO_IS_STEREO),
            Triple(64000, 22050, false),
            Triple(32000, 16000, false),
        )

        for ((bitrate, sampleRate, stereo) in configs) {
            try {
                log("   尝试: ${bitrate/1000}kbps @ ${sampleRate}Hz stereo=$stereo")
                val ok = rtspServer?.prepareAudio(bitrate, sampleRate, stereo, false, false) ?: false
                if (ok) return true
                log("   ❌ 失败，尝试下一个...")
            } catch (e: Exception) {
                log("   ❌ 异常: ${e.message}")
            }
        }
        return false
    }

    fun startServer(): Boolean {
        if (rtspServer?.isStreaming == true) {
            log("已在推流中")
            return false
        }
        if (!isPrepared) {
            logError("未初始化")
            return false
        }

        return try {
            val url = getRtspUrl()
            log("")
            log("[5/5] 🚀 启动 RTSP 服务器...")
            log("   监听地址: $url")
            log("   Surface 尺寸: ${surfaceView.width}x${surfaceView.height}")

            // 确保水印在推流前启动
            if (isWatermarkEnabled && timeWatermarkView != null && !timeWatermarkView!!.isRunning) {
                timeWatermarkView?.start()
            }
            
            rtspServer?.startStream()

            log("")
            log("🎉🎉🎉  RTSP 服务器已启动! 🎉🎉🎉")
            log("   VLC: 打开网络串流 → $url")
            log("   ffplay: ffplay $url")

            true
        } catch (e: Exception) {
            logError("启动服务器失败: ${e.message}")
            postToMain { errorCallback?.invoke("启动失败: ${e.message}") }
            false
        }
    }

    fun stopServer() {
        try {
            log("停止 RTSP 服务器...")
            rtspServer?.stopStream()
            log("服务器已停止")
        } catch (e: Exception) {
            logError("停止服务器错误: ${e.message}")
        }
    }

    fun stopPreview() {
        try {
            timeWatermarkView?.stop()
            rtspServer?.stopPreview()
            log("预览已停止")
        } catch (e: Exception) {
            logError("停止预览错误: ${e.message}")
        }
    }

    fun release() {
        try {
            stopServer()
            stopPreview()
            
            // 清除水印滤镜
            watermarkFilter?.let {
                try { rtspServer?.glInterface?.clearFilters() } catch (_: Exception) {}
            }
            watermarkFilter = null
            
            timeWatermarkView?.stop()
            timeWatermarkView = null
            
            rtspServer = null
            isPrepared = false
            isSurfaceReady = false
            videoOnlyMode = false
            usingFrontCamera = false
            surfaceWaitContinuation = null
            log("资源已释放")
        } catch (e: Exception) {
            logError("释放资源错误: ${e.message}")
        }
    }

    fun isCurrentlyStreaming(): Boolean = rtspServer?.isStreaming == true
    fun isVideoOnlyMode(): Boolean = videoOnlyMode
    fun isPrepared(): Boolean = isPrepared

    fun switchCamera() {
        try {
            rtspServer?.switchCamera()
            usingFrontCamera = !usingFrontCamera
            log("摄像头已切换 → ${if (usingFrontCamera) "前置" else "后置"}")
        } catch (e: Exception) {
            logError("切换摄像头错误: ${e.message}")
        }
    }

    fun isUsingFrontCamera(): Boolean = usingFrontCamera

    fun enableAudio(enable: Boolean) {
        try {
            if (enable) rtspServer?.enableAudio() else rtspServer?.disableAudio()
        } catch (e: Exception) {
            logError("启用/禁用音频错误: ${e.message}")
        }
    }
}
