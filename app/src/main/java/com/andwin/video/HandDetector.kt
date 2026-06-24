package com.andwin.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 手部检测器 - 使用 MediaPipe Hands 进行实时双手检测
 *
 * 基于 Google 官方示例代码实现：
 * https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/hand_landmarker/android
 *
 * 功能：
 * - 检测画面中的手部数量（最多2只）
 * - 支持检测单手或双手
 * - 通过回调返回检测结果
 * - 使用 LIVE_STREAM 模式实现实时流式检测
 */
class HandDetector(private val context: Context) {

    var onHandsDetected: ((handCount: Int, isBothHands: Boolean, landmarks: List<List<NormalizedLandmark>>?) -> Unit)? = null
    var onError: ((error: String) -> Unit)? = null

    private var handLandmarker: HandLandmarker? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isRunning = false

    // 检测参数
    private val maxNumHands = 2              // 最大检测手数
    private val minHandDetectionConfidence = 0.5f   // 检测置信度阈值
    private val minHandTrackingConfidence = 0.5f    // 跟踪置信度阈值
    private val minHandPresenceConfidence = 0.5f    // 存在置信度阈值

    // 平滑处理：连续 N 帧确认后才触发状态变化
    private val confirmFrames = 3
    private var detectedCountHistory = mutableListOf<Int>()

    companion object {
        private const val TAG = "HandDetector"

        /** MediaPipe 手部 landmark 模型文件（需放在 assets 目录） */
        private const val MODEL_FILE = "hand_landmarker.task"
    }

