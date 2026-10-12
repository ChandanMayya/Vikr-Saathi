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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class GroupedBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var days: List<Long> = emptyList()
    private var purchases: List<Double> = emptyList()
    private var paid: List<Double> = emptyList()
    private val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    private val barRect = RectF()

    private val purchasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.orange_700)
    }
    private val paidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_paid)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary_dark)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
    }

    private val horizontalPaddingPx = 8f * resources.displayMetrics.density
    private val bottomLabelHeightPx = 18f * resources.displayMetrics.density
    private val topPaddingPx = 8f * resources.displayMetrics.density
    private val minBarHeightPx = 2f * resources.displayMetrics.density

    fun setSeries(dayStarts: List<Long>, purchaseAmounts: List<Double>, paidAmounts: List<Double>) {
        days = dayStarts
        purchases = purchaseAmounts
        paid = paidAmounts
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (140f * resources.displayMetrics.density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = minOf(days.size, purchases.size, paid.size)
        if (count == 0) return

        val chartTop = topPaddingPx
        val chartBottom = height - bottomLabelHeightPx
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val maxValue = (0 until count).maxOf { max(purchases[it], paid[it]) }.coerceAtLeast(1.0)
        val slotWidth = (width - horizontalPaddingPx * 2) / count
        val barWidth = (slotWidth * 0.28f).coerceAtLeast(2f)
        val gap = 2f * resources.displayMetrics.density

        for (index in 0 until count) {
            val centerX = horizontalPaddingPx + slotWidth * index + slotWidth / 2f
            drawBar(canvas, centerX - gap / 2f - barWidth, chartBottom, chartHeight, barWidth, purchases[index], maxValue, purchasePaint)
            drawBar(canvas, centerX + gap / 2f, chartBottom, chartHeight, barWidth, paid[index], maxValue, paidPaint)
            if (shouldShowLabel(index, count)) {
                val baseline = height - 4f * resources.displayMetrics.density
                canvas.drawText(dateFormat.format(Date(days[index])), centerX, baseline, labelPaint)
            }
        }
    }

    private fun drawBar(
        canvas: Canvas,
        left: Float,
        chartBottom: Float,
        chartHeight: Float,
        barWidth: Float,
        value: Double,
        maxValue: Double,
        paint: Paint
    ) {
        val plot = value.coerceAtLeast(0.0)
        val barHeight = max((plot / maxValue).toFloat() * chartHeight, if (plot > 0) minBarHeightPx else 0f)
        barRect.set(left, chartBottom - barHeight, left + barWidth, chartBottom)
        canvas.drawRoundRect(barRect, barWidth / 4f, barWidth / 4f, paint)
    }

    private fun shouldShowLabel(index: Int, total: Int): Boolean {
        if (total <= 7) return true
        if (total <= 14) return index % 2 == 0
        if (total <= 31) return index % 4 == 0 || index == total - 1
        return index % 7 == 0 || index == total - 1
    }
}
