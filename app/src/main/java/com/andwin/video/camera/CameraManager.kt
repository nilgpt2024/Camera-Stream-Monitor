package com.andwin.video.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    
    // 前置摄像头组件
    private var frontCamera: Camera? = null
    private var frontImageCapture: ImageCapture? = null
    private var frontVideoCapture: VideoCapture<Recorder>? = null
    private var frontPreview: Preview? = null
    
    // 后置摄像头组件
    private var backCamera: Camera? = null
    private var backImageCapture: ImageCapture? = null
    private var backVideoCapture: VideoCapture<Recorder>? = null
    private var backPreview: Preview? = null
    
    private var isDualMode = false
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    // 录制质量配置（默认使用 HD，平衡画质和文件大小）
    var recordingQuality: String = "hd"  // "uhd" | "fhd" | "hd" | "sd"

    var onFrameAvailable: ((android.graphics.Bitmap) -> Unit)? = null

    suspend fun initialize(): Boolean = suspendCancellableCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                Log.i("CameraManager", "✅ CameraProvider 初始化成功")
                continuation.resume(true)
            } catch (e: Exception) {
                Log.e("CameraManager", "❌ CameraProvider 初始化失败", e)
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 启动单个摄像头（包含 VideoCapture 用于录像）
     */
    fun startCamera(
        previewView: androidx.camera.view.PreviewView,
        cameraId: String = "0",
        resolution: Size = Size(1280, 720),
        fps: Int = 30
    ) {
        val provider = cameraProvider ?: run {
            Log.e("CameraManager", "CameraProvider 为空!")
            return
        }

        try {
            provider.unbindAll()
            isDualMode = false

            // 创建 Preview
            val preview = Preview.Builder()
                .setTargetResolution(resolution)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // 创建 ImageCapture (用于拍照)
            val imageCapture = ImageCapture.Builder()
                .setTargetResolution(resolution)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // 创建 VideoCapture (用于录像) - 根据配置选择画质
            val quality = when (recordingQuality.lowercase()) {
                "uhd" -> androidx.camera.video.Quality.UHD      // 4K, 文件最大 (~150MB/分钟)
                "fhd" -> androidx.camera.video.Quality.FHD      // 1080p (~60MB/分钟)
                "hd" -> androidx.camera.video.Quality.HD        // 720p（默认推荐 ~30MB/分钟）
                "sd" -> androidx.camera.video.Quality.SD        // 480p（~15MB/分钟）
                else -> androidx.camera.video.Quality.HD       // 默认 HD
            }
            
            Log.i("CameraManager", "录制质量: $recordingQuality")
            
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality))
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)

            // 选择摄像头
            val cameraSelector = if (cameraId == "1") {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            // 绑定所有用例到生命周期
            val camera = provider.bindToLifecycle(
                (context as androidx.lifecycle.LifecycleOwner),
                cameraSelector,
                preview,
                imageCapture,
                videoCapture
            )

            // 保存引用
            if (cameraId == "1") {
                backCamera = camera
                backImageCapture = imageCapture
                backVideoCapture = videoCapture
                backPreview = preview
                
                frontCamera = null
                frontImageCapture = null
                frontVideoCapture = null
                frontPreview = null
            } else {
                frontCamera = camera
                frontImageCapture = imageCapture
                frontVideoCapture = videoCapture
                frontPreview = preview
                
                backCamera = null
                backImageCapture = null
                backVideoCapture = null
                backPreview = null
            }
            
            Log.i("CameraManager", "✅ 摄像头启动成功 (cameraId=$cameraId)")
            Log.d("CameraManager", "   ImageCapture: ${if (cameraId == "1") "back" else "front"} != null")
            Log.d("CameraManager", "   VideoCapture: ${if (cameraId == "1") "back" else "front"} != null")

        } catch (e: Exception) {
            Log.e("CameraManager", "启动摄像头失败", e)
            e.printStackTrace()
        }
    }

    /**
     * 启动双摄像头模式
     */
    fun startDualCamera(
        frontPreviewView: androidx.camera.view.PreviewView,
        backPreviewView: androidx.camera.view.PreviewView,
        resolution: Size = Size(640, 480)
    ): Boolean {
        val provider = cameraProvider ?: return false

        try {
            provider.unbindAll()

            // 前置摄像头
            frontPreview = Preview.Builder().setTargetResolution(resolution).build()
                .also { it.setSurfaceProvider(frontPreviewView.surfaceProvider) }
            frontImageCapture = ImageCapture.Builder().setTargetResolution(resolution).build()
            
            val qualityFront = when (recordingQuality.lowercase()) {
                "uhd" -> androidx.camera.video.Quality.UHD
                "fhd" -> androidx.camera.video.Quality.FHD
                "hd" -> androidx.camera.video.Quality.HD
                "sd" -> androidx.camera.video.Quality.SD
                else -> androidx.camera.video.Quality.HD
            }
            
            val recorderFront = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(qualityFront))
                .build()
            frontVideoCapture = VideoCapture.withOutput(recorderFront)

            frontCamera = provider.bindToLifecycle(
                context as androidx.lifecycle.LifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                frontPreview!!, frontImageCapture!!, frontVideoCapture!!
            )

            // 尝试后置摄像头
            try {
                backPreview = Preview.Builder().setTargetResolution(resolution).build()
                    .also { it.setSurfaceProvider(backPreviewView.surfaceProvider) }
                backImageCapture = ImageCapture.Builder().setTargetResolution(resolution).build()
                
                val qualityBack = when (recordingQuality.lowercase()) {
                    "uhd" -> androidx.camera.video.Quality.UHD
                    "fhd" -> androidx.camera.video.Quality.FHD
                    "hd" -> androidx.camera.video.Quality.HD
                    "sd" -> androidx.camera.video.Quality.SD
                    else -> androidx.camera.video.Quality.HD
                }
                
                val recorderBack = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(qualityBack))
                    .build()
                backVideoCapture = VideoCapture.withOutput(recorderBack)

                backCamera = provider.bindToLifecycle(
                    context as androidx.lifecycle.LifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    backPreview!!, backImageCapture!!, backVideoCapture!!
                )
                
                isDualMode = true
                Log.i("CameraManager", "✅ 双摄模式启动成功")
                return true
                
            } catch (e: Exception) {
                Log.w("CameraManager", "后置摄像头启动失败，使用单摄模式", e)
                isDualMode = false
                return false
            }

        } catch (e: Exception) {
            Log.e("CameraManager", "双摄模式启动失败", e)
            isDualMode = false
            startCamera(frontPreviewView, "0", resolution)
            return false
        }
    }

    fun hasMultipleCameras(): Boolean = cameraProvider?.availableCameraInfos?.size ?: 0 > 1
    fun isDualCameraActive(): Boolean = isDualMode && (frontCamera != null || backCamera != null)

    fun stopCamera() {
        try {
            Log.i("CameraManager", "正在停止所有摄像头...")
            
            // 步骤1: 解绑所有用例（异步）
            cameraProvider?.unbindAll()
            
            // 步骤2: 清空所有引用
            frontCamera = null; backCamera = null
            frontImageCapture = null; backImageCapture = null
            frontVideoCapture = null; backVideoCapture = null
            frontPreview = null; backPreview = null
            
            isDualMode = false
            
            Log.i("CameraManager", "✅ 摄像头已停止 (unbindAll 完成)")
        } catch (e: Exception) {
            Log.e("CameraManager", "停止摄像头异常", e)
        }
    }
    
    /**
     * 完全关闭 CameraProvider（用于切换到 RootEncoder）
     */
    fun forceShutdown() {
        try {
            Log.i("CameraManager", "═══ 强制关闭 CameraProvider ═══")
            
            // 解绑所有用例
            cameraProvider?.unbindAll()
            
            // 清空引用
            frontCamera = null; backCamera = null
            frontImageCapture = null; backImageCapture = null
            frontVideoCapture = null; backVideoCapture = null
            frontPreview = null; backPreview = null
            
            // 关闭 executor
            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdownNow()
            }
            
            // 完全释放 CameraProvider
            cameraProvider = null
            
            isDualMode = false
            
            Log.i("CameraManager", "✅ CameraProvider 已完全释放")
        } catch (e: Exception) {
            Log.e("CameraManager", "强制关闭异常", e)
        }
    }
    
    /**
     * 重新初始化 CameraProvider（从 forceShutdown 后恢复）
     */
    suspend fun reinitialize(): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            Log.i("CameraManager", "重新初始化 CameraProvider...")
            
            // 创建新的 executor
            val newExecutor = Executors.newSingleThreadExecutor()
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    
                    // 更新 executor 引用（如果需要）
                    // cameraExecutor = newExecutor
                    
                    Log.i("CameraManager", "✅ CameraProvider 重新初始化成功")
                    continuation.resume(true)
                } catch (e: Exception) {
                    Log.e("CameraManager", "❌ CameraProvider 重新初始化失败", e)
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e("CameraManager", "重新初始化异常", e)
            continuation.resumeWithException(e)
        }
    }

    // ========== 获取各个组件 ==========

    fun getFrontImageCapture(): ImageCapture? = frontImageCapture
    fun getBackImageCapture(): ImageCapture? = backImageCapture
    fun getImageCapture(): ImageCapture? = frontImageCapture ?: backImageCapture

    fun getFrontVideoCapture(): VideoCapture<Recorder>? = frontVideoCapture
    fun getBackVideoCapture(): VideoCapture<Recorder>? = backVideoCapture
    fun getVideoCapture(): VideoCapture<Recorder>? = frontVideoCapture ?: backVideoCapture

    fun getFrontCamera(): Camera? = frontCamera
    fun getBackCamera(): Camera? = backCamera
    fun getCamera(): Camera? = frontCamera ?: backCamera

    fun shutdown() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        cameraProvider = null
    }
}
