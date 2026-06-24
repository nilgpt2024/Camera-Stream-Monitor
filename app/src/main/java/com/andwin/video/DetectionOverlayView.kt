package com.andwin.video

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * 检测结果叠加视图 - 在相机预览上绘制骨骼/关键点
 *
 * 支持绘制：
 * - 手部 21 个关键点 + 骨骼连线（每只手）
 * - 人脸轮廓/五官关键点
 * - 姿态 33 个骨骼点 + 全身骨架连线
 */
class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 显示开关
    var showHandSkeleton = true
    var showFaceSkeleton = true
    var showPoseSkeleton = true

    // 当前帧的检测数据
    private var handLandmarks: List<List<NormalizedLandmark>>? = null
    private var faceLandmarks: List<List<NormalizedLandmark>>? = null
    private var poseLandmarks: List<List<NormalizedLandmark>>? = null

    // 手部画笔
    private val handPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88")   // 绿色
        style = Paint.Style.FILL
        strokeWidth = 8f
    }
    private val handLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00CC66")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // 人脸画笔
    private val facePointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B6B")   // 红色
        style = Paint.Style.FILL
        strokeWidth = 4f
    }
    private val faceLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // 姿态画笔
    private val posePointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD93D")   // 黄色
        style = Paint.Style.FILL
        strokeWidth = 10f
    }
    private val poseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFAA00")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    companion object {
        /** 手部 21 个关键点的连接关系 (MediaPipe Hands 标准) */
        private val HAND_CONNECTIONS = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4),       // 拇指
            intArrayOf(0, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8),      // 食指
            intArrayOf(0, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 12),  // 中指
            intArrayOf(0, 13), intArrayOf(13, 14), intArrayOf(14, 15), intArrayOf(15, 16),// 无名指
            intArrayOf(0, 17), intArrayOf(17, 18), intArrayOf(18, 19), intArrayOf(19, 20),// 小指
            intArrayOf(5, 9), intArrayOf(9, 13), intArrayOf(13, 17)                      // 掌心
        )

        /** 姿态 33 个关键点的连接关系 (MediaPipe Pose 标准) */
        private val POSE_CONNECTIONS = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 7),       // 面部-头部
            intArrayOf(0, 4), intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 8),
            intArrayOf(8, 9), intArrayOf(9, 10),                                          // 躯干
            intArrayOf(11, 12),                                                            // 肩膀横线
            intArrayOf(11, 13), intArrayOf(13, 15), intArrayOf(15, 17), intArrayOf(17, 19),intArrayOf(19, 21),intArrayOf(21, 23), // 左臂
            intArrayOf(12, 14), intArrayOf(14, 16), intArrayOf(16, 18), intArrayOf(18, 20),intArrayOf(20, 22),intArrayOf(22, 24), // 右臂
            intArrayOf(11, 23), intArrayOf(12, 24),                                        // 躯干侧面
            intArrayOf(23, 25), intArrayOf(25, 27), intArrayOf(27, 29), intArrayOf(29, 31),// 左腿
            intArrayOf(24, 26), intArrayOf(26, 28), intArrayOf(28, 30), intArrayOf(30, 32) // 右腿
        )

        /** 人脸轮廓关键点索引（精简版，只画轮廓+眼睛+嘴巴） */
        private val FACE_OVAL = listOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
            397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
            172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10
        )
        private val LEFT_EYE = listOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246, 33)
        private val RIGHT_EYE = listOf(362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398, 362)
        private val LIPS_OUTER = listOf(61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 308, 324, 318, 402, 317, 14, 87, 178, 88, 95, 185, 61)
    }

    /**
     * 更新手部数据
     */
    fun setHandLandmarks(landmarks: List<List<NormalizedLandmark>>?) {
        handLandmarks = landmarks
        invalidate()
    }

    /**
     * 更新人脸数据
     */
    fun setFaceLandmarks(landmarks: List<List<NormalizedLandmark>>?) {
        faceLandmarks = landmarks
        invalidate()
    }

    /**
     * 更新姿态数据
     */
    fun setPoseLandmarks(landmarks: List<List<NormalizedLandmark>>?) {
        poseLandmarks = landmarks
        invalidate()
    }

    /**
     * 清除所有绘制数据
     */
    fun clear() {
        handLandmarks = null
        faceLandmarks = null
        poseLandmarks = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        // 绘制手部骨骼
        if (showHandSkeleton && handLandmarks != null) {
            for (landmarks in handLandmarks!!) {
                drawConnections(canvas, landmarks, HAND_CONNECTIONS, handLinePaint)
                drawPoints(canvas, landmarks, handPointPaint)
            }
        }

        // 绘制人脸关键点
        if (showFaceSkeleton && faceLandmarks != null) {
            for (landmarks in faceLandmarks!!) {
                drawFace(canvas, landmarks)
            }
        }

        // 绘制姿态骨骼
        if (showPoseSkeleton && poseLandmarks != null) {
            for (landmarks in poseLandmarks!!) {
                drawConnections(canvas, landmarks, POSE_CONNECTIONS, poseLinePaint)
                drawPoints(canvas, landmarks, posePointPaint)
            }
        }
    }

    /**
     * 绘制连线（通用）
     */
    private fun drawConnections(
        canvas: Canvas,
        landmarks: List<NormalizedLandmark>,
        connections: Array<IntArray>,
        paint: Paint
    ) {
        for (connection in connections) {
            val start = connection[0]
            val end = connection[1]
            if (start < landmarks.size && end < landmarks.size) {
                val startPoint = toScreenPoint(landmarks[start])
                val endPoint = toScreenPoint(landmarks[end])
                canvas.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y, paint)
            }
        }
    }

    /**
     * 绘制关键点圆圈（通用）
     */
    private fun drawPoints(
        canvas: Canvas,
        landmarks: List<NormalizedLandmark>,
        paint: Paint
    ) {
        val radius = paint.strokeWidth / 2
        for (landmark in landmarks) {
            val point = toScreenPoint(landmark)
            canvas.drawCircle(point.x, point.y, radius, paint)
        }
    }

    /**
     * 绘制人脸（轮廓 + 眼睛 + 嘴巴，避免全 478 点太密集）
     */
    private fun drawFace(canvas: Canvas, landmarks: List<NormalizedLandmark>) {
        // 面部轮廓
        drawOval(canvas, landmarks, FACE_OVAL, faceLinePaint)
        // 左眼
        drawOval(canvas, landmarks, LEFT_EYE, faceLinePaint)
        // 右眼
        drawOval(canvas, landmarks, RIGHT_EYE, faceLinePaint)
        // 嘴唇外圈
        drawOval(canvas, landmarks, LIPS_OUTER, faceLinePaint)
        // 关键点（眉毛、鼻尖等）
        val keyPoints = listOf(1, 33, 133, 263, 362, 61, 291, 199, 168, 6, 358)
        for (idx in keyPoints) {
            if (idx < landmarks.size) {
                val point = toScreenPoint(landmarks[idx])
                canvas.drawCircle(point.x, point.y, 3f, facePointPaint)
            }
        }
    }

    /**
     * 绘制闭合轮廓
     */
    private fun drawOval(
        canvas: Canvas,
        landmarks: List<NormalizedLandmark>,
        indices: List<Int>,
        paint: Paint
    ) {
        if (indices.isEmpty()) return
        var prevPoint = toScreenPoint(landmarks[indices[0]])
        for (i in 1 until indices.size) {
            val idx = indices[i]
            if (idx < landmarks.size) {
                val point = toScreenPoint(landmarks[idx])
                canvas.drawLine(prevPoint.x, prevPoint.y, point.x, point.y, paint)
                prevPoint = point
            }
        }
        // 闭合
        val firstIdx = indices[0]
        if (firstIdx < landmarks.size) {
            val endPoint = toScreenPoint(landmarks[firstIdx])
            canvas.drawLine(prevPoint.x, prevPoint.y, endPoint.x, endPoint.y, paint)
        }
    }

    /**
     * 归一化坐标 → 屏幕坐标
     */
    private fun toScreenPoint(landmark: NormalizedLandmark): PointF {
        return PointF(landmark.x().toFloat() * width, landmark.y().toFloat() * height)
    }
}
