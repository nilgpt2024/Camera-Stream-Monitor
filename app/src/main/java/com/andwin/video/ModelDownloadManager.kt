package com.andwin.video

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * MediaPipe 模型下载管理器
 *
 * 负责检查、下载和缓存 MediaPipe AI 模型文件（.task）
 *
 * 模型存储策略：
 * 1. 优先从 assets 目录加载（打包时内置）
 * 2. assets 不存在则从内部存储加载（之前下载缓存的）
 * 3. 都不存在则提示用户下载
 */
object ModelDownloadManager {

    private const val TAG = "ModelDownloadMgr"
    private const val MODEL_DIR = "mediapipe_models"

    /**
     * 模型定义：名称、下载URL、描述、预计大小(MB)
     */
    enum class ModelInfo(
        val fileName: String,
        val downloadUrl: String,
        val description: String,
        val sizeMB: Int
    ) {
        HAND(
            "hand_landmarker.task",
            "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task",
            "手部检测模型（检测手部关键点）",
            10
        ),
        FACE(
            "face_landmarker.task",
            "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task",
            "人脸检测模型（478个面部关键点 + 表情）",
            12
        ),
        POSE(
            "pose_landmarker.task",
            "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task",
            "姿态检测模型（33个全身骨骼关键点）",
            6
        )
    }

    /**
     * 获取模型在内部存储中的路径
     * 如果文件不存在返回 null
     */
    fun getModelFilePath(context: Context, model: ModelInfo): File? {
        val dir = File(context.filesDir, MODEL_DIR)
        val file = File(dir, model.fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * 获取所有缺失的模型列表
     * 同时检查 assets 和内部存储
     */
    fun getMissingModels(context: Context): List<ModelInfo> {
        return ModelInfo.values().filter { model ->
            !isModelAvailable(context, model.fileName)
        }
    }

    /**
     * 检查指定模型是否可用（assets 或 内部存储）
     */
    fun isModelAvailable(context: Context, fileName: String): Boolean {
        // 先检查 assets
        try {
            context.assets.open(fileName).use { stream ->
                if (stream.available() > 0) return true
            }
        } catch (_: Exception) {
            // assets 中不存在，继续检查内部存储
        }

        // 再检查内部存储
        val file = File(context.filesDir, "$MODEL_DIR/$fileName")
        return file.exists() && file.length() > 0
    }

    /**
     * 获取模型的实际可用路径
     * 返回值: Pair<是否为Asset路径, 完整路径字符串>
     * 供检测器使用来决定调用 setModelAssetPath 还是 setModelPath
     */
    fun getModelPath(context: Context, fileName: String): Pair<Boolean, String> {
        // 优先 assets
        try {
            context.assets.open(fileName).use { stream ->
                if (stream.available() > 0) return Pair(true, fileName)
            }
        } catch (_: Exception) {}

        // 回退到内部存储
        val file = File(context.filesDir, "$MODEL_DIR/$fileName")
        if (file.exists() && file.length() > 0) {
            return Pair(false, file.absolutePath)
        }

        // 都不存在，返回 asset 路径让初始化时报错
        return Pair(true, fileName)
    }

    /**
     * 下载单个模型文件
     *
     * @param context 上下文
     * @param model 要下载的模型
     * @param onProgress 进度回调 (0-100)
     * @return 下载成功返回 true
     */
    suspend fun downloadModel(
        context: Context,
        model: ModelInfo,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "📥 开始下载模型: ${model.fileName}")

            // 确保目录存在
            val dir = File(context.filesDir, MODEL_DIR)
            if (!dir.exists()) dir.mkdirs()

            val outputFile = File(dir, model.fileName)
            val tempFile = File(dir, "${model.fileName}.tmp")

            val url = URL(model.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.requestMethod = "GET"
            connection.doInput = true

            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "❌ 下载失败 HTTP ${connection.responseCode}: ${model.fileName}")
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(-1)
                }
                return@withContext false
            }

