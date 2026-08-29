package com.example.exercisetracker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class MuscleStatsView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // 0: Arms, 1: Chest, 2: Legs, 3: Abs, 4: Shoulders, 5: Back
    private var muscleProgress = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
    private val labels = arrayOf("Arms", "Chest", "Legs", "Abs", "Shoulders", "Back")

    private val gridPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.parseColor("#886200EE") // Translucent Primary
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#6200EE")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    fun setStats(arms: Float, chest: Float, legs: Float, abs: Float, shoulders: Float, back: Float) {
        muscleProgress[0] = arms.coerceIn(0f, 1f)
        muscleProgress[1] = chest.coerceIn(0f, 1f)
        muscleProgress[2] = legs.coerceIn(0f, 1f)
        muscleProgress[3] = abs.coerceIn(0f, 1f)
        muscleProgress[4] = shoulders.coerceIn(0f, 1f)
        muscleProgress[5] = back.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (minOf(width, height) / 2f) * 0.7f

        // Draw background grid (5 rings)
        for (i in 1..5) {
            drawHexagon(canvas, centerX, centerY, radius * (i / 5f), gridPaint)
        }

        // Draw spoke lines
        for (i in 0 until 6) {
            val angle = Math.toRadians(i * 60.0 - 90.0)
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            canvas.drawLine(centerX, centerY, x, y, gridPaint)
            
            // Draw Labels
            val labelX = centerX + (radius + 50f) * cos(angle).toFloat()
            val labelY = centerY + (radius + 50f) * sin(angle).toFloat()
            canvas.drawText(labels[i], labelX, labelY, labelPaint)
        }

        // Draw Stats Shape
        val path = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians(i * 60.0 - 90.0)
            val p = muscleProgress[i]
            val x = centerX + radius * p * cos(angle).toFloat()
            val y = centerY + radius * p * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, borderPaint)
    }

    private fun drawHexagon(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val path = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians(i * 60.0 - 90.0)
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}
