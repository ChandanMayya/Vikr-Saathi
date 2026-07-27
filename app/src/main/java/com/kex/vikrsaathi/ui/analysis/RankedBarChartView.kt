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

class RankedBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var items: List<RankedBarItem> = emptyList()

    private val barColors = intArrayOf(
        R.color.orange_700,
        R.color.orange_600,
        R.color.orange_500,
        R.color.orange_400,
        R.color.orange_300
    )

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary_dark)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            11f,
            resources.displayMetrics
        )
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_accent)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            10f,
            resources.displayMetrics
        )
        textAlign = Paint.Align.RIGHT
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barRect = RectF()

    private val rowHeightPx = 28f * resources.displayMetrics.density
    private val barHeightPx = 14f * resources.displayMetrics.density
    private val verticalPaddingPx = 4f * resources.displayMetrics.density
    private val labelColumnFraction = 0.38f
    private val valueColumnWidthPx = 72f * resources.displayMetrics.density

    fun setItems(newItems: List<RankedBarItem>) {
        items = newItems
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = max(items.size, 1)
        val height = ((rows * rowHeightPx) + verticalPaddingPx * 2).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val chartLeft = width * labelColumnFraction + verticalPaddingPx
        val chartRight = width - valueColumnWidthPx - verticalPaddingPx
        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
        val labelMaxWidth = width * labelColumnFraction - verticalPaddingPx * 2

        items.forEachIndexed { index, item ->
            val rowTop = verticalPaddingPx + index * rowHeightPx
            val centerY = rowTop + rowHeightPx / 2f

            val label = truncateText(item.label, labelPaint, labelMaxWidth)
            val labelBaseline = centerY - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(label, verticalPaddingPx, labelBaseline, labelPaint)

            val barWidth = chartWidth * item.barFraction.coerceIn(0f, 1f)
            val barTop = centerY - barHeightPx / 2f
            barPaint.color = ContextCompat.getColor(
                context,
                barColors.getOrElse(index) { barColors.last() }
            )
            barRect.set(chartLeft, barTop, chartLeft + barWidth, barTop + barHeightPx)
            canvas.drawRoundRect(barRect, barHeightPx / 4f, barHeightPx / 4f, barPaint)

            val valueBaseline = centerY - (valuePaint.descent() + valuePaint.ascent()) / 2f
            canvas.drawText(item.valueLabel, width - verticalPaddingPx, valueBaseline, valuePaint)
        }
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText("${text.substring(0, end)}…") > maxWidth) {
            end--
        }
        return "${text.substring(0, end)}…"
    }
}