            val contentLength = connection.contentLength
            val inputStream = BufferedInputStream(connection.inputStream, 8192)
            val outputStream = FileOutputStream(tempFile)
            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                // 报告进度
                if (contentLength > 0 && onProgress != null) {
                    val progress = ((totalBytesRead * 100 / contentLength).toInt()).coerceIn(0, 100)
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(progress)
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            // 下载完成，重命名为正式文件名
            if (tempFile.exists()) {
                tempFile.renameTo(outputFile)
            }

            val fileSizeMB = outputFile.length() / (1024 * 1024)
            Log.i(TAG, "✅ 模型下载完成: ${model.fileName} (${fileSizeMB}MB)")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ 下载模型异常: ${model.fileName} - ${e.message}", e)
            withContext(Dispatchers.Main) {
                onProgress?.invoke(-1)
            }
            false
        }
    }

    /**
     * 显示模型下载对话框
     *
     * 列出所有缺失的模型，用户确认后逐个下载
     *
     * @param activity 当前 Activity
     * @param missingModels 缺失的模型列表
     * @param onAllComplete 全部下载完成回调
     */
    fun showDownloadDialog(
        activity: FragmentActivity,
        missingModels: List<ModelInfo>,
        onAllComplete: () -> Unit
    ) {
        if (missingModels.isEmpty()) {
            onAllComplete.invoke()
            return
        }

        // 构建缺失模型信息文本
        val modelInfoText = missingModels.joinToString("\n\n") { model ->
            "• ${model.description}\n  大小: ~${model.sizeMB}MB"
        }

        val totalSizeMB = missingModels.sumOf { it.sizeMB }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.model_download_title))
            .setMessage(activity.getString(R.string.model_download_message, totalSizeMB, modelInfoText))
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.model_download_start)) { dialog, _ ->
                dialog.dismiss()
                startDownloadWithProgress(activity, missingModels, onAllComplete)
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    /**
     * 带进度的下载流程
     */
    private fun startDownloadWithProgress(
        activity: FragmentActivity,
        models: List<ModelInfo>,
        onComplete: () -> Unit
    ) {
        val progressDialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.model_downloading))
            .setMessage(activity.getString(R.string.model_download_progress, models[0].description))
            .setCancelable(false)
            .create()

        progressDialog.show()

        GlobalScope.launch(Dispatchers.Main) {
            var allSuccess = true
            val failedList = mutableListOf<String>()

            for ((index, model) in models.withIndex()) {
                // 更新对话框文字
                progressDialog.setMessage(
                    activity.getString(
                        R.string.model_download_item_progress,
                        index + 1,
                        models.size,
                        model.description
                    )
                )

                val success = downloadModel(activity, model) { progress ->
                    if (progress >= 0 && progress <= 100) {
                        progressDialog.setMessage(
                            activity.getString(
                                R.string.model_download_percent,
                                model.description,
                                progress
                            )
                        )
                    }
                }

                if (!success) {
                    allSuccess = false
                    failedList.add(model.description)
                }
            }

            progressDialog.dismiss()

            if (allSuccess) {
                AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.model_download_complete_title))
                    .setMessage(activity.getString(R.string.model_download_complete_msg))
                    .setPositiveButton(activity.getString(R.string.ok)) { _, _ ->
                        onComplete.invoke()
                    }
                    .show()
            } else {
                AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.model_download_failed_title))
                    .setMessage(
                        activity.getString(
                            R.string.model_download_failed_msg,
                            failedList.joinToString("\n")
                        )
                    )
                    .setPositiveButton(activity.getString(R.string.retry)) { _, _ ->
                        startDownloadWithProgress(activity, models, onComplete)
                    }
                    .setNegativeButton(activity.getString(R.string.cancel), null)
                    .show()
            }
        }
    }

    /**
     * 清理已下载的所有模型缓存
     */
    fun clearModelCache(context: Context) {
        val dir = File(context.filesDir, MODEL_DIR)
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
            Log.i(TAG, "🗑 已清理模型缓存")
        }
    }

    /**
     * 获取已下载模型的总大小
     */
    fun getCacheSizeMB(context: Context): Long {
        val dir = File(context.filesDir, MODEL_DIR)
        if (!dir.exists()) return 0
        return dir.listFiles()?.sumOf { it.length() }?.div(1024 * 1024) ?: 0
    }
}