    /**
     * 初始化手部检测器
     *
     * 使用 MediaPipe Tasks Vision API 创建 HandLandmarker 实例
     * 模型加载优先级: assets > 内部存储（下载缓存）
     */
    fun initialize(): Boolean {
        return try {
            Log.i(TAG, "🔄 正在初始化 MediaPipe HandLandmarker...")

            // Step 1: 配置基础选项（模型路径 + 推理设备）
            val baseOptionBuilder = BaseOptions.builder()
                .setDelegate(Delegate.CPU)  // 使用 CPU 推理（兼容性最好）
                .setModelAssetPath(MODEL_FILE)  // 从 assets 加载模型

            // Step 2: 配置 HandLandmarker 专用选项
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptionBuilder.build())
                .setRunningMode(RunningMode.LIVE_STREAM)  // 流模式，适合实时检测
                .setNumHands(maxNumHands)
                .setMinHandDetectionConfidence(minHandDetectionConfidence)
                .setMinTrackingConfidence(minHandTrackingConfidence)
                .setMinHandPresenceConfidence(minHandPresenceConfidence)
                .setResultListener { result: HandLandmarkerResult, input: MPImage ->
                    // LIVE_STREAM 模式的结果回调（在推理线程执行）
                    handleDetectionResult(result)
                }
                .setErrorListener { error: RuntimeException ->
                    Log.e(TAG, "❌ MediaPipe 检测错误: ${error.message}", error)
                    runOnUiThread {
                        onError?.invoke("检测错误: ${error.message}")
                    }
                }
                .build()

            // Step 3: 创建 HandLandmarker 实例
            handLandmarker = HandLandmarker.createFromOptions(context, options)

            Log.i(TAG, "✅ MediaPipe HandLandmarker 初始化成功")
            Log.i(TAG, "   - 模型: $MODEL_FILE")
            Log.i(TAG, "   - 最大手数: $maxNumHands")
            Log.i(TAG, "   - 置信度: $minHandDetectionConfidence")
            true
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ HandLandmarker 初始化失败 (IllegalState): ${e.message}", e)
            onError?.invoke("初始化失败: ${e.message}")
            false
        } catch (e: RuntimeException) {
            Log.e(TAG, "❌ HandLandmarker 初始化失败 (Runtime): ${e.message}", e)
            onError?.invoke("模型加载失败，请检查 assets/hand_landmarker.task 是否存在")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ HandLandmarker 初始化异常", e)
            onError?.invoke("未知错误: ${e.message}")
            false
        }
    }

    /**
     * 在主线程执行操作
     */
    private fun runOnUiThread(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }

    /**
     * 处理检测结果（LIVE_STREAM 回调）
     */
    private fun handleDetectionResult(result: HandLandmarkerResult) {
        try {
            // 获取检测到的手部关键点
            val landmarks = result.landmarks()
            val handCount = landmarks.size
            val isBothHands = handCount >= 2

            // 平滑处理
            val confirmedCount = smoothDetection(handCount)
            val confirmedBothHands = confirmedCount >= 2

            // 回调结果（切到主线程，传递 landmark 数据）
            if (detectedCountHistory.size >= confirmFrames) {
                runOnUiThread {
                    onHandsDetected?.invoke(confirmedCount, confirmedBothHands, landmarks)
                }

                if (handCount > 0) {
                    Log.d(TAG, "🤚 检测到 $handCount 只手 | 双手=$isBothHands | 平滑后=$confirmedCount")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理结果异常", e)
        }
    }

    /**
     * 平滑处理：避免检测结果抖动
     */
    private fun smoothDetection(currentCount: Int): Int {
        detectedCountHistory.add(currentCount)

        while (detectedCountHistory.size > confirmFrames) {
            detectedCountHistory.removeAt(0)
        }

        if (detectedCountHistory.size < confirmFrames) {
            return currentCount
        }

        // 取众数
        return detectedCountHistory.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key ?: currentCount
    }

    /**
     * 创建 CameraX ImageAnalysis 用例
     *
     * 关键：设置 OUTPUT_IMAGE_FORMAT_RGBA_8888，使帧数据为 ARGB 格式
     * 这样可以直接用 copyPixelsFromBuffer 转换为 Bitmap，无需手动 YUV→RGB 转换
     */
    fun createImageAnalysis(): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    if (isRunning && handLandmarker != null) {
                        detectHands(imageProxy)
                    } else {
                        imageProxy.close()
                    }
                }
            }
    }

    /**
     * 处理单帧图像（供外部统一帧分发器调用）
     *
     * 将 Bitmap 转换为 MPImage 后送入检测器进行异步检测
     *
     * @param bitmap 输入的位图图像（ARGB_8888 格式，已旋转和镜像）
     * @param frameTime 帧时间戳（毫秒），用于 LIVE_STREAM 模式排序
     */
    fun processFrame(bitmap: Bitmap, frameTime: Long) {
        try {
            if (!isRunning || handLandmarker == null) {
                return
            }

            // 将 Bitmap 转换为 MediaPipe MPImage
            val mpImage = BitmapImageBuilder(bitmap).build()

            // 异步检测（LIVE_STREAM 模式通过 ResultListener 返回结果）
            handLandmarker?.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 帧处理异常: ${e.message}", e)
        }
    }

    /**
     * 执行手部检测（将 CameraX 帧转换为 MediaPipe MPImage）
     *
     * 参考 MediaPipe 官方示例的 detectLiveStream() 方法
     */
    private fun detectHands(imageProxy: ImageProxy) {
        try {
            // Step 1: 将 ImageProxy 的 YUV 数据转为 ARGB Bitmap
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

            // 复制像素数据到 Bitmap
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            // Step 2: 应用旋转和镜像变换
            val matrix = Matrix().apply {
                // 根据摄像头旋转角度旋转
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                // 前置摄像头需要水平翻转
                // 如果是后置摄像头可以去掉这行
                postScale(-1f, 1f, imageProxy.width.toFloat() / 2, imageProxy.height.toFloat() / 2)
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0,
                bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            ).also {
                bitmapBuffer.recycle()
            }

            // Step 3: 转换为 MediaPipe MPImage 并送入检测器
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            val frameTime = System.nanoTime() / 1_000_000  // 毫秒时间戳

            // 异步检测（LIVE_STREAM 模式通过 ResultListener 返回结果）
            handLandmarker?.detectAsync(mpImage, frameTime)

            // 注意：rotatedBitmap 不在这里回收，MediaPipe 内部会使用
            // 在 ResultListener 回调后再回收（或由 GC 管理）

        } catch (e: Exception) {
            Log.e(TAG, "❌ 检测帧处理异常: ${e.message}", e)
        }
    }

    /**
     * 开始检测
     */
    fun start() {
        if (handLandmarker == null) {
            val success = initialize()
            if (!success) {
                Log.e(TAG, "❌ 无法启动检测：初始化失败")
                return
            }
        }
        isRunning = true
        detectedCountHistory.clear()
        Log.i(TAG, "▶️ MediaPipe 手部检测已启动 (LIVE_STREAM 模式)")
    }

    /**
     * 停止检测
     */
    fun stop() {
        isRunning = false
        Log.i(TAG, "⏹ 手部检测已停止")
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            stop()
            handLandmarker?.close()
            handLandmarker = null
            detectedCountHistory.clear()
            analysisExecutor.shutdown()
            Log.i(TAG, "🗑 MediaPipe HandLandmarker 资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败: ${e.message}")
        }
    }

    /**
     * 检查是否正在运行
     */
    fun isActive(): Boolean = isRunning
}
