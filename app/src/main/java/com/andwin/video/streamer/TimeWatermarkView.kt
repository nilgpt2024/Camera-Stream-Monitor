package com.andwin.video.streamer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeWatermarkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "TimeWatermark"
        const val DEFAULT_UPDATE_INTERVAL_MS = 1000L
    }

    // 配置属性
    var textColor: Int = Color.WHITE
        set(value) {
            field = value
            textPaint.color = value
            invalidate()
        }
    
    var textSizePx: Float = 36f
        set(value) {
            field = value
            textPaint.textSize = value
            invalidate()
        }
    
    var bgColor: Int = Color.parseColor("#80000000")
        set(value) {
            field = value
            bgPaint.color = value
            invalidate()
        }
    
    var showDate: Boolean = true
    var showTime: Boolean = true
    var showMilliseconds: Boolean = false
    
    var position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT
    
    var paddingX: Float = 16f
        set(value) {
            field = value
            invalidate()
        }
    
    var paddingY: Float = 12f
        set(value) {
            field = value
            invalidate()
        }
    
    var cornerRadius: Float = 8f
        set(value) {
            field = value
            invalidate()
        }

    // 内部状态（公开访问）
    private var _isRunning = false
    val isRunning: Boolean get() = _isRunning
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = textSizePx
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }
    
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val msFormat = SimpleDateFormat(".SSS", Locale.getDefault())
    
    private var currentText = ""
    private var textWidth = 0f
    private var textHeight = 0f
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateText()
            if (_isRunning) {
                postDelayed(this, DEFAULT_UPDATE_INTERVAL_MS)
            }
        }
    }

    enum class WatermarkPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    init {
        // 简化初始化，不依赖 R.styleable（避免资源编译问题）
        if (attrs != null) {
            try {
                val ta = context.obtainStyledAttributes(attrs, intArrayOf(
                    android.R.attr.textSize,
                    android.R.attr.textColor,
                    android.R.attr.background
                ))
                try {
                    textSizePx = ta.getDimension(0, textSizePx)
                    textColor = ta.getColor(1, textColor)
                    bgColor = ta.getColor(2, bgColor)
                } finally {
                    ta.recycle()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read attributes: ${e.message}")
            }
        }
        
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    fun start() {
        if (!_isRunning) {
            _isRunning = true
            post(updateRunnable)
            Log.d(TAG, "时间水印已启动")
        }
    }

    fun stop() {
        _isRunning = false
        removeCallbacks(updateRunnable)
        Log.d(TAG, "时间水印已停止")
    }

    private fun updateText() {
        val sb = StringBuilder()
        val now = Date()
        
        if (showDate && showTime) {
            sb.append(dateFormat.format(now))
            sb.append(" ")
            sb.append(timeFormat.format(now))
        } else if (showDate) {
            sb.append(dateFormat.format(now))
        } else if (showTime) {
            sb.append(timeFormat.format(now))
        }
        
        if (showMilliseconds) {
            sb.append(msFormat.format(now))
        }
        
        currentText = sb.toString()
        
        val metrics = textPaint.fontMetrics
        textHeight = metrics.descent - metrics.ascent
        textWidth = textPaint.measureText(currentText)
        
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (currentText.isEmpty()) return
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        val bgWidth = textWidth + paddingX * 2
        val bgHeight = textHeight + paddingY * 2
        
        val x = when (position) {
            WatermarkPosition.TOP_LEFT -> 0f
            WatermarkPosition.TOP_RIGHT -> viewWidth - bgWidth
            WatermarkPosition.BOTTOM_LEFT -> 0f
            WatermarkPosition.BOTTOM_RIGHT -> viewWidth - bgWidth
        }
        
        val y = when (position) {
            WatermarkPosition.TOP_LEFT -> 0f
            WatermarkPosition.TOP_RIGHT -> 0f
            WatermarkPosition.BOTTOM_LEFT -> viewHeight - bgHeight
            WatermarkPosition.BOTTOM_RIGHT -> viewHeight - bgHeight
        }
        
        canvas.drawRoundRect(x, y, x + bgWidth, y + bgHeight, cornerRadius, cornerRadius, bgPaint)
        
        val textX = x + paddingX
        val textY = y + paddingY - textPaint.fontMetrics.ascent
        canvas.drawText(currentText, textX, textY, textPaint)
    }
}
