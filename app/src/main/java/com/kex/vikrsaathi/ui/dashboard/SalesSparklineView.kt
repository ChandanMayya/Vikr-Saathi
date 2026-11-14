package com.kex.vikrsaathi.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.kex.vikrsaathi.R
import kotlin.math.max

/**
 * Compact sales line with no axes. Empty, flat, and single-point series stay honest:
 * nothing is invented to fill a missing day.
 */
class SalesSparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val values = ArrayList<Float>()
    private val linePath = Path()
    private val fillPath = Path()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.orange_700)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.chart_line_fill)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.orange_700)
    }

    fun setValues(points: List<Double>) {
        values.clear()
        points.forEach { values.add(it.toFloat()) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty() || width <= 0 || height <= 0) return
        val stroke = max(2f, width * 0.018f)
        linePaint.strokeWidth = stroke
        val pad = stroke + 2f
        val left = pad
        val right = width - pad
        val top = pad
        val bottom = height - pad
        if (values.size == 1) {
            canvas.drawCircle((left + right) / 2f, (top + bottom) / 2f, stroke * 1.4f, dotPaint)
            return
        }
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 0f
        val span = max - min
        fun xAt(index: Int): Float {
            val step = (right - left) / (values.size - 1).toFloat()
            return left + step * index
        }
        fun yAt(value: Float): Float {
            if (span == 0f) return bottom - (bottom - top) * 0.35f
            return bottom - ((value - min) / span) * (bottom - top)
        }
        linePath.reset()
        fillPath.reset()
        values.forEachIndexed { index, value ->
            val x = xAt(index)
            val y = yAt(value)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(xAt(values.lastIndex), bottom)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
        val lastX = xAt(values.lastIndex)
        val lastY = yAt(values.last())
        canvas.drawCircle(lastX, lastY, stroke * 1.35f, dotPaint)
    }
}
