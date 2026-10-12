package com.kex.vikrsaathi.ui.analysis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.kex.vikrsaathi.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PurchaseLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var days: List<Long> = emptyList()
    private var values: List<Double> = emptyList()
    private val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    private val linePath = Path()
    private val fillPath = Path()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.orange_700)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_line_fill)
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.orange_700)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary_dark)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
    }

    private val horizontalPaddingPx = 12f * resources.displayMetrics.density
    private val bottomLabelHeightPx = 18f * resources.displayMetrics.density
    private val topPaddingPx = 12f * resources.displayMetrics.density
    private val dotRadiusPx = 3f * resources.displayMetrics.density

    fun setSeries(dayStarts: List<Long>, amounts: List<Double>) {
        days = dayStarts
        values = amounts
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (140f * resources.displayMetrics.density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = minOf(days.size, values.size)
        if (count == 0) return

        val chartTop = topPaddingPx
        val chartBottom = height - bottomLabelHeightPx
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val maxValue = values.take(count).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val slotWidth = (width - horizontalPaddingPx * 2) / count

        linePath.reset()
        fillPath.reset()
        var lastX = horizontalPaddingPx
        for (index in 0 until count) {
            val x = horizontalPaddingPx + slotWidth * index + slotWidth / 2f
            val plot = values[index].coerceAtLeast(0.0)
            val y = chartBottom - (plot / maxValue).toFloat() * chartHeight
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, chartBottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastX = x
        }
        fillPath.lineTo(lastX, chartBottom)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        for (index in 0 until count) {
            val x = horizontalPaddingPx + slotWidth * index + slotWidth / 2f
            val plot = values[index].coerceAtLeast(0.0)
            val y = chartBottom - (plot / maxValue).toFloat() * chartHeight
            if (count <= 14 || values[index] > 0.0) {
                canvas.drawCircle(x, y, dotRadiusPx, dotPaint)
            }
            if (shouldShowLabel(index, count)) {
                val baseline = height - 4f * resources.displayMetrics.density
                canvas.drawText(dateFormat.format(Date(days[index])), x, baseline, labelPaint)
            }
        }
    }

    private fun shouldShowLabel(index: Int, total: Int): Boolean {
        if (total <= 7) return true
        if (total <= 14) return index % 2 == 0
        if (total <= 31) return index % 4 == 0 || index == total - 1
        return index % 7 == 0 || index == total - 1
    }
}
