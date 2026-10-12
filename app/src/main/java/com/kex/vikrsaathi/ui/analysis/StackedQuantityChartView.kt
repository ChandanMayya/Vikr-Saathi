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

data class StackedQuantityBar(
    val label: String,
    val accepted: Int,
    val damaged: Int,
    val rejected: Int
) {
    val total: Int get() = accepted + damaged + rejected
}

class StackedQuantityChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var items: List<StackedQuantityBar> = emptyList()
    private val barRect = RectF()

    private val acceptedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_accepted)
    }
    private val damagedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_damaged)
    }
    private val rejectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_rejected)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary_dark)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_accent)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        textAlign = Paint.Align.RIGHT
    }

    private val rowHeightPx = 28f * resources.displayMetrics.density
    private val barHeightPx = 14f * resources.displayMetrics.density
    private val verticalPaddingPx = 4f * resources.displayMetrics.density
    private val labelColumnFraction = 0.38f
    private val valueColumnWidthPx = 48f * resources.displayMetrics.density

    fun setItems(newItems: List<StackedQuantityBar>) {
        items = newItems
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = max(items.size, 1)
        setMeasuredDimension(width, ((rows * rowHeightPx) + verticalPaddingPx * 2).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return
        val maxTotal = items.maxOf { it.total }.coerceAtLeast(1)
        val chartLeft = width * labelColumnFraction + verticalPaddingPx
        val chartRight = width - valueColumnWidthPx - verticalPaddingPx
        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
        val labelMaxWidth = width * labelColumnFraction - verticalPaddingPx * 2

        items.forEachIndexed { index, item ->
            val rowTop = verticalPaddingPx + index * rowHeightPx
            val centerY = rowTop + rowHeightPx / 2f
            val labelBaseline = centerY - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(truncate(item.label, labelMaxWidth), verticalPaddingPx, labelBaseline, labelPaint)

            val barWidth = chartWidth * (item.total.toFloat() / maxTotal)
            val barTop = centerY - barHeightPx / 2f
            var cursor = chartLeft
            val segments = listOf(
                item.accepted to acceptedPaint,
                item.damaged to damagedPaint,
                item.rejected to rejectedPaint
            )
            segments.forEach { (qty, paint) ->
                if (qty <= 0 || item.total <= 0) return@forEach
                val segmentWidth = barWidth * qty / item.total
                barRect.set(cursor, barTop, cursor + segmentWidth, barTop + barHeightPx)
                canvas.drawRect(barRect, paint)
                cursor += segmentWidth
            }

            val valueBaseline = centerY - (valuePaint.descent() + valuePaint.ascent()) / 2f
            canvas.drawText(item.total.toString(), width - verticalPaddingPx, valueBaseline, valuePaint)
        }
    }

    private fun truncate(text: String, maxWidth: Float): String {
        if (labelPaint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && labelPaint.measureText("${text.substring(0, end)}…") > maxWidth) end--
        return "${text.substring(0, end)}…"
    }
}
