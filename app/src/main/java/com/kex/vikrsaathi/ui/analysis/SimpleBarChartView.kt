package com.kex.vikrsaathi.ui.analysis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.kex.vikrsaathi.R
import kotlin.math.max

class SimpleBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var labels: List<String> = emptyList()
    private var values: List<Double> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.orange_600)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary_dark)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            9f,
            resources.displayMetrics
        )
        textAlign = Paint.Align.CENTER
    }

    private val barRect = RectF()
    private val horizontalPaddingPx = 8f * resources.displayMetrics.density
    private val bottomLabelHeightPx = 18f * resources.displayMetrics.density
    private val topPaddingPx = 8f * resources.displayMetrics.density
    private val minBarHeightPx = 2f * resources.displayMetrics.density

    fun setData(newLabels: List<String>, newValues: List<Double>) {
        labels = newLabels
        values = newValues
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (120f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (labels.isEmpty() || values.isEmpty()) return

        val chartTop = topPaddingPx
        val chartBottom = height - bottomLabelHeightPx
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val maxValue = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val count = minOf(labels.size, values.size)
        val slotWidth = (width - horizontalPaddingPx * 2) / count.coerceAtLeast(1)
        val barWidth = (slotWidth * 0.6f).coerceAtLeast(4f)

        for (index in 0 until count) {
            val centerX = horizontalPaddingPx + slotWidth * index + slotWidth / 2f
            val barFraction = (values[index] / maxValue).toFloat()
            val barHeight = max(
                barFraction * chartHeight,
                if (values[index] > 0) minBarHeightPx else 0f
            )
            val left = centerX - barWidth / 2f
            val top = chartBottom - barHeight
            barRect.set(left, top, left + barWidth, chartBottom)
            canvas.drawRoundRect(barRect, barWidth / 4f, barWidth / 4f, barPaint)

            if (shouldShowLabel(index, count)) {
                val baseline = height - 4f * resources.displayMetrics.density
                canvas.drawText(labels[index], centerX, baseline, labelPaint)
            }
        }
    }

    private fun shouldShowLabel(index: Int, total: Int): Boolean {
        if (total <= 7) return true
        if (total <= 14) return index % 2 == 0
        if (total <= 24) return index % 3 == 0 || index == total - 1
        return index % 4 == 0 || index == total - 1
    }
}
