package com.example.exercisetracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

// Simple bar chart of the last few weeks of activity, used on the History screen
class WeeklyChartView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var values = floatArrayOf()
    private var labels = listOf<String>()
    private var formatValue: (Float) -> String = { it.toInt().toString() }

    private val density = resources.displayMetrics.density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6200EE")
    }

    private val emptyBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 11 * density
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 11 * density
        textAlign = Paint.Align.CENTER
    }

    fun setData(values: List<Float>, labels: List<String>, formatValue: (Float) -> String) {
        this.values = values.toFloatArray()
        this.labels = labels
        this.formatValue = formatValue
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val labelSpace = 22 * density
        val valueSpace = 18 * density
        val chartBottom = height - labelSpace
        val chartHeight = chartBottom - valueSpace
        val slot = width.toFloat() / values.size
        val barWidth = slot * 0.6f
        val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f

        values.forEachIndexed { i, value ->
            val centerX = slot * i + slot / 2
            // Empty weeks still get a sliver so the timeline reads clearly
            val barHeight = maxOf(chartHeight * (value / max), 3 * density)
            val top = chartBottom - barHeight
            val rect = RectF(centerX - barWidth / 2, top, centerX + barWidth / 2, chartBottom)
            canvas.drawRoundRect(rect, 4 * density, 4 * density, if (value > 0f) barPaint else emptyBarPaint)

            if (value > 0f) canvas.drawText(formatValue(value), centerX, top - 5 * density, valuePaint)
            labels.getOrNull(i)?.let { canvas.drawText(it, centerX, height - 6 * density, labelPaint) }
        }
    }
}
