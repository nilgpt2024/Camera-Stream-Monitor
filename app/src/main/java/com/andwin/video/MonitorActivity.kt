package com.andwin.video

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.andwin.video.camera.CameraManager
import com.andwin.video.databinding.ActivityMonitorBinding
import com.andwin.video.model.CameraConfig
import com.andwin.video.recorder.VideoRecorder
import com.andwin.video.service.RecordService
import com.andwin.video.service.StreamService
import com.andwin.video.streamer.StreamPublisher
import com.andwin.video.streamer.DirectStreamServer
import com.andwin.video.streamer.TimeWatermarkView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitorBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var videoRecorder: VideoRecorder
    private lateinit var streamPublisher: StreamPublisher
    private lateinit var directStreamServer: DirectStreamServer
    private lateinit var cameraConfig: CameraConfig
    private var isRecording = false
    private var isStreaming = false
    private var isDirectMode = false
    private var isCameraReady = false
    private var isDualMode = false
    
    // 录制计时器
    private var recordingStartTime: Long = 0
    private var recordingTimerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraConfig = intent.getParcelableExtra("camera_config") ?: run {
            finish()
            return
        }

        title = cameraConfig.name
        initializeComponents()
        setupListeners()
        initializeCameraAsync()
    }

    private fun initializeComponents() {
        cameraManager = CameraManager(this)
        videoRecorder = VideoRecorder(this)
        streamPublisher = StreamPublisher(this, binding.surfaceView)
        directStreamServer = DirectStreamServer(this, binding.surfaceView)

        // 配置时间水印（UI显示 + 推流嵌入共用同一个视图）
        binding.timeWatermark.apply {
            textColor = Color.YELLOW  // 使用黄色更醒目
            textSizePx = 36f         // 稍大一点
            bgColor = Color.parseColor("#CC000000")  // 半透明黑背景
            position = TimeWatermarkView.WatermarkPosition.TOP_LEFT  // 左上角
            showDate = true
            showTime = true
            showMilliseconds = true  // 显示毫秒，更容易看到变化
            paddingX = 16f
            paddingY = 12f
            cornerRadius = 8f
        }

        Log.i("MonitorActivity", "📝 时间水印配置完成:")
        Log.i("MonitorActivity", "   - 位置: 左上角 (TOP_LEFT)")
        Log.i("MonitorActivity", "   - 颜色: 黄色文字 + 半透明黑背景")
        Log.i("MonitorActivity", "   - 字体: ${binding.timeWatermark.textSizePx}sp")

        // 显式启动时间水印（确保预览时显示）
        binding.timeWatermark.start()
        Log.i("MonitorActivity", "✅ 时间水印已启动 (UI层) - isRunning=${binding.timeWatermark.isRunning}")

        // 启用推流水印 - 使用已添加到布局的 UI 水印视图
        streamPublisher.setTimeWatermarkView(binding.timeWatermark)
        directStreamServer.setTimeWatermarkView(binding.timeWatermark)

        updateUIForLoadingState(true)
    }

    private fun setupListeners() {
        binding.btnRecordContainer.setOnClickListener {
            if (!isCameraReady) {
                Toast.makeText(this, "摄像头正在初始化，请稍候...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleRecording()
        }

        binding.btnStreamContainer.setOnClickListener {
            if (!isCameraReady) {
                Toast.makeText(this, "摄像头正在初始化，请稍候...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showStreamDialog()
        }

        binding.btnSwitchCamera.setOnClickListener {
            if (!isCameraReady) return@setOnClickListener
            switchCamera()
        }

        binding.btnCapture.setOnClickListener {
            if (!isCameraReady) {
                Toast.makeText(this, "摄像头正在初始化，请稍候...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            capturePhoto()
        }

        binding.btnDualCamera.setOnClickListener {
            if (!isCameraReady) {
                Toast.makeText(this, "摄像头正在初始化，请稍候...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleDualCameraMode()
        }

        binding.btnBack.setOnClickListener {
            stopAll()
            finish()
        }
    }

    private fun initializeCameraAsync() {
        lifecycleScope.launch {
            try {
                val success = cameraManager.initialize()
                
                if (success) {
                    startPreview()
                    
                    isCameraReady = true
                    updateUIForLoadingState(false)
                    runOnUiThread {
                        binding.tvStatus.text = "就绪"
                        
                        val hasMultipleCameras = cameraManager.hasMultipleCameras()
                        if (hasMultipleCameras) {
                            binding.btnDualCamera.visibility = View.VISIBLE
                            binding.tvHint.text = "点击红色按钮开始录制 | 紫色按钮尝试双摄"
                        } else {
                            binding.tvHint.text = "点击红色按钮开始录制 | 本设备只有一个摄像头"
                            binding.btnDualCamera.alpha = 0.3f
                            binding.btnDualCamera.isEnabled = false
                        }
                    }
                } else {
                    isCameraReady = false
                    updateUIForLoadingState(false)
                    runOnUiThread {
                        binding.tvStatus.text = "初始化失败"
                        Toast.makeText(this@MonitorActivity, "摄像头初始化失败", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                isCameraReady = false
                updateUIForLoadingState(false)
                runOnUiThread {
                    binding.tvStatus.text = "错误: ${e.message}"
                    Toast.makeText(this@MonitorActivity, "摄像头错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startPreview() {
        val resolution = android.util.Size(cameraConfig.resolution.width, cameraConfig.resolution.height)
        
        if (isDualMode) {
            val dualSuccess = cameraManager.startDualCamera(
                binding.previewView,
                binding.previewViewBack,
                resolution
            )
            
            if (dualSuccess) {
                binding.previewViewBack.visibility = View.VISIBLE
                binding.dualCameraIndicator.visibility = View.VISIBLE
                binding.previewViewBack.elevation = 10f
            } else {
                isDualMode = false
                binding.previewViewBack.visibility = View.GONE
                binding.dualCameraIndicator.visibility = View.GONE
                cameraManager.startCamera(binding.previewView, cameraConfig.cameraId, resolution)
                Toast.makeText(this, "本设备不支持双摄模式，已切换到单摄", Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraManager.startCamera(binding.previewView, cameraConfig.cameraId, resolution)
        }
    }

    /**
     * 切换双摄模式
     */
    private fun toggleDualCameraMode() {
        if (isRecording || isStreaming) {
            Toast.makeText(this, "请先停止录制/推流后再切换模式", Toast.LENGTH_SHORT).show()
            return
        }

        isDualMode = !isDualMode
        
        isCameraReady = false
        updateUIForLoadingState(true)
        
        cameraManager.stopCamera()
        
        lifecycleScope.launch {
            try {
                startPreview()
                isCameraReady = true
                updateUIForLoadingState(false)
                
                if (isDualMode) {
                    binding.btnDualCamera.backgroundTintList = ContextCompat.getColorStateList(
                        this@MonitorActivity, 
                        android.R.color.holo_purple
                    )
                    binding.tvResolutionInfo.text = "双摄模式 | 前置+后置"
                    Toast.makeText(this@MonitorActivity, "✅ 已开启双摄模式（前后同时录制）", Toast.LENGTH_SHORT).show()
                } else {
                    binding.btnDualCamera.backgroundTintList = ContextCompat.getColorStateList(
                        this@MonitorActivity, 
                        R.color.primary
                    )
                    binding.tvResolutionInfo.text = "${cameraConfig.resolution.width}×${cameraConfig.resolution.height} @ ${cameraConfig.fps}fps"
                    Toast.makeText(this@MonitorActivity, "已关闭双摄模式", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                isDualMode = !isDualMode
                isCameraReady = false
                updateUIForLoadingState(false)
                Toast.makeText(this@MonitorActivity, "切换失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUIForLoadingState(isLoading: Boolean) {
        runOnUiThread {
            val enabled = !isLoading

            binding.btnRecordContainer.isEnabled = enabled
            binding.btnStreamContainer.isEnabled = enabled
            binding.btnSwitchCamera.isEnabled = enabled
            binding.btnCapture.isEnabled = enabled
            binding.btnDualCamera.isEnabled = enabled

            if (isLoading) {
                binding.tvStatus.text = "正在初始化摄像头..."
                binding.statusDot.setBackgroundResource(R.drawable.circle_status_ready)

                binding.btnRecordContainer.alpha = 0.5f
                binding.btnStreamContainer.alpha = 0.5f
                binding.btnSwitchCamera.alpha = 0.5f
                binding.btnCapture.alpha = 0.5f
                binding.btnDualCamera.alpha = 0.5f
            } else {
                binding.btnRecordContainer.alpha = 1f
                binding.btnStreamContainer.alpha = 1f
                binding.btnSwitchCamera.alpha = 1f
                binding.btnCapture.alpha = 1f
                binding.btnDualCamera.alpha = 1f
            }
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    /**
     * 开始录制 - 优先使用 VideoCapture（真正录像），回退到 ImageCapture（拍照）
     */
    private fun startRecording() {
        // 策略1: 尝试使用 VideoCapture 进行真正的视频录制
        val videoCapture = if (isDualMode && cameraManager.isDualCameraActive()) {
            cameraManager.getFrontVideoCapture() ?: cameraManager.getVideoCapture()
        } else {
            cameraManager.getVideoCapture()
        }
        
        if (videoCapture != null) {
            Log.i("MonitorActivity", "使用 VideoCapture 进行视频录制")
            
            val success = videoRecorder.startVideoRecording(
                videoCapture,
                onComplete = { outputFile ->
                    runOnUiThread {
                        val sizeKB = outputFile.length() / 1024
                        val msg = "🎬 视频已保存: ${outputFile.name} (${sizeKB}KB)"
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        
                        Log.i("MonitorActivity", "✅ 视频录制完成!")
                        
                        stopRecordingTimer()
                        updateRecordButton(false)
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Log.e("MonitorActivity", "❌ 视频录制失败: $error")
                        Toast.makeText(this, "视频录制失败: $error\n尝试拍照模式...", Toast.LENGTH_LONG).show()
                        
                        // 回退到拍照模式
                        startPhotoMode()
                    }
                }
            )

            if (success) {
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
                updateRecordButton(true)
                startRecordingTimer()
                startRecordService()
                
                Log.i("MonitorActivity", "🎬 视频录制已启动 (VideoCapture)")
                return
            }
        }

        // 策略2: 回退到 ImageCapture 拍照模式
        Log.w("MonitorActivity", "VideoCapture 不可用，使用拍照模式")
        startPhotoMode()
    }

    /**
     * 拍照模式（备用方案）
     */
    private fun startPhotoMode() {
        val imageCapture = if (isDualMode && cameraManager.isDualCameraActive()) {
            cameraManager.getFrontImageCapture() ?: cameraManager.getImageCapture()
        } else {
            cameraManager.getImageCapture()
        }
        
        if (imageCapture == null) {
            Toast.makeText(this, "摄像头未就绪", Toast.LENGTH_SHORT).show()
            return
        }

        val success = videoRecorder.startRecording(
            imageCapture,
            onComplete = { outputFile ->
                runOnUiThread {
                    val sizeKB = outputFile.length() / 1024
                    val msg = "📸 已保存: ${outputFile.name} (${sizeKB}KB)"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    
                    Log.i("MonitorActivity", "✅ 拍照完成: ${outputFile.absolutePath}")
                    
                    stopRecordingTimer()
                    updateRecordButton(false)
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "❌ $error", Toast.LENGTH_LONG).show()
                    stopRecordingTimer()
                    updateRecordButton(false)
                }
            }
        )

        if (success) {
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            updateRecordButton(true)
            startRecordingTimer()
            startRecordService()
            
            Log.i("MonitorActivity", "📸 拍照模式已启动 (ImageCapture)")
        }
    }

    /**
     * 停止视频录制
     */
    private fun stopRecording() {
        videoRecorder.stopRecording { file ->
            isRecording = false
            updateRecordButton(false)
            stopRecordingTimer()
            stopRecordService()
        }
    }

    /**
     * 启动录制计时器
     */
    private fun startRecordingTimer() {
        binding.recordingIndicator.visibility = View.VISIBLE
        
        recordingTimerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = System.currentTimeMillis() - recordingStartTime
                    val seconds = (elapsed / 1000) % 60
                    val minutes = (elapsed / (1000 * 60)) % 60
                    val hours = elapsed / (1000 * 60 * 60)
                    
                    binding.tvRecordingTime.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    binding.tvRecordingTime.postDelayed(this, 1000)
                }
            }
        }
        binding.tvRecordingTime.post(recordingTimerRunnable!!)
    }

    /**
     * 停止录制计时器
     */
    private fun stopRecordingTimer() {
        recordingTimerRunnable?.let { binding.tvRecordingTime.removeCallbacks(it) }
        recordingTimerRunnable = null
    }

    private fun showStreamDialog() {
        val options = arrayOf(
            "📡 直连模式 (推荐，无需服务器)",
            "🌐 RTMP 推流模式 (需要服务器)"
        )
        
        AlertDialog.Builder(this)
            .setTitle("选择推流方式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startDirectStream()
                    1 -> showRtmpInputDialog()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showRtmpInputDialog() {
        // 读取默认的 RTMP 地址
        val sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val defaultRtmpUrl = sharedPreferences.getString("default_rtmp_url", "") ?: ""
        
        val editText = EditText(this).apply {
            hint = "rtmp://服务器地址/应用/流名"
            // 优先使用 cameraConfig 中的地址，如果没有就用默认地址
            setText(if (cameraConfig.streamUrl.isNotEmpty()) cameraConfig.streamUrl else defaultRtmpUrl)
        }

        AlertDialog.Builder(this)
            .setTitle("🌐 RTMP 推流设置")
            .setMessage("输入 RTMP 推流地址\n\n" +
                    "支持的服务器：\n" +
                    "• SRS: rtmp://IP/live/stream\n" +
                    "• nginx-rtmp: rtmp://IP/live/stream\n\n" +
                    "提示：在设置中可以配置默认地址")
            .setView(editText)
            .setPositiveButton("开始推流") { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotEmpty()) {
                    startStreaming(url)
                } else {
                    Toast.makeText(this, "请输入推流地址", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private var streamJob: kotlinx.coroutines.Job? = null

    private fun startDirectStream() {
        if (isStreaming) {
            Toast.makeText(this, "已在推流中，请先停止", Toast.LENGTH_SHORT).show()
            return
        }

        if (isRecording) {
            Toast.makeText(this, "请先停止录制再开始推流", Toast.LENGTH_SHORT).show()
            return
        }

        isDirectMode = true

        directStreamServer.logCallback = { msg ->
            Log.i("StreamServer", msg)
        }

        directStreamServer.clientConnectedCallback = { clientInfo ->
            runOnUiThread {
                Log.i("MonitorActivity", "客户端已连接: $clientInfo")
                binding.tvStatus.text = "客户端已连接"
            }
        }

        directStreamServer.clientDisconnectedCallback = {
            runOnUiThread {
                Log.i("MonitorActivity", "客户端断开连接")
                binding.tvStatus.text = "等待连接..."
            }
        }

        directStreamServer.errorCallback = { error ->
            runOnUiThread {
                isStreaming = false
                updateStreamButton(false)
                binding.streamingIndicator.visibility = View.GONE
                binding.tvStatus.text = "错误"

                AlertDialog.Builder(this)
                    .setTitle("❌ 推流失败")
                    .setMessage("错误信息:\n$error\n\n可能的原因:\n1. 摄像头被其他应用占用\n2. 音频权限未授予\n3. 网络配置问题")
                    .setPositiveButton("查看日志") { _, _ ->
                        showLogInfo()
                    }
                    .setNegativeButton("关闭", null)
                    .show()

                switchToCameraXPreview()
            }
        }

        directStreamServer.readyCallback = { url ->
            runOnUiThread {
                showDirectStreamInfo(url)
            }
        }

        // 使用协程进行异步推流启动（优化后，无阻塞调用）
        streamJob = lifecycleScope.launch {
            try {
                // Phase 1: 切换到推流模式（不阻塞主线程）
                binding.tvStatus.text = "步骤1/4: 切换到推流模式..."
                switchToStreamPreviewNonBlocking()

                // Phase 2: 等待 Surface 创建（非阻塞）
                binding.tvStatus.text = "步骤2/4: 等待Surface创建..."
                val surfaceReady = directStreamServer.waitForSurface(10000)

                if (!surfaceReady) {
                    handleError("Surface 创建超时\n\n可能原因:\n1. SurfaceView 未正确显示\n2. 布局异常")
                    return@launch
                }

                logSurfaceSize("Surface已就绪")

                // Phase 3: 初始化编码器并启动预览
                binding.tvStatus.text = "步骤3/4: 初始化编码器..."
                val success = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    directStreamServer.initialize()
                }

                if (!success) {
                    handleError("视频编码器初始化失败\n\n可能原因:\n1. 摄像头被其他应用占用\n2. 设备不支持该分辨率/帧率配置\n3. 内存不足")
                    return@launch
                }

                // Phase 4: 启动预览和服务器
                binding.tvStatus.text = "步骤4/4: 启动摄像头预览和RTSP服务..."
                val useFrontCamera = cameraConfig.cameraId == "0"
                
                val serverSuccess = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    directStreamServer.startPreviewAndWait(useFrontCamera)
                    kotlinx.coroutines.delay(500)
                    directStreamServer.startServer()
                }

                if (serverSuccess) {
                    isStreaming = true
                    updateStreamButton(true)
                    binding.streamingIndicator.visibility = View.VISIBLE
                    binding.tvStatus.text = "✅ 直连服务已启动"
                    binding.statusDot.setBackgroundResource(R.drawable.circle_recording_pulse)

                    val url = directStreamServer.getRtspUrl()

                    AlertDialog.Builder(this@MonitorActivity)
                        .setTitle("🎉 推流成功！")
                        .setMessage("RTSP 服务已启动\n\n" +
                                "📍 地址: $url\n\n" +
                                "📺 在电脑上用 VLC 打开此地址即可观看\n\n" +
                                "⚠️ 注意: 如果音频初始化失败，将只推送视频流")
                        .setPositiveButton("复制地址") { _, _ ->
                            copyToClipboard(url)
                            Toast.makeText(this@MonitorActivity, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                        .setNeutralButton("生成网页") { _, _ ->
                            generateWebPlayerPage(url)
                        }
                        .setNegativeButton("关闭", null)
                        .show()

                    Log.i("MonitorActivity", "========================================")
                    Log.i("MonitorActivity", "🎯 直连模式已启动！")
                    Log.i("MonitorActivity", "   RTSP URL: $url")
                    Log.i("MonitorActivity", "========================================")
                } else {
                    handleError("启动 RTSP 服务器失败\n\n可能原因: 端口 8554 被占用或网络权限不足")
                }
            } catch (e: Exception) {
                Log.e("MonitorActivity", "Direct stream error", e)
                e.printStackTrace()
                handleError("异常: ${e.javaClass.simpleName}\n${e.message}")
            }
        }
    }
    
    private fun handleError(message: String) {
        runOnUiThread {
            isStreaming = false
            updateStreamButton(false)
            binding.streamingIndicator.visibility = View.GONE
            binding.tvStatus.text = "失败"
            
            AlertDialog.Builder(this)
                .setTitle("❌ 推流失败")
                .setMessage(message)
                .setPositiveButton("重试") { _, _ ->
                    startDirectStream()
                }
                .setNegativeButton("取消", null)
                .show()
                
            switchToCameraXPreview()
        }
    }
    
    private fun logSurfaceSize(context: String) {
        Log.i("MonitorActivity", "[$context] SurfaceView 尺寸: ${binding.surfaceView.width}x${binding.surfaceView.height}, 可见: ${binding.surfaceView.visibility == View.VISIBLE}")
    }

    private fun showLogInfo() {
        val logMsg = """
            推流调试信息:
            
            • 手机 IP: ${directStreamServer.getLocalIpAddress()}
            • RTSP 地址: ${directStreamServer.getRtspUrl()}
            
            请检查 Logcat 日志过滤 "DirectStreamServer"
            
            常见问题:
            1. 确保手机和电脑在同一 WiFi
            2. 防火墙允许端口 8554
            3. 摄像头/麦克风权限已授权
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("📋 调试信息")
            .setMessage(logMsg)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showDirectStreamInfo(rtspUrl: String) {
        val ip = directStreamServer.getLocalIpAddress()
        
        val message = """
            ✅ 直连服务已启动！无需任何中转服务器
            
            📍 本机 IP: $ip
            
            📺 其他设备连接方式：
            
            【方式1】VLC 播放器（推荐）
            打开 VLC → 媒体 → 打开网络串流 → 输入:
            $rtspUrl
            
            【方式2】ffplay 命令行
            ffplay $rtspUrl
            
            【方式3】另一部手机
            使用 VLC App 或本App播放器打开上述地址
            
            ⚠️ 确保所有设备在同一 WiFi 网络
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("🎯 直连模式 - P2P 视频传输")
            .setMessage(message)
            .setPositiveButton("复制地址") { _, _ ->
                copyToClipboard(rtspUrl)
                Toast.makeText(this, "✅ 已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("生成网页播放器") { _, _ ->
                generateWebPlayerPage(rtspUrl)
            }
            .setNegativeButton("知道了", null)
            .show()
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("RTSP URL", text)
        clipboard.setPrimaryClip(clip)
    }
    
    private fun generateWebPlayerPage(rtspUrl: String) {
        try {
            val htmlContent = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>视频监控 - 直连播放</title>
    <style>
        body { 
            margin: 0; 
            background: #000; 
            display: flex; 
            justify-content: center; 
            align-items: center; 
            min-height: 100vh;
            font-family: Arial, sans-serif;
        }
        .container { 
            text-align: center; 
            color: #fff; 
            max-width: 90%;
        }
        video { 
            width: 100%; 
            max-width: 800px; 
            border-radius: 8px;
            background: #111;
        }
        .info { 
            margin-top: 20px; 
            padding: 15px;
            background: rgba(255,255,255,0.1);
            border-radius: 8px;
        }
        .url { 
            font-size: 12px; 
            color: #aaa; 
            word-break: break-all;
            margin-top: 10px;
        }
        h2 { color: #03A9F4; margin-bottom: 10px; }
    </style>
</head>
<body>
    <div class="container">
        <h2>🎥 实时视频监控</h2>
        <video id="player" controls autoplay playsinline></video>
        <div class="info">
            <p><strong>状态:</strong> <span id="status">等待连接...</span></p>
            <p class="url" id="urlDisplay"></p>
        </div>
    </div>
    
    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
    <script>
        const rtspUrl = '$rtspUrl';
        document.getElementById('urlDisplay').textContent = '源地址: ' + rtspUrl;
        document.getElementById('status').textContent = '⚠️ 浏览器不支持直接播放 RTSP，请使用 VLC 或 ffplay';
        
        // 尝试使用 HLS.js 如果有 HLS 转换服务
        if (Hls.isSupported()) {
            // 如果 SRS 服务器开启了 HLS 转换，可以使用这个
            const hlsUrl = rtspUrl.replace('rtsp://', 'http://').replace(':8554', ':8080') + '.m3u8';
            console.log('尝试 HLS:', hlsUrl);
        }
    </script>
</body>
</html>
            """.trimIndent()
            
            val fileName = "stream_player_${System.currentTimeMillis()}.html"
            val file = File(getExternalFilesDir(null), fileName)
            file.writeText(htmlContent, Charsets.UTF_8)
            
            Toast.makeText(this, "📄 网页已保存: ${file.name}", Toast.LENGTH_LONG).show()
            Log.i("MonitorActivity", "HTML player saved to: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e("MonitorActivity", "Generate HTML failed", e)
            Toast.makeText(this, "生成网页失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startStreaming(url: String) {
        if (isStreaming) {
            Toast.makeText(this, "已在推流中，请先停止", Toast.LENGTH_SHORT).show()
            return
        }

        if (isRecording) {
            Toast.makeText(this, "请先停止录制再开始推流", Toast.LENGTH_SHORT).show()
            return
        }

        streamPublisher.onConnectionFailed = { reason ->
            runOnUiThread {
                isStreaming = false
                updateStreamButton(false)
                binding.streamingIndicator.visibility = View.GONE
                binding.tvStatus.text = "推流失败"
                Toast.makeText(this, "❌ 连接失败: $reason", Toast.LENGTH_LONG).show()
                switchToCameraXPreview()
            }
        }

        streamPublisher.onConnectionStarted = {
            runOnUiThread {
                binding.tvStatus.text = "正在连接服务器..."
                Toast.makeText(this, "🔄 正在建立连接...", Toast.LENGTH_SHORT).show()
            }
        }

        streamPublisher.onConnectionSuccess = {
            runOnUiThread {
                isStreaming = true
                updateStreamButton(true)
                binding.streamingIndicator.visibility = View.VISIBLE
                binding.tvStatus.text = "推流中"
                binding.statusDot.setBackgroundResource(R.drawable.circle_recording_pulse)
                Toast.makeText(this, "✅ 推流成功！外部设备可查看", Toast.LENGTH_LONG).show()
            }
        }

        streamPublisher.onDisconnect = {
            runOnUiThread {
                isStreaming = false
                updateStreamButton(false)
                binding.streamingIndicator.visibility = View.GONE
                binding.tvStatus.text = "已断开"
                binding.statusDot.setBackgroundResource(R.drawable.circle_status_ready)
                Toast.makeText(this, "已停止推流", Toast.LENGTH_SHORT).show()
                switchToCameraXPreview()
            }
        }

        streamPublisher.onError = { error ->
            runOnUiThread {
                Toast.makeText(this, "⚠️ 错误: $error", Toast.LENGTH_LONG).show()
            }
        }

        // 关键修复：使用协程，不阻塞主线程
        streamJob = lifecycleScope.launch {
            try {
                binding.tvStatus.text = "步骤1/3: 切换到推流模式..."
                switchToStreamPreviewNonBlocking()

                binding.tvStatus.text = "步骤2/3: 等待Surface创建..."
                val surfaceReady = directStreamServer.waitForSurface(10000)
                if (!surfaceReady) {
                    Toast.makeText(this@MonitorActivity, "Surface创建超时", Toast.LENGTH_SHORT).show()
                    switchToCameraXPreview()
                    return@launch
                }

                binding.tvStatus.text = "步骤3/3: 初始化RTMP编码器并启动推流..."
                val streamResult = withContext(Dispatchers.IO) {
                    val initOk = streamPublisher.initializeWithCamera(cameraConfig.cameraId)
                    if (initOk) {
                        kotlinx.coroutines.delay(300)
                        // cameraConfig.cameraId: "0"=前置, "1"=后置
                        // Camera1 API facing: 0=后置(CAMERA_FACING_BACK), 1=前置(CAMERA_FACING_FRONT)
                        val facing = if (cameraConfig.cameraId == "0") 1 else 0
                        streamPublisher.startPreview(facing)
                        kotlinx.coroutines.delay(500)
                        streamPublisher.startStream(url)
                    } else {
                        false
                    }
                }

                if (streamResult) {
                    isStreaming = true
                    updateStreamButton(true)
                    binding.streamingIndicator.visibility = View.VISIBLE
                    binding.tvStatus.text = "推流中"
                    binding.statusDot.setBackgroundResource(R.drawable.circle_recording_pulse)

                    startStreamService(url)
                    Log.i("MonitorActivity", "========================================")
                    Log.i("MonitorActivity", "🚀 RTMP 推流已启动!")
                    Log.i("MonitorActivity", "   URL: $url")
                    Log.i("MonitorActivity", "========================================")

                    Toast.makeText(this@MonitorActivity, "✅ RTMP 推流成功!\n$url", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MonitorActivity, "启动推流失败", Toast.LENGTH_SHORT).show()
                    switchToCameraXPreview()
                }
            } catch (e: Exception) {
                Log.e("MonitorActivity", "Start streaming error", e)
                Toast.makeText(this@MonitorActivity, "推流异常: ${e.message}", Toast.LENGTH_LONG).show()
                switchToCameraXPreview()
            }
        }
    }

    private fun switchToStreamPreviewNonBlocking() {
        Log.i("MonitorActivity", "═══ 切换到推流模式（非阻塞）═══")

        cameraManager.forceShutdown()
        Log.d("MonitorActivity", "[1/3] CameraProvider 已强制关闭")

        binding.previewView.visibility = View.GONE
        binding.previewViewBack.visibility = View.GONE

        // 显示 SurfaceView 容器（不调用 bringToFront，避免遮挡 UI 控件）
        binding.surfaceContainer.visibility = View.VISIBLE

        Log.d("MonitorActivity", "[2/3] SurfaceView 已显示")
        Log.d("MonitorActivity", "[3/3] 等待协程中 waitForSurface() 回调...")
        Log.i("MonitorActivity", "═══ UI切换完成，主线程空闲等待Surface ═══")
    }

    @Deprecated("Use switchToStreamPreviewNonBlocking() + waitForSurface() instead")
    private fun switchToStreamPreview() {
        switchToStreamPreviewNonBlocking()
    }

    /**
     * 切换回 CameraX 的 PreviewView
     */
    private fun switchToCameraXPreview() {
        try {
            Log.i("MonitorActivity", "═══ 切换回CameraX模式 ═══")
            
            // 步骤1: 完全释放 RootEncoder 资源
            binding.tvStatus.text = "正在释放推流资源..."
            if (isDirectMode) {
                directStreamServer.release()
                directStreamServer = DirectStreamServer(this, binding.surfaceView)
                directStreamServer.setTimeWatermarkView(binding.timeWatermark)
            } else {
                streamPublisher.release()
                streamPublisher = StreamPublisher(this, binding.surfaceView)
                streamPublisher.setTimeWatermarkView(binding.timeWatermark)
            }
            Log.d("MonitorActivity", "[1/4] RootEncoder已释放并重建")
            
            // 步骤2: 隐藏 SurfaceView 容器
            binding.surfaceContainer.visibility = View.GONE
            
            // 步骤3: 显示 PreviewView
            binding.previewView.visibility = View.VISIBLE
            Log.d("MonitorActivity", "[2/4] PreviewView已显示")
            
            // 步骤4: 使用协程在后台等待后重新初始化
            lifecycleScope.launch {
                // 在后台线程等待，不阻塞主线程
                kotlinx.coroutines.delay(500)
                
                // 步骤5: 重新初始化 CameraProvider
                binding.tvStatus.text = "正在重新初始化CameraX..."
                val success = cameraManager.reinitialize()
                
                if (success) {
                    // 重启 CameraX 预览
                    if (isDualMode) {
                        binding.previewViewBack.visibility = View.VISIBLE
                    }
                    startPreview()
                    Log.d("MonitorActivity", "[4/4] CameraX已重启")
                    
                    Log.i("MonitorActivity", "═══ 切换回CameraX完成 ═══")
                    binding.tvStatus.text = "就绪"
                } else {
                    Log.e("MonitorActivity", "CameraProvider 重新初始化失败")
                    runOnUiThread {
                        Toast.makeText(this@MonitorActivity, "摄像头重新初始化失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("MonitorActivity", "Error switching back to CameraX", e)
        }
    }

    private fun stopStreaming() {
        try {
            Log.i("MonitorActivity", "Stopping stream...")
            
            if (isDirectMode) {
                // 停止直连模式
                directStreamServer.stopServer()
                directStreamServer.release()
            } else {
                // 停止 RTMP 推流
                streamPublisher.stopStream()
                streamPublisher.release()
            }
            
            isStreaming = false
            isDirectMode = false
            updateStreamButton(false)
            binding.streamingIndicator.visibility = View.GONE
            binding.tvStatus.text = "就绪"
            binding.statusDot.setBackgroundResource(R.drawable.circle_status_ready)
            
            stopStreamService()
            
            // 恢复 CameraX 预览
            switchToCameraXPreview()
            
            Toast.makeText(this, "已停止推流", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MonitorActivity", "Stop streaming error", e)
        }
    }

    private fun switchCamera() {
        if (isStreaming) {
            if (isDirectMode) {
                directStreamServer.switchCamera()
            } else {
                streamPublisher.switchCamera()
            }
            val currentCamera = if (isDirectMode) {
                if (directStreamServer.isUsingFrontCamera()) "前置" else "后置"
            } else {
                "已切换"
            }
            Toast.makeText(this, "已切换到${currentCamera}摄像头", Toast.LENGTH_SHORT).show()
            return
        }

        val newCameraId = if (cameraConfig.cameraId == "0") "1" else "0"
        cameraConfig = cameraConfig.copy(cameraId = newCameraId)
        
        isCameraReady = false
        updateUIForLoadingState(true)
        
        cameraManager.stopCamera()
        
        lifecycleScope.launch {
            try {
                startPreview()
                isCameraReady = true
                updateUIForLoadingState(false)
            } catch (e: Exception) {
                isCameraReady = false
                updateUIForLoadingState(false)
                Toast.makeText(this@MonitorActivity, "切换摄像头失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 拍照
     */
    private fun capturePhoto() {
        val imageCapture = if (isDualMode && cameraManager.isDualCameraActive()) {
            val frontCap = cameraManager.getFrontImageCapture()
            val backCap = cameraManager.getBackImageCapture()
            
            frontCap?.let { 
                videoRecorder.takePhoto(it,
                    onComplete = { file -> 
                        Toast.makeText(this, "📷 前置照片已保存", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    })
            }
            
            backCap?.let {
                videoRecorder.takePhoto(it,
                    onComplete = { file ->
                        Toast.makeText(this, "📷 后置照片已保存", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    })
            }
            
            if (frontCap != null || backCap != null) {
                return
            }
            
            null
        } else {
            cameraManager.getImageCapture()
        }
        
        if (imageCapture == null) {
            Toast.makeText(this, "摄像头未就绪", Toast.LENGTH_SHORT).show()
            return
        }

        videoRecorder.takePhoto(imageCapture,
            onComplete = { file ->
                Toast.makeText(this, "📷 照片已保存: ${file.name}", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            })
    }

    private fun updateRecordButton(recording: Boolean) {
        if (recording) {
            binding.btnRecordText.text = if (isDualMode) "⏹ 停止双录" else "⏹ 停止录制"
            binding.recordDot.setBackgroundResource(R.drawable.circle_recording_pulse)
            binding.recordingIndicator.visibility = View.VISIBLE
        } else {
            binding.btnRecordText.text = if (isDualMode) "▶️ 开始双录" else "⏺ 开始录制"
            binding.recordDot.setBackgroundResource(R.drawable.circle_record_idle)
            binding.recordingIndicator.visibility = View.GONE
        }
    }

    private fun updateStreamButton(streaming: Boolean) {
        binding.btnStreamText.text = if (streaming) "■ 停止推流" else "▶ 开始推流"
        binding.btnStreamContainer.setCardBackgroundColor(
            ContextCompat.getColorStateList(this, if (streaming) android.R.color.holo_red_dark else R.color.secondary)
        )
    }

    private fun startRecordService() {
        Intent(this, RecordService::class.java).also { intent ->
            intent.action = RecordService.ACTION_START_RECORDING
            startForegroundService(intent)
        }
    }

    private fun stopRecordService() {
        Intent(this, RecordService::class.java).also { intent ->
            intent.action = RecordService.ACTION_STOP_RECORDING
            startService(intent)
        }
    }

    private fun startStreamService(url: String) {
        Intent(this, StreamService::class.java).also { intent ->
            intent.action = StreamService.ACTION_START_STREAMING
            intent.putExtra("stream_url", url)
            startForegroundService(intent)
        }
    }

    private fun stopStreamService() {
        Intent(this, StreamService::class.java).also { intent ->
            intent.action = StreamService.ACTION_STOP_STREAMING
            startService(intent)
        }
    }

    private fun stopAll() {
        if (isRecording) stopRecording()
        if (isStreaming) stopStreaming()
        stopRecordingTimer()
        cameraManager.stopCamera()
        cameraManager.shutdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAll()
    }

    companion object {
        const val EXTRA_CAMERA_CONFIG = "camera_config"
    }
}
