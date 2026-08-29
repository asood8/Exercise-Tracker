package com.example.exercisetracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1f
    private var postScaleWidthOffset: Float = 0f
    private var postScaleHeightOffset: Float = 0f
    
    private var hudLines = mutableListOf<String>()

    private val landmarkPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val landmarkOutlinePaint = Paint().apply {
        color = Color.parseColor("#00E5FF") // Cyber Cyan
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val connectionPaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF") // Semi-transparent white
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val glowPaint = Paint().apply {
        color = Color.parseColor("#4000E5FF")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val hudTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 54f 
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeWidth = 2f
    }

    private val hudBgPaint = Paint().apply {
        color = Color.parseColor("#AA000000")
        style = Paint.Style.FILL
    }

    private val hudBorderPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val poseConnections = listOf(
        Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7), Pair(0, 4),
        Pair(4, 5), Pair(5, 6), Pair(6, 8), Pair(9, 10), Pair(11, 12),
        Pair(11, 13), Pair(13, 15), Pair(15, 17), Pair(15, 19), Pair(15, 21),
        Pair(17, 19), Pair(12, 14), Pair(14, 16), Pair(16, 18), Pair(16, 20),
        Pair(16, 22), Pair(18, 20), Pair(11, 23), Pair(12, 24), Pair(23, 24),
        Pair(23, 25), Pair(24, 26), Pair(25, 27), Pair(26, 28), Pair(27, 29),
        Pair(28, 30), Pair(29, 31), Pair(30, 32), Pair(27, 31), Pair(28, 32)
    )

    fun setResults(
        poseLandmarkerResult: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        feedback: List<String> = emptyList()
    ) {
        results = poseLandmarkerResult
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        this.hudLines = feedback.toMutableList()

        scaleFactor = minOf(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        postScaleWidthOffset = (width - (imageWidth * scaleFactor)) / 2f
        postScaleHeightOffset = (height - (imageHeight * scaleFactor)) / 2f

        invalidate()
    }

    fun clear() {
        results = null
        hudLines.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        results?.let { result ->
            result.landmarks().forEach { landmarks ->
                // Draw connections first
                poseConnections.forEach { connection ->
                    val (startIdx, endIdx) = connection
                    if (startIdx < landmarks.size && endIdx < landmarks.size) {
                        val startLandmark = landmarks[startIdx]
                        val endLandmark = landmarks[endIdx]

                        val startX = startLandmark.x() * imageWidth * scaleFactor + postScaleWidthOffset
                        val startY = startLandmark.y() * imageHeight * scaleFactor + postScaleHeightOffset
                        val endX = endLandmark.x() * imageWidth * scaleFactor + postScaleWidthOffset
                        val endY = endLandmark.y() * imageHeight * scaleFactor + postScaleHeightOffset

                        // Draw glow then main line
                        canvas.drawLine(startX, startY, endX, endY, glowPaint)
                        canvas.drawLine(startX, startY, endX, endY, connectionPaint)
                    }
                }

                // Draw landmarks
                landmarks.forEach { landmark ->
                    val x = landmark.x() * imageWidth * scaleFactor + postScaleWidthOffset
                    val y = landmark.y() * imageHeight * scaleFactor + postScaleHeightOffset
                    
                    // Outer cyan ring
                    canvas.drawCircle(x, y, 8f, landmarkOutlinePaint)
                    // Inner white dot
                    canvas.drawCircle(x, y, 4f, landmarkPaint)
                }
            }
            
            drawHud(canvas, hudLines)
        }
    }

    private fun drawHud(canvas: Canvas, lines: List<String>) {
        if (lines.isEmpty()) return

        val padding = 32f
        val lineHeight = 80f
        
        var maxLineWidth = 0f
        for (line in lines) {
            val lineWidth = hudTextPaint.measureText(line)
            if (lineWidth > maxLineWidth) {
                maxLineWidth = lineWidth
            }
        }
        
        val boxWidth = maxLineWidth + (padding * 2)
        val boxHeight = (lines.size * lineHeight) + (padding * 2)
        
        val x = 30f
        val y = 180f
        
        val rect = RectF(x, y, x + boxWidth, y + boxHeight)
        
        canvas.drawRoundRect(rect, 24f, 24f, hudBgPaint)
        canvas.drawRoundRect(rect, 24f, 24f, hudBorderPaint)
        
        lines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                x + padding,
                y + padding + (index + 1) * lineHeight - 24f,
                hudTextPaint
            )
        }
    }
}
