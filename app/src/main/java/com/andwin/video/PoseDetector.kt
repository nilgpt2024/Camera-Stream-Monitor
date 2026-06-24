package com.andwin.video

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * 姿态检测器 - 使用 MediaPipe PoseLandmarker 进行实时人体姿态检测
 *
 * 基于 Google 官方示例代码实现，API 与 HandDetector 保持一致
 * 模型文件需放在 assets 目录: pose_landmarker.task
 */
class PoseDetector(private val context: Context) {

    var onPoseDetected: ((poseCount: Int, landmarks: List<List<NormalizedLandmark>>?) -> Unit)? = null
    var onError: ((error: String) -> Unit)? = null

    private var poseLandmarker: PoseLandmarker? = null
    private var isRunning = false

    // 检测参数
    private val maxNumPoses = 1
    private val minPoseDetectionConfidence = 0.5f
    private val minPoseTrackingConfidence = 0.5f
    private val minPosePresenceConfidence = 0.5f

    // 平滑处理：连续 N 帧确认后才触发状态变化
    private val confirmFrames = 3
    private var detectedCountHistory = mutableListOf<Int>()

    companion object {
        private const val TAG = "PoseDetector"
        private const val MODEL_FILE = "pose_landmarker.task"
    }

    /**
     * 初始化姿态检测器
     */
    fun initialize(): Boolean {
        return try {
            Log.i(TAG, "🔄 正在初始化 MediaPipe PoseLandmarker...")

            val baseOptionBuilder = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath(MODEL_FILE)

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionBuilder.build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(maxNumPoses)
                .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                .setMinTrackingConfidence(minPoseTrackingConfidence)
                .setMinPosePresenceConfidence(minPosePresenceConfidence)
                .setResultListener { result: PoseLandmarkerResult, input: MPImage ->
                    handleDetectionResult(result)
                }
                .setErrorListener { error: RuntimeException ->
                    Log.e(TAG, "❌ 姿态检测错误: ${error.message}", error)
                    runOnUiThread {
                        onError?.invoke("检测错误: ${error.message}")
                    }
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)

            Log.i(TAG, "✅ PoseLandmarker 初始化成功 | 模型: $MODEL_FILE")
            true
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ 初始化失败 (IllegalState): ${e.message}", e)
            onError?.invoke("初始化失败: ${e.message}")
            false
        } catch (e: RuntimeException) {
            Log.e(TAG, "❌ 初始化失败 (Runtime): ${e.message}", e)
            onError?.invoke("模型加载失败，请检查 assets/$MODEL_FILE 是否存在")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化异常", e)
            onError?.invoke("未知错误: ${e.message}")
            false
        }
    }

    private fun runOnUiThread(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }

    /**
     * 处理检测结果（LIVE_STREAM 回调）
     */
    private fun handleDetectionResult(result: PoseLandmarkerResult) {
        try {
            // 获取姿态关键点列表
            val landmarksList = result.landmarks()
            val poseCount = landmarksList.size

            // 平滑处理
            val confirmedCount = smoothDetection(poseCount)

            // 回调结果（切到主线程，传递 landmark 数据）
            if (detectedCountHistory.size >= confirmFrames) {
                runOnUiThread {
                    onPoseDetected?.invoke(confirmedCount, landmarksList)
                }

                if (poseCount > 0) {
                    Log.d(TAG, "🏃 检测到 $poseCount 个人体姿态 | 平滑后=$confirmedCount")
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
        return detectedCountHistory.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key ?: currentCount
    }

    /**
     * 处理单帧图像（供外部统一帧分发器调用）
     */
    fun processFrame(bitmap: Bitmap, frameTime: Long) {
        try {
            if (!isRunning || poseLandmarker == null) return
            val mpImage = BitmapImageBuilder(bitmap).build()
            poseLandmarker?.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 帧处理异常: ${e.message}", e)
        }
    }

    fun start() {
        if (poseLandmarker == null) {
            val success = initialize()
            if (!success) return
        }
        isRunning = true
        detectedCountHistory.clear()
        Log.i(TAG, "▶️ 姿态检测已启动")
    }

    fun stop() {
        isRunning = false
        Log.i(TAG, "⏹ 姿态检测已停止")
    }

    fun release() {
        try {
            stop()
            poseLandmarker?.close()
            poseLandmarker = null
            detectedCountHistory.clear()
            Log.i(TAG, "🗑 PoseLandmarker 资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败: ${e.message}")
        }
    }

    fun isActive(): Boolean = isRunning
}
