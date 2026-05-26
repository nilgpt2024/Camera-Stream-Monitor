package com.andwin.video.recorder

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoRecorder(private val context: Context) {

    private var activeRecording: Recording? = null
    private var isRecordingActive = false
    private var currentOutputFile: File? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "VideoRecorder"
        private const val RECORDINGS_DIR = "recordings"
        private const val FILENAME_FORMAT = "yyyy-MM-dd_HH-mm-ss"
    }

    fun getRecordingsDir(): File {
        val dir = File(context.getExternalFilesDir(null), RECORDINGS_DIR)
        if (!dir.exists()) {
            val created = dir.mkdirs()
            Log.d(TAG, "创建目录: ${dir.absolutePath} -> $created")
        }
        return dir
    }

    fun getRecordingFiles(): List<File> {
        return getRecordingsDir().listFiles()
            ?.filter { it.extension.lowercase() in listOf("mp4", "webm", "jpg") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * 方案1: 使用 CameraX VideoCapture 进行真正的视频录制
     */
    fun startVideoRecording(
        videoCapture: VideoCapture<androidx.camera.video.Recorder>,
        onComplete: (File) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        if (isRecordingActive) {
            Log.w(TAG, "已在录制中")
            return false
        }

        try {
            val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()).format(Date())
            val outputFile = File(getRecordingsDir(), "video_$timestamp.mp4")
            
            if (!outputFile.parentFile!!.exists()) {
                outputFile.parentFile!!.mkdirs()
            }
            
            currentOutputFile = outputFile
            
            Log.i(TAG, "=== 开始视频录制 ===")
            Log.i(TAG, "输出文件: ${outputFile.absolutePath}")
            
            val outputOptions = FileOutputOptions.Builder(outputFile).build()

            activeRecording = videoCapture.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    Log.d(TAG, "录制事件: ${event.javaClass.simpleName}")
                    
                    // 检查是否是 Finalize 事件（录制结束）
                    if (event is androidx.camera.video.VideoRecordEvent.Finalize) {
                        Log.i(TAG, "=== 录制 Finalize 事件 ===")
                        
                        if (event.hasError()) {
                            val errorMsg = "录制错误: ${event.error}"
                            Log.e(TAG, errorMsg)
                            
                            scope.launch { onError(errorMsg) }
                        } else {
                            val sizeKB = outputFile.length() / 1024
                            Log.i(TAG, "✅ 视频录制成功!")
                            Log.i(TAG, "   文件: ${outputFile.absolutePath}")
                            Log.i(TAG, "   大小: ${sizeKB} KB")
                            Log.i(TAG, "   存在: ${outputFile.exists()}")
                            
                            scope.launch { onComplete(outputFile) }
                        }
                        
                        isRecordingActive = false
                        activeRecording = null
                    }
                    // Status 事件只是记录日志，不需要特殊处理
                }
            
            isRecordingActive = true
            Log.i(TAG, "✅ 录制已启动")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "启动视频录制失败", e)
            isRecordingActive = false
            onError("启动失败: ${e.message}")
            return false
        }
    }

    /**
     * 方案2: 使用 ImageCapture 拍照（备用）
     */
    fun startPhotoCapture(
        imageCapture: ImageCapture,
        onComplete: (File) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        if (isRecordingActive) {
            return false
        }

        try {
            val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()).format(Date())
            val outputFile = File(getRecordingsDir(), "photo_$timestamp.jpg")
            
            if (!outputFile.parentFile!!.exists()) {
                outputFile.parentFile!!.mkdirs()
            }
            
            currentOutputFile = outputFile
            
            Log.i(TAG, "=== 开始拍照 ===")
            Log.i(TAG, "输出文件: ${outputFile.absolutePath}")

            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val sizeKB = outputFile.length() / 1024
                        Log.i(TAG, "✅ 拍照成功!")
                        Log.i(TAG, "   文件: ${outputFile.absolutePath}")
                        Log.i(TAG, "   大小: ${sizeKB} KB")
                        
                        isRecordingActive = false
                        
                        scope.launch { onComplete(outputFile) }
                    }

                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                        Log.e(TAG, "❌ 拍照失败!", exception)
                        isRecordingActive = false
                        
                        scope.launch { onError("拍照失败: ${exception.message}") }
                    }
                }
            )
            
            isRecordingActive = true
            return true

        } catch (e: Exception) {
            Log.e(TAG, "启动拍照失败", e)
            onError("启动失败: ${e.message}")
            return false
        }
    }

    /**
     * 通用开始录制方法 - 自动选择最佳方案
     */
    fun startRecording(
        imageCapture: ImageCapture?,
        onComplete: (File) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        if (imageCapture == null) {
            onError("ImageCapture 为空")
            return false
        }
        
        return startPhotoCapture(imageCapture, onComplete, onError)
    }

    fun stopRecording(onStopped: (File?) -> Unit = {}) {
        Log.d(TAG, "请求停止录制, 当前状态: isRecording=$isRecordingActive")
        
        if (!isRecordingActive || activeRecording == null) {
            onStopped(currentOutputFile)
            return
        }

        try {
            Log.i(TAG, "正在停止录制...")
            activeRecording?.stop()
            
            // 延迟回调，等待 Finalize 事件
            scope.launch {
                kotlinx.coroutines.delay(500)
                onStopped(currentOutputFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止录制异常", e)
            onStopped(currentOutputFile)
        }
    }

    fun takePhoto(
        imageCapture: ImageCapture,
        onComplete: (File) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        return startPhotoCapture(imageCapture, onComplete, onError)
    }

    fun isCurrentlyRecording(): Boolean = isRecordingActive
    
    fun getCurrentRecordingFile(): File? = currentOutputFile

    fun deleteRecording(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            Log.e(TAG, "删除失败", e); false
        }
    }

    fun getTotalSize(): Long = getRecordingFiles().sumOf { it.length() }
    
    fun clearAllRecordings(): Int {
        val files = getRecordingFiles()
        return files.count { it.delete() }
    }

    fun release() {
        try {
            if (isRecordingActive && activeRecording != null) {
                activeRecording?.stop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "释放资源异常", e)
        }
        isRecordingActive = false
        activeRecording = null
    }
}
