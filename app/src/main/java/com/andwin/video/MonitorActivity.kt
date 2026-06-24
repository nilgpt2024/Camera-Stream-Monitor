package com.andwin.video

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.media.MediaPlayer
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.andwin.video.camera.CameraManager
import com.andwin.video.databinding.ActivityMonitorBinding
import com.andwin.video.model.CameraConfig
import com.andwin.video.recorder.VideoRecorder
import com.andwin.video.service.RecordService
import com.andwin.video.service.StreamService
import com.andwin.video.streamer.StreamPublisher
import com.andwin.video.streamer.DirectStreamServer
import com.andwin.video.streamer.TimeWatermarkView
import com.andwin.video.utils.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitorActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase!!, LocaleHelper.getLocale(newBase)))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: android.content.res.Configuration?) {
        super.applyOverrideConfiguration(LocaleHelper.applyOverrideConfiguration(overrideConfiguration))
    }

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

    // AI 检测相关（统一管理所有 MediaPipe 检测器）
    private lateinit var handDetector: HandDetector
    private lateinit var faceDetector: FaceDetector
    private lateinit var poseDetector: PoseDetector
    private var alertMediaPlayer: MediaPlayer? = null  // 提示音播放器
    private var isDetectionEnabled = false          // AI 检测总开关
    private var lastAlertTime: Long = 0             // 上次报警时间，防止频繁报警
    private val ALERT_INTERVAL_MS = 3000L           // 报警间隔：3秒
    private var currentImageAnalysis: ImageAnalysis? = null  // 统一的帧分析器

    // 各检测器的启用状态（从设置读取）
    private var enableHandDetection = true
    private var enableFaceDetection = false
    private var enablePoseDetection = false

    // 自动触发设置（从设置读取）
    private var autoRecordOnDetect = false       // 检测到目标自动录制
    private var autoStreamOnDetect = false       // 检测到目标自动推流
    private var autoStopDelaySec = 3L            // 消失后延迟停止（秒）
    private var detectTriggerCondition = "any"   // 触发条件
    private var handTriggerMode = "single"        // 手部触发模式: single=单手, both=双手

    // 自动触发状态
    private var isAutoTriggeredRecording = false  // 当前录制是否由自动触发启动
    private var isAutoTriggeredStreaming = false  // 当前推流是否由自动触发启动
    private var autoStopJob: kotlinx.coroutines.Job? = null  // 延迟停止的协程任务

    // 各检测器的最新检测结果（用于判断触发条件）
    @Volatile private var lastHandCount = 0
    @Volatile private var lastHandBothHands = false
    @Volatile private var lastFaceCount = 0
    @Volatile private var lastPoseCount = 0

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

        // 根据用户设置配置推流器视频参数
        streamPublisher.configureVideo(
            cameraConfig.resolution.width,
            cameraConfig.resolution.height,
            cameraConfig.fps,
            cameraConfig.bitrate
        )
        directStreamServer.configureVideo(
            cameraConfig.resolution.width,
            cameraConfig.resolution.height,
            cameraConfig.fps,
            cameraConfig.bitrate
        )

        // 从设置读取各检测器的启用状态
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        enableHandDetection = prefs.getBoolean("enable_hand_detection", true)
        enableFaceDetection = prefs.getBoolean("enable_face_detection", false)
        enablePoseDetection = prefs.getBoolean("enable_pose_detection", false)

        // 从设置读取自动触发选项
        autoRecordOnDetect = prefs.getBoolean("auto_record_on_detect", false)
        autoStreamOnDetect = prefs.getBoolean("auto_stream_on_detect", false)
        autoStopDelaySec = (prefs.getString("auto_stop_delay", "3")?.toLongOrNull() ?: 3L) * 1000L
        detectTriggerCondition = prefs.getString("detect_trigger_condition", "any") ?: "any"

        Log.i("MonitorActivity", "📋 AI 检测设置: 手部=$enableHandDetection, 人脸=$enableFaceDetection, " +
                "姿态=$enablePoseDetection")
        Log.i("MonitorActivity", "📋 自动触发: 录制=$autoRecordOnDetect, 推流=$autoStreamOnDetect, " +
                "延迟=${autoStopDelaySec / 1000}s, 条件=$detectTriggerCondition")

        // 初始化所有检测器
        handDetector = HandDetector(this)
        handDetector.onHandsDetected = { handCount: Int, isBothHands: Boolean, landmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>? ->
            handleHandDetectionResult(handCount, isBothHands, landmarks)
        }
        handDetector.onError = { error ->
            Log.e("MonitorActivity", "手部检测错误: $error")
            runOnUiThread { Toast.makeText(this, "手部检测错误: $error", Toast.LENGTH_SHORT).show() }
        }

        faceDetector = FaceDetector(this)
        faceDetector.onFaceDetected = { faceCount: Int, landmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>? ->
            handleFaceDetectionResult(faceCount, landmarks)
        }
        faceDetector.onError = { error ->
            Log.e("MonitorActivity", "人脸检测错误: $error")
            runOnUiThread { Toast.makeText(this, "人脸检测错误: $error", Toast.LENGTH_SHORT).show() }
        }

        poseDetector = PoseDetector(this)
        poseDetector.onPoseDetected = { poseCount: Int, landmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>? ->
            handlePoseDetectionResult(poseCount, landmarks)
        }
        poseDetector.onError = { error ->
            Log.e("MonitorActivity", "姿态检测错误: $error")
            runOnUiThread { Toast.makeText(this, "姿态检测错误: $error", Toast.LENGTH_SHORT).show() }
        }

        // 初始化提示音
        initAlertSound()

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
                Toast.makeText(this, getString(R.string.camera_initializing), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleRecording()
        }

        binding.btnStreamContainer.setOnClickListener {
            if (!isCameraReady) {
                Toast.makeText(this, getString(R.string.camera_initializing), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isStreaming) {
                stopStreaming()
            } else {
                showStreamDialog()
            }
        }

        binding.btnSwitchCamera.setOnClickListener {
            if (!isCameraReady) {
                Toast.makeText(this, getString(R.string.camera_initializing), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            switchCamera()
        }

        binding.btnCapture.setOnClickListener {
            if (!isCameraReady) {
                Toast.makeText(this, getString(R.string.camera_initializing), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            capturePhoto()
        }

        binding.btnDualCamera.setOnClickListener {
            if (!isCameraReady) {
                Toast.makeText(this, getString(R.string.camera_initializing), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleDualCameraMode()
        }

        binding.btnBack.setOnClickListener {
            stopAll()
            finish()
        }

        // AI 检测按钮（统一控制所有已启用的检测器）
        binding.btnHandDetection.setOnClickListener {
            toggleDetection()
        }
    }

    /**
     * 初始化提示音播放器（使用系统默认通知音）
     */
    private fun initAlertSound() {
        try {
            val notificationUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            alertMediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, notificationUri)
                isLooping = false
                prepare()
                Log.i("MonitorActivity", "✅ 提示音初始化成功")
            }
        } catch (e: Exception) {
            Log.e("MonitorActivity", "❌ 提示音初始化失败: ${e.message}")
        }
    }

    /**
     * 播放提示音报警
     */
    private fun playAlertSound() {
        // 防止频繁报警（间隔至少3秒）
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime < ALERT_INTERVAL_MS) {
            return
        }
        lastAlertTime = currentTime

        alertMediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                    player.prepare()
                }
                player.start()
                Log.i("MonitorActivity", "🔊 提示音报警")
            } catch (e: Exception) {
                Log.e("MonitorActivity", "播放提示音失败: ${e.message}")
            }
        }
    }

    /**
     * 处理手部检测结果
     */
    private fun handleHandDetectionResult(handCount: Int, isBothHands: Boolean,
                                          landmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>?) {
        lastHandCount = handCount
        lastHandBothHands = isBothHands
        runOnUiThread {
            // 更新骨骼叠加层
            if (enableHandDetection) {
                binding.detectionOverlay?.setHandLandmarks(landmarks)
            }
            updateHandDetectionUI(handCount, isBothHands)
            if (isDetectionEnabled && enableHandDetection && !isBothHands) {
                playAlertSound()
            }
            checkAutoTrigger()
        }
    }

    /**
     * 处理人脸检测结果
     */
    private fun handleFaceDetectionResult(faceCount: Int,
                                           landmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>?) {
        lastFaceCount = faceCount
        runOnUiThread {
            // 更新骨骼叠加层
            if (enableFaceDetection) {
                binding.detectionOverlay?.setFaceLandmarks(landmarks)
            }
            updateFaceDetectionUI(faceCount)
            checkAutoTrigger()
        }
    }

    /**
     * 处理姿态检测结果
     */
    private fun handlePoseDetectionResult(poseCount: Int,
                                           landmarks: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>?) {
        lastPoseCount = poseCount
        runOnUiThread {
            // 更新骨骼叠加层
            if (enablePoseDetection) {
                binding.detectionOverlay?.setPoseLandmarks(landmarks)
            }
            updatePoseDetectionUI(poseCount)
            checkAutoTrigger()
        }
    }

    /**
     * 更新双手检测 UI 状态
     */
    private fun updateHandDetectionUI(handCount: Int, isBothHands: Boolean) {
        when {
            handCount == 0 -> {
                binding.tvHandStatus.text = getString(R.string.hand_status_none)
                binding.tvHandStatus.setTextColor(Color.parseColor("#FF5252"))
                binding.handIndicator.setBackgroundResource(R.drawable.circle_status_error)
            }
            handCount == 1 -> {
                binding.tvHandStatus.text = getString(R.string.hand_status_one)
                binding.tvHandStatus.setTextColor(Color.parseColor("#FFA726"))
                binding.handIndicator.setBackgroundResource(R.drawable.circle_warning)
            }
            else -> {
                binding.tvHandStatus.text = getString(R.string.hand_status_both)
                binding.tvHandStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.handIndicator.setBackgroundResource(R.drawable.circle_recording_pulse)
            }
        }
    }

    /**
     * 更新人脸检测 UI 状态
     */
    private fun updateFaceDetectionUI(faceCount: Int) {
        when {
            faceCount == 0 -> {
                binding.tvFaceStatus.text = getString(R.string.face_status_none)
                binding.tvFaceStatus.setTextColor(Color.parseColor("#FF5252"))
                binding.faceIndicator.setBackgroundResource(R.drawable.circle_status_error)
            }
            faceCount == 1 -> {
                binding.tvFaceStatus.text = getString(R.string.face_status_one)
                binding.tvFaceStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.faceIndicator.setBackgroundResource(R.drawable.circle_recording_pulse)
            }
            else -> {
                binding.tvFaceStatus.text = getString(R.string.face_status_multiple, faceCount)
                binding.tvFaceStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.faceIndicator.setBackgroundResource(R.drawable.circle_recording_pulse)
            }
        }
    }

    /**
     * 更新姿态检测 UI 状态
     */
    private fun updatePoseDetectionUI(poseCount: Int) {
        if (poseCount > 0) {
            binding.tvPoseStatus.text = getString(R.string.pose_status_detected)
            binding.tvPoseStatus.setTextColor(Color.parseColor("#4CAF50"))
            binding.poseIndicator.setBackgroundResource(R.drawable.circle_recording_pulse)
        } else {
            binding.tvPoseStatus.text = getString(R.string.pose_status_none)
            binding.tvPoseStatus.setTextColor(Color.parseColor("#FF5252"))
            binding.poseIndicator.setBackgroundResource(R.drawable.circle_status_error)
        }
    }

    /**
     * 切换 AI 检测总开关（启动/停止所有已启用的检测器）
     * 启动前会自动检查模型文件，缺失则提示下载
     */
    private fun toggleDetection() {
        if (!isCameraReady && !isDetectionEnabled) {
            Toast.makeText(this, getString(R.string.camera_initializing), Toast.LENGTH_SHORT).show()
            return
        }

        isDetectionEnabled = !isDetectionEnabled

        if (isDetectionEnabled) {
            // 先检查已启用检测器的模型是否齐全
            checkModelsAndStart()
        } else {
            stopAllDetection()
        }

        updateDetectionButton(isDetectionEnabled)
    }

    /**
     * 检查模型文件，缺失则弹出下载对话框，齐全则直接启动检测
     */
    private fun checkModelsAndStart() {
        // 收集所有已启用的检测器需要的模型
        val requiredModels = mutableListOf<ModelDownloadManager.ModelInfo>()
        if (enableHandDetection) requiredModels.add(ModelDownloadManager.ModelInfo.HAND)
        if (enableFaceDetection) requiredModels.add(ModelDownloadManager.ModelInfo.FACE)
        if (enablePoseDetection) requiredModels.add(ModelDownloadManager.ModelInfo.POSE)

        // 检查哪些模型缺失
        val missingModels = requiredModels.filter { model ->
            !ModelDownloadManager.isModelAvailable(this, model.fileName)
        }

        if (missingModels.isEmpty()) {
            // 所有模型齐全，直接启动
            startAllDetection()
        } else {
            // 有缺失的模型，弹出下载对话框
            Log.i("MonitorActivity", "📦 缺少 ${missingModels.size} 个 AI 模型: ${missingModels.joinToString { it.fileName }}")
            ModelDownloadManager.showDownloadDialog(
                this,
                missingModels
            ) {
                // 下载完成后重新检查并启动
                val stillMissing = ModelDownloadManager.getMissingModels(this)
                    .filter { m -> requiredModels.any { it.fileName == m.fileName } }
                if (stillMissing.isEmpty()) {
                    startAllDetection()
                } else {
                    isDetectionEnabled = false
                    updateDetectionButton(false)
                    Toast.makeText(this,
                        "${getString(R.string.detection_init_failed)} (${stillMissing.size} models missing)",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 启动所有已启用的检测器
     * 使用统一的 ImageAnalysis 分发帧给各检测器
     */
    private fun startAllDetection() {
        lifecycleScope.launch {
            try {
                // 初始化并启动已启用的检测器
                val inits = mutableListOf<Pair<String, Boolean>>()

                if (enableHandDetection) {
                    inits.add("手部" to handDetector.initialize())
                    handDetector.start()
                }
                if (enableFaceDetection) {
                    inits.add("人脸" to faceDetector.initialize())
                    faceDetector.start()
                }
                if (enablePoseDetection) {
                    inits.add("姿态" to poseDetector.initialize())
                    poseDetector.start()
                }

                // 检查是否有至少一个初始化成功
                val anySuccess = inits.any { it.second }
                if (!anySuccess) {
                    isDetectionEnabled = false
                    updateDetectionButton(false)
                    val failedNames = inits.filter { !it.second }.joinToString(", ") { it.first }
                    Toast.makeText(this@MonitorActivity,
                        "${getString(R.string.detection_init_failed)} [$failedNames]", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // 创建统一的 ImageAnalysis 并绑定到摄像头
                withContext(Dispatchers.Main) {
                    currentImageAnalysis = createUnifiedImageAnalysis()
                    cameraManager.bindAnalysis(currentImageAnalysis!!)
                }

                // 显示已启用检测器的状态面板
                if (enableHandDetection) binding.handDetectionPanel.visibility = View.VISIBLE
                if (enableFaceDetection) binding.faceDetectionPanel.visibility = View.VISIBLE
                if (enablePoseDetection) binding.poseDetectionPanel.visibility = View.VISIBLE

                // 显示骨骼叠加层
                binding.detectionOverlay?.visibility = View.VISIBLE

                Toast.makeText(this@MonitorActivity, getString(R.string.detection_started), Toast.LENGTH_SHORT).show()
                Log.i("MonitorActivity", "✅ AI 检测已启动 (已启用: ${inits.filter { it.second }.joinToString(", ") { it.first }})")
            } catch (e: Exception) {
                isDetectionEnabled = false
                updateDetectionButton(false)
                Log.e("MonitorActivity", "启动 AI 检测失败", e)
                Toast.makeText(this@MonitorActivity,
                    "${getString(R.string.detection_start_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 停止所有检测器
     */
    private fun stopAllDetection() {
        try {
            if (enableHandDetection) handDetector.stop()
            if (enableFaceDetection) faceDetector.stop()
            if (enablePoseDetection) poseDetector.stop()

            cameraManager.unbindAnalysis()
            currentImageAnalysis = null

            // 隐藏所有检测面板
            binding.handDetectionPanel.visibility = View.GONE
            binding.faceDetectionPanel.visibility = View.GONE
            binding.poseDetectionPanel.visibility = View.GONE

            // 清理并隐藏骨骼叠加层
            binding.detectionOverlay?.clear()
            binding.detectionOverlay?.visibility = View.GONE

            Toast.makeText(this, getString(R.string.detection_stopped), Toast.LENGTH_SHORT).show()
            Log.i("MonitorActivity", "⏹ AI 检测已停止")
        } catch (e: Exception) {
            Log.e("MonitorActivity", "停止 AI 检测失败", e)
        }
    }

    /**
     * 创建统一的 ImageAnalysis 用例
     * 将帧数据分发给所有已启动的检测器
     */
    private fun createUnifiedImageAnalysis(): ImageAnalysis {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(executor) { imageProxy ->
                    if (!isDetectionEnabled) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    try {
                        // 将 ImageProxy 转为 Bitmap
                        val bitmapBuffer = Bitmap.createBitmap(
                            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
                        )
                        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
                        imageProxy.close()

                        // 应用旋转和镜像变换
                        val matrix = Matrix().apply {
                            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                            postScale(-1f, 1f, imageProxy.width.toFloat() / 2, imageProxy.height.toFloat() / 2)
                        }
                        val rotatedBitmap = Bitmap.createBitmap(
                            bitmapBuffer, 0, 0,
                            bitmapBuffer.width, bitmapBuffer.height,
                            matrix, true
                        ).also { bitmapBuffer.recycle() }

                        val frameTime = System.nanoTime() / 1_000_000

                        // 分发给所有已启用的检测器
                        if (enableHandDetection && handDetector.isActive()) {
                            handDetector.processFrame(rotatedBitmap, frameTime)
                        }
                        if (enableFaceDetection && faceDetector.isActive()) {
                            faceDetector.processFrame(rotatedBitmap, frameTime)
                        }
                        if (enablePoseDetection && poseDetector.isActive()) {
                            poseDetector.processFrame(rotatedBitmap, frameTime)
                        }
                    } catch (e: Exception) {
                        Log.e("MonitorActivity", "❌ 统一帧处理异常: ${e.message}", e)
                    }
                }
            }
    }

    /**
     * 更新 AI 检测按钮状态
     */
    private fun updateDetectionButton(enabled: Boolean) {
        if (enabled) {
            binding.btnHandDetection.setCardBackgroundColor(ContextCompat.getColorStateList(this, R.color.primary))
        } else {
            binding.btnHandDetection.setCardBackgroundColor(ContextCompat.getColorStateList(this, R.color.glass_background))
        }
    }

    // ==================== 自动触发：检测 → 录制/推流 ====================

    /**
     * 根据最新检测结果判断是否需要自动启动/停止录制或推流
     *
     * 触发流程：
     * 1. 检查是否满足触发条件
     * 2. 满足且未运行 → 自动启动录制/推流
     * 3. 不满足且正在自动触发的任务中 → 延迟停止（防抖动）
     * 4. 延迟期间重新检测到目标 → 取消延迟停止
     */
    private fun checkAutoTrigger() {
        if (!isDetectionEnabled) return
        if (!autoRecordOnDetect && !autoStreamOnDetect) return

        val targetDetected = isTriggerConditionMet()

        Log.d("MonitorActivity", "🎯 自动触发检查: 手部=$lastHandCount(双手=$lastHandBothHands), " +
                "人脸=$lastFaceCount, 姿态=$lastPoseCount → 目标检测到=$targetDetected")

        if (targetDetected) {
            // 检测到目标 → 取消待执行的延迟停止（目标重现了）
            autoStopJob?.cancel()
            autoStopJob = null
            // 检测到目标 → 自动启动
            if (autoRecordOnDetect && !isRecording) {
                Log.i("MonitorActivity", "🎬 自动触发: 启动录制")
                isAutoTriggeredRecording = true
                startRecording()
                Toast.makeText(this, getString(R.string.auto_record_started_by_detect), Toast.LENGTH_SHORT).show()
            }
            if (autoStreamOnDetect && !isStreaming) {
                Log.i("MonitorActivity", "🚀 自动触发: 启动推流（直连模式）")
                isAutoTriggeredStreaming = true
                startDirectStream()
            }
        } else {
            // 未检测到目标 → 如果有自动触发的任务在运行，则延迟停止
            if ((isAutoTriggeredRecording && isRecording) || (isAutoTriggeredStreaming && isStreaming)) {
                // 只有当没有活跃的延迟任务时才创建新任务（避免每帧重复创建）
                if (autoStopJob == null || !autoStopJob!!.isActive) {
                    Log.i("MonitorActivity", "⏳ 目标消失，${autoStopDelaySec / 1000}秒后自动停止...")
                    autoStopJob = lifecycleScope.launch {
                        delay(autoStopDelaySec)
                        // 再次确认目标仍然未出现（防止延迟期间又出现了）
                        if (!isTriggerConditionMet()) {
                            runOnUiThread {
                                stopAutoTriggeredActions()
                            }
                        } else {
                            Log.i("MonitorActivity", "↩️ 延迟期间目标重现，取消停止")
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断当前检测结果是否满足触发条件
     *
     * @return true 表示检测到了目标，应该触发自动动作
     */
    private fun isTriggerConditionMet(): Boolean {
        return when (detectTriggerCondition) {
            "any" -> {
                // 任意已启用的检测器有结果即触发（手部需满足手部触发模式）
                val handMet = if (enableHandDetection) {
                    when (handTriggerMode) {
                        "single" -> lastHandCount >= 1
                        "both"   -> lastHandBothHands
                        else     -> false
                    }
                } else false
                val faceMet = enableFaceDetection && lastFaceCount > 0
                val poseMet = enablePoseDetection && lastPoseCount > 0
                handMet || faceMet || poseMet
            }
            "hand" -> {
                // 手部检测（按手部触发模式）
                when (handTriggerMode) {
                    "single" -> enableHandDetection && lastHandCount >= 1
                    "both"   -> enableHandDetection && lastHandBothHands
                    else     -> false
                }
            }
            "hand_both" -> {
                // 兼容旧值：双手才触发
                enableHandDetection && lastHandBothHands
            }
            "face_any" -> {
                // 人脸检测到人脸
                enableFaceDetection && lastFaceCount > 0
            }
            "pose_any" -> {
                // 姿态检测到人体
                enablePoseDetection && lastPoseCount > 0
            }
            "all" -> {
                // 所有已启用的检测器都有结果（手部需满足手部触发模式）
                var allMet = true
                if (enableHandDetection) {
                    when (handTriggerMode) {
                        "single" -> if (lastHandCount == 0) allMet = false
                        "both"   -> if (!lastHandBothHands) allMet = false
                        else     -> allMet = false
                    }
                }
                if (enableFaceDetection && lastFaceCount == 0) allMet = false
                if (enablePoseDetection && lastPoseCount == 0) allMet = false
                allMet
            }
            else -> false
        }
    }

    /**
     * 停止所有由自动触发的录制/推流操作
     */
    private fun stopAutoTriggeredActions() {
        var stoppedSomething = false

        if (isAutoTriggeredRecording && isRecording) {
            Log.i("MonitorActivity", "⏹ 自动触发停止: 录制")
            stopRecording()
            isAutoTriggeredRecording = false
            stoppedSomething = true
        }

        if (isAutoTriggeredStreaming && isStreaming) {
            Log.i("MonitorActivity", "⏹ 自动触发停止: 推流")
            stopStreaming()
            isAutoTriggeredStreaming = false
            stoppedSomething = true
        }

        if (stoppedSomething) {
            Toast.makeText(this, getString(R.string.auto_stopped_no_target), Toast.LENGTH_SHORT).show()
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
                        binding.tvStatus.text = getString(R.string.ready)
                        binding.tvResolutionInfo.text = "${cameraConfig.resolution.width}×${cameraConfig.resolution.height} @ ${cameraConfig.fps}fps"
                        
                        val hasMultipleCameras = cameraManager.hasMultipleCameras()
                        if (hasMultipleCameras) {
                            binding.btnDualCamera.visibility = View.VISIBLE
                            binding.tvHint.text = getString(R.string.hint_record_with_dual)
                        } else {
                            binding.tvHint.text = getString(R.string.hint_single_camera)
                            binding.btnDualCamera.alpha = 0.3f
                            binding.btnDualCamera.isEnabled = false
                        }
                    }
                } else {
                    isCameraReady = false
                    updateUIForLoadingState(false)
                    runOnUiThread {
                        binding.tvStatus.text = getString(R.string.init_failed)
                        Toast.makeText(this@MonitorActivity, getString(R.string.camera_init_failed), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                isCameraReady = false
                updateUIForLoadingState(false)
                runOnUiThread {
                    binding.tvStatus.text = String.format(getString(R.string.camera_error), e.message ?: "")
                    Toast.makeText(this@MonitorActivity, String.format(getString(R.string.camera_error), e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startPreview() {
        val resolution = android.util.Size(cameraConfig.resolution.width, cameraConfig.resolution.height)

        // 根据分辨率设置录制质量档位
        cameraManager.recordingQuality = when (cameraConfig.resolution) {
            com.andwin.video.model.Resolution.UHD_4K -> "uhd"
            com.andwin.video.model.Resolution.FULL_HD_1080P -> "fhd"
            com.andwin.video.model.Resolution.HD_720P -> "hd"
            com.andwin.video.model.Resolution.SD_480P -> "sd"
        }

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
                cameraManager.startCamera(binding.previewView, cameraConfig.cameraId, resolution, cameraConfig.fps)
                Toast.makeText(this, getString(R.string.no_dual_camera_support), Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraManager.startCamera(binding.previewView, cameraConfig.cameraId, resolution, cameraConfig.fps)
        }
    }

    /**
     * 切换双摄模式
     */
    private fun toggleDualCameraMode() {
        if (isRecording || isStreaming) {
            Toast.makeText(this, getString(R.string.stop_before_mode_switch), Toast.LENGTH_SHORT).show()
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
                    binding.tvResolutionInfo.text = getString(R.string.dual_mode_info)
                    Toast.makeText(this@MonitorActivity, getString(R.string.dual_mode_enabled), Toast.LENGTH_SHORT).show()
                } else {
                    binding.btnDualCamera.backgroundTintList = ContextCompat.getColorStateList(
                        this@MonitorActivity, 
                        R.color.primary
                    )
                    binding.tvResolutionInfo.text = "${cameraConfig.resolution.width}×${cameraConfig.resolution.height} @ ${cameraConfig.fps}fps"
                    Toast.makeText(this@MonitorActivity, getString(R.string.dual_mode_disabled), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                isDualMode = !isDualMode
                isCameraReady = false
                updateUIForLoadingState(false)
                Toast.makeText(this@MonitorActivity, String.format(getString(R.string.mode_switch_failed), e.message ?: ""), Toast.LENGTH_SHORT).show()
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
                binding.tvStatus.text = getString(R.string.initializing_camera)
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
            // 用户手动停止 → 重置自动触发标志
            isAutoTriggeredRecording = false
            stopRecording()
        } else {
            startRecording()
        }
    }

    /**
     * 开始录制 - 优先使用 VideoCapture（真正录像），回退到 ImageCapture（拍照）
     */
    private fun startRecording() {
        // 启动前台模式，使摄像头不受 Activity 生命周期影响（支持熄屏录制）
        cameraManager.startForegroundMode()

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
                        val msg = String.format(getString(R.string.video_saved_with_info), outputFile.name, sizeKB)
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

                        Log.i("MonitorActivity", "✅ 视频录制完成!")

                        stopRecordingTimer()
                        updateRecordButton(false)

                        // 录制完成后停止前台模式，恢复正常生命周期控制
                        cameraManager.stopForegroundMode()

                        // 录制完成后自动清理旧录像
                        performAutoDelete()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Log.e("MonitorActivity", "❌ 视频录制失败: $error")
                        Toast.makeText(this, String.format(getString(R.string.record_failed), error), Toast.LENGTH_LONG).show()

                        // 停止前台模式
                        cameraManager.stopForegroundMode()

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

            // VideoCapture 启动失败时也停止前台模式
            cameraManager.stopForegroundMode()
        }

        // 策略2: 回退到 ImageCapture 拍照模式
        Log.w("MonitorActivity", "VideoCapture 不可用，使用拍照模式")
        startPhotoMode()
    }

    /**
     * 拍照模式（备用方案）
     */
    private fun startPhotoMode() {
        // 启动前台模式（如果尚未启动）
        cameraManager.startForegroundMode()

        val imageCapture = if (isDualMode && cameraManager.isDualCameraActive()) {
            cameraManager.getFrontImageCapture() ?: cameraManager.getImageCapture()
        } else {
            cameraManager.getImageCapture()
        }

        if (imageCapture == null) {
            Toast.makeText(this, getString(R.string.camera_not_ready), Toast.LENGTH_SHORT).show()
            cameraManager.stopForegroundMode()
            return
        }

        val success = videoRecorder.startRecording(
            imageCapture,
            onComplete = { outputFile ->
                runOnUiThread {
                    val sizeKB = outputFile.length() / 1024
                    val msg = String.format(getString(R.string.photo_saved_info), outputFile.name, sizeKB)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

                    Log.i("MonitorActivity", "✅ 拍照完成: ${outputFile.absolutePath}")

                    stopRecordingTimer()
                    updateRecordButton(false)

                    // 停止前台模式
                    cameraManager.stopForegroundMode()

                    // 录制完成后自动清理旧录像
                    performAutoDelete()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "❌ $error", Toast.LENGTH_LONG).show()
                    stopRecordingTimer()
                    updateRecordButton(false)
                    // 停止前台模式
                    cameraManager.stopForegroundMode()
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
        } else {
            // 启动失败时停止前台模式
            cameraManager.stopForegroundMode()
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
            // 停止前台模式，恢复正常生命周期控制
            cameraManager.stopForegroundMode()
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

    /**
     * 执行自动清理旧录像
     */
    private fun performAutoDelete() {
        val deletedCount = videoRecorder.autoDeleteOldRecordings()
        if (deletedCount > 0) {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            val days = prefs.getString("auto_delete_days", "0")?.toIntOrNull() ?: 0
            Toast.makeText(
                this,
                getString(R.string.auto_delete_completed, deletedCount, days),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ==================== 后台录制支持 ====================

    override fun onPause() {
        super.onPause()
        // 录制中切到后台时，保持摄像头不释放
        if (isRecording) {
            Log.i("MonitorActivity", "📱 录制中进入后台，保持录制继续...")
        }
    }

    override fun onResume() {
        super.onResume()
        // 从后台恢复时刷新 UI 状态
        if (isRecording) {
            Log.i("MonitorActivity", "📱 从后台恢复，录制状态: isRecording=$isRecording")
            updateRecordButton(true)
        }
    }

    private fun showStreamDialog() {
        val options = arrayOf(
            getString(R.string.direct_mode_option),
            getString(R.string.rtmp_mode_option)
        )
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_stream_method))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startDirectStream()
                    1 -> showRtmpInputDialog()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showRtmpInputDialog() {
        // 读取默认的 RTMP 地址
        val sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val defaultRtmpUrl = sharedPreferences.getString("default_rtmp_url", "") ?: ""
        
        val editText = EditText(this).apply {
            hint = getString(R.string.rtmp_url_hint)
            // 优先使用 cameraConfig 中的地址，如果没有就用默认地址
            setText(if (cameraConfig.streamUrl.isNotEmpty()) cameraConfig.streamUrl else defaultRtmpUrl)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rtmp_stream_settings))
            .setMessage(getString(R.string.rtmp_input_message))
            .setView(editText)
            .setPositiveButton(getString(R.string.start_stream)) { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotEmpty()) {
                    startStreaming(url)
                } else {
                    Toast.makeText(this, getString(R.string.enter_rtmp_url), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private var streamJob: kotlinx.coroutines.Job? = null

    private fun startDirectStream() {
        if (isStreaming) {
            Toast.makeText(this, getString(R.string.already_streaming_stop_first), Toast.LENGTH_SHORT).show()
            return
        }

        if (isRecording) {
            Toast.makeText(this, getString(R.string.stop_record_before_stream), Toast.LENGTH_SHORT).show()
            return
        }

        isDirectMode = true

        directStreamServer.logCallback = { msg ->
            Log.i("StreamServer", msg)
        }

        directStreamServer.clientConnectedCallback = { clientInfo ->
            runOnUiThread {
                Log.i("MonitorActivity", "客户端已连接: $clientInfo")
                binding.tvStatus.text = getString(R.string.client_connected)
            }
        }

        directStreamServer.clientDisconnectedCallback = {
            runOnUiThread {
                Log.i("MonitorActivity", "客户端断开连接")
                binding.tvStatus.text = getString(R.string.waiting_connection)
            }
        }

        directStreamServer.errorCallback = { error ->
            runOnUiThread {
                isStreaming = false
                updateStreamButton(false)
                binding.streamingIndicator.visibility = View.GONE
                binding.tvStatus.text = getString(R.string.error_status)

                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.stream_failed_title))
                    .setMessage(String.format(getString(R.string.stream_error_details), error))
                    .setPositiveButton(getString(R.string.view_log)) { _, _ ->
                        showLogInfo()
                    }
                    .setNegativeButton(getString(R.string.close), null)
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
                binding.tvStatus.text = getString(R.string.step_switch_mode)
                switchToStreamPreviewNonBlocking()

                // Phase 2: 等待 Surface 创建（非阻塞）
                binding.tvStatus.text = getString(R.string.step_wait_surface)
                val surfaceReady = directStreamServer.waitForSurface(10000)

                if (!surfaceReady) {
                    handleError(getString(R.string.surface_timeout_details))
                    return@launch
                }

                logSurfaceSize("Surface已就绪")

                // Phase 3: 初始化编码器并启动预览
                binding.tvStatus.text = getString(R.string.step_init_encoder)
                val success = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    directStreamServer.initialize()
                }

                if (!success) {
                    handleError(getString(R.string.encoder_init_failed))
                    return@launch
                }

                // Phase 4: 启动预览和服务器
                binding.tvStatus.text = getString(R.string.step_start_preview_server)
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
                    binding.tvStatus.text = getString(R.string.direct_service_started)
                    binding.statusDot.setBackgroundResource(R.drawable.circle_recording_pulse)

                    val url = directStreamServer.getRtspUrl()

                    AlertDialog.Builder(this@MonitorActivity)
                        .setTitle(getString(R.string.stream_success_title))
                        .setMessage(String.format(getString(R.string.rtsp_started_message), url))
                        .setPositiveButton(getString(R.string.copy_address)) { _, _ ->
                            copyToClipboard(url)
                            Toast.makeText(this@MonitorActivity, getString(R.string.copied_success_emoji), Toast.LENGTH_SHORT).show()
                        }
                        .setNeutralButton(getString(R.string.generate_webpage)) { _, _ ->
                            generateWebPlayerPage(url)
                        }
                        .setNegativeButton(getString(R.string.close), null)
                        .show()

                    Log.i("MonitorActivity", "========================================")
                    Log.i("MonitorActivity", "🎯 直连模式已启动！")
                    Log.i("MonitorActivity", "   RTSP URL: $url")
                    Log.i("MonitorActivity", "========================================")
                } else {
                    handleError(getString(R.string.rtsp_start_failed))
                }
            } catch (e: Exception) {
                Log.e("MonitorActivity", "Direct stream error", e)
                e.printStackTrace()
                handleError(String.format(getString(R.string.exception_error), e.javaClass.simpleName, e.message ?: ""))
            }
        }
    }
    
    private fun handleError(message: String) {
        runOnUiThread {
            isStreaming = false
            updateStreamButton(false)
            binding.streamingIndicator.visibility = View.GONE
            binding.tvStatus.text = getString(R.string.failed_status)
            
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.stream_failed_title))
                .setMessage(message)
                .setPositiveButton(getString(R.string.retry)) { _, _ ->
                    startDirectStream()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
                
            switchToCameraXPreview()
        }
    }
    
    private fun logSurfaceSize(context: String) {
        Log.i("MonitorActivity", "[$context] SurfaceView 尺寸: ${binding.surfaceView.width}x${binding.surfaceView.height}, 可见: ${binding.surfaceView.visibility == View.VISIBLE}")
    }

    private fun showLogInfo() {
        val logMsg = """
            ${getString(R.string.debug_stream_info)}:

            ${getString(R.string.debug_phone_ip)}: ${directStreamServer.getLocalIpAddress()}
            ${getString(R.string.debug_rtsp_url)}: ${directStreamServer.getRtspUrl()}

            ${getString(R.string.debug_logcat_hint)}

            ${getString(R.string.debug_common_issues)}:
            1. ${getString(R.string.debug_issue_same_wifi)}
            2. ${getString(R.string.debug_issue_firewall)}
            3. ${getString(R.string.debug_issue_permissions)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.debug_info_title))
            .setMessage(logMsg)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }
    
    private fun showDirectStreamInfo(rtspUrl: String) {
        val ip = directStreamServer.getLocalIpAddress()
        
        val message = String.format(getString(R.string.direct_stream_info), ip, rtspUrl)
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.direct_mode_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.copy_address)) { _, _ ->
                copyToClipboard(rtspUrl)
                Toast.makeText(this, getString(R.string.copied_success_emoji), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(getString(R.string.generate_web_player)) { _, _ ->
                generateWebPlayerPage(rtspUrl)
            }
            .setNegativeButton(getString(R.string.got_it), null)
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
    <title>${getString(R.string.webplayer_title)}</title>
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
        <h2>${getString(R.string.webplayer_heading)}</h2>
        <video id="player" controls autoplay playsinline></video>
        <div class="info">
            <p><strong>${getString(R.string.webplayer_status)}:</strong> <span id="status">${getString(R.string.webplayer_waiting)}</span></p>
            <p class="url" id="urlDisplay"></p>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
    <script>
        const rtspUrl = '$rtspUrl';
        document.getElementById('urlDisplay').textContent = '${getString(R.string.webplayer_source_url)}' + rtspUrl;
        document.getElementById('status').textContent = '${getString(R.string.webplayer_no_rtsp_support)}';

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
            
            Toast.makeText(this, String.format(getString(R.string.webpage_saved), file.name), Toast.LENGTH_LONG).show()
            Log.i("MonitorActivity", "HTML player saved to: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e("MonitorActivity", "Generate HTML failed", e)
            Toast.makeText(this, String.format(getString(R.string.webpage_save_failed), e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startStreaming(url: String) {
        if (isStreaming) {
            Toast.makeText(this, getString(R.string.already_streaming_stop_first), Toast.LENGTH_SHORT).show()
            return
        }

        if (isRecording) {
            Toast.makeText(this, getString(R.string.stop_record_before_stream), Toast.LENGTH_SHORT).show()
            return
        }

        streamPublisher.onConnectionFailed = { reason ->
            runOnUiThread {
                isStreaming = false
                updateStreamButton(false)
                binding.streamingIndicator.visibility = View.GONE
                binding.tvStatus.text = getString(R.string.stream_failed_status)
                Toast.makeText(this, String.format(getString(R.string.connection_failed), reason), Toast.LENGTH_LONG).show()
                switchToCameraXPreview()
            }
        }

        streamPublisher.onConnectionStarted = {
            runOnUiThread {
                binding.tvStatus.text = getString(R.string.connecting_to_server)
                Toast.makeText(this, getString(R.string.establishing_connection), Toast.LENGTH_SHORT).show()
            }
        }

        streamPublisher.onConnectionSuccess = {
            runOnUiThread {
                isStreaming = true
                updateStreamButton(true)
                binding.streamingIndicator.visibility = View.VISIBLE
                binding.tvStatus.text = getString(R.string.streaming_status)
                binding.statusDot.setBackgroundResource(R.drawable.circle_recording_pulse)
                Toast.makeText(this, getString(R.string.stream_started_success), Toast.LENGTH_LONG).show()
            }
        }

        streamPublisher.onDisconnect = {
            runOnUiThread {
                isStreaming = false
                updateStreamButton(false)
                binding.streamingIndicator.visibility = View.GONE
                binding.tvStatus.text = getString(R.string.disconnected)
                binding.statusDot.setBackgroundResource(R.drawable.circle_status_ready)
                Toast.makeText(this, getString(R.string.stream_stopped), Toast.LENGTH_SHORT).show()
                switchToCameraXPreview()
            }
        }

        streamPublisher.onError = { error ->
            runOnUiThread {
                Toast.makeText(this, String.format(getString(R.string.stream_error), error), Toast.LENGTH_LONG).show()
            }
        }

        // 关键修复：使用协程，不阻塞主线程
        streamJob = lifecycleScope.launch {
            try {
                binding.tvStatus.text = getString(R.string.step_rtmp_1)
                switchToStreamPreviewNonBlocking()

                binding.tvStatus.text = getString(R.string.step_rtmp_2)
                val surfaceReady = directStreamServer.waitForSurface(10000)
                if (!surfaceReady) {
                    Toast.makeText(this@MonitorActivity, getString(R.string.surface_timeout), Toast.LENGTH_SHORT).show()
                    switchToCameraXPreview()
                    return@launch
                }

                binding.tvStatus.text = getString(R.string.step_rtmp_3)
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
                    binding.tvStatus.text = getString(R.string.streaming_status)
                    binding.statusDot.setBackgroundResource(R.drawable.circle_recording_pulse)

                    startStreamService(url)
                    Log.i("MonitorActivity", "========================================")
                    Log.i("MonitorActivity", "🚀 RTMP 推流已启动!")
                    Log.i("MonitorActivity", "   URL: $url")
                    Log.i("MonitorActivity", "========================================")

                    Toast.makeText(this@MonitorActivity, String.format(getString(R.string.rtmp_stream_success), url), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MonitorActivity, getString(R.string.rtmp_start_failed), Toast.LENGTH_SHORT).show()
                    switchToCameraXPreview()
                }
            } catch (e: Exception) {
                Log.e("MonitorActivity", "Start streaming error", e)
                Toast.makeText(this@MonitorActivity, String.format(getString(R.string.stream_exception), e.message ?: ""), Toast.LENGTH_LONG).show()
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
            binding.tvStatus.text = getString(R.string.releasing_resources)
            if (isDirectMode) {
                directStreamServer.release()
                directStreamServer = DirectStreamServer(this, binding.surfaceView)
                directStreamServer.setTimeWatermarkView(binding.timeWatermark)
                directStreamServer.configureVideo(
                    cameraConfig.resolution.width, cameraConfig.resolution.height,
                    cameraConfig.fps, cameraConfig.bitrate
                )
            } else {
                streamPublisher.release()
                streamPublisher = StreamPublisher(this, binding.surfaceView)
                streamPublisher.setTimeWatermarkView(binding.timeWatermark)
                streamPublisher.configureVideo(
                    cameraConfig.resolution.width, cameraConfig.resolution.height,
                    cameraConfig.fps, cameraConfig.bitrate
                )
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
                binding.tvStatus.text = getString(R.string.reinitializing_camerax)
                val success = cameraManager.reinitialize()
                
                if (success) {
                    // 重启 CameraX 预览
                    if (isDualMode) {
                        binding.previewViewBack.visibility = View.VISIBLE
                    }
                    startPreview()
                    Log.d("MonitorActivity", "[4/4] CameraX已重启")
                    
                    Log.i("MonitorActivity", "═══ 切换回CameraX完成 ═══")
                    binding.tvStatus.text = getString(R.string.ready)
                } else {
                    Log.e("MonitorActivity", "CameraProvider 重新初始化失败")
                    runOnUiThread {
                        Toast.makeText(this@MonitorActivity, getString(R.string.camera_reinit_failed), Toast.LENGTH_SHORT).show()
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
            binding.tvStatus.text = getString(R.string.ready)
            binding.statusDot.setBackgroundResource(R.drawable.circle_status_ready)
            
            stopStreamService()
            
            // 恢复 CameraX 预览
            switchToCameraXPreview()
            
            Toast.makeText(this, getString(R.string.stream_stopped), Toast.LENGTH_SHORT).show()
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
                if (directStreamServer.isUsingFrontCamera()) getString(R.string.front_camera) else getString(R.string.rear_camera)
            } else {
                getString(R.string.camera_switched)
            }
            Toast.makeText(this, String.format(getString(R.string.switched_to_camera), currentCamera), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@MonitorActivity, getString(R.string.switch_camera_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 拍照（带自动重试，应对 VideoEncoder 状态转换导致的 Session 临时不可用）
     */
    private fun capturePhoto() {
        capturePhotoWithRetry(retryCount = 0)
    }

    private fun capturePhotoWithRetry(retryCount: Int) {
        val imageCapture = if (isDualMode && cameraManager.isDualCameraActive()) {
            val frontCap = cameraManager.getFrontImageCapture()
            val backCap = cameraManager.getBackImageCapture()
            
            frontCap?.let { 
                videoRecorder.takePhoto(it,
                    onComplete = { file -> 
                        Toast.makeText(this, getString(R.string.front_photo_saved), Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    })
            }
            
            backCap?.let {
                videoRecorder.takePhoto(it,
                    onComplete = { file ->
                        Toast.makeText(this, getString(R.string.rear_photo_saved), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.camera_not_ready), Toast.LENGTH_SHORT).show()
            return
        }

        videoRecorder.takePhoto(imageCapture,
            onComplete = { file ->
                Toast.makeText(this, String.format(getString(R.string.photo_saved_with_name), file.name), Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                // 当 Camera Session 繁忙时自动重试（最多3次，间隔500ms）
                if (retryCount < 3 && error.contains("忙碌", ignoreCase = true)) {
                    lifecycleScope.launch {
                        delay(500L)
                        capturePhotoWithRetry(retryCount + 1)
                    }
                } else {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun updateRecordButton(recording: Boolean) {
        if (recording) {
            binding.btnRecordText.text = if (isDualMode) getString(R.string.stop_dual_recording) else getString(R.string.stop_recording_btn)
            binding.recordDot.setBackgroundResource(R.drawable.circle_recording_pulse)
            binding.recordingIndicator.visibility = View.VISIBLE
        } else {
            binding.btnRecordText.text = if (isDualMode) getString(R.string.start_dual_recording) else getString(R.string.start_recording_btn)
            binding.recordDot.setBackgroundResource(R.drawable.circle_record_idle)
            binding.recordingIndicator.visibility = View.GONE
        }
    }

    private fun updateStreamButton(streaming: Boolean) {
        binding.btnStreamText.text = if (streaming) getString(R.string.stop_streaming_btn) else getString(R.string.start_streaming_btn)
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
        // 取消自动触发的延迟停止任务
        autoStopJob?.cancel()
        autoStopJob = null
        isAutoTriggeredRecording = false
        isAutoTriggeredStreaming = false

        if (isRecording) stopRecording()
        if (isStreaming) stopStreaming()
        stopRecordingTimer()

        // 停止所有 AI 检测
        if (isDetectionEnabled) {
            stopAllDetection()
        }

        cameraManager.stopCamera()
        cameraManager.shutdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAll()

        // 释放提示音播放器资源
        alertMediaPlayer?.release()
        alertMediaPlayer = null

        // 释放所有检测器资源
        handDetector.release()
        faceDetector.release()
        poseDetector.release()
    }

    companion object {
        const val EXTRA_CAMERA_CONFIG = "camera_config"
    }
}
