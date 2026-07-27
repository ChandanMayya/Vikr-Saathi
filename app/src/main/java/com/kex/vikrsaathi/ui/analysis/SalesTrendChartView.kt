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
import com.kex.vikrsaathi.data.model.analytics.SalesTrendPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class SalesTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points: List<SalesTrendPoint> = emptyList()
    private val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

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

    fun setPoints(newPoints: List<SalesTrendPoint>) {
        points = newPoints
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
        if (points.isEmpty()) return

        val chartTop = topPaddingPx
        val chartBottom = height - bottomLabelHeightPx
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val maxRevenue = points.maxOf { it.revenue }.coerceAtLeast(1.0)
        val slotWidth = (width - horizontalPaddingPx * 2) / points.size.coerceAtLeast(1)
        val barWidth = (slotWidth * 0.6f).coerceAtLeast(4f)

        points.forEachIndexed { index, point ->
            val centerX = horizontalPaddingPx + slotWidth * index + slotWidth / 2f
            val barFraction = (point.revenue / maxRevenue).toFloat()
            val barHeight = max(barFraction * chartHeight, if (point.revenue > 0) minBarHeightPx else 0f)
            val left = centerX - barWidth / 2f
            val top = chartBottom - barHeight
            barRect.set(left, top, left + barWidth, chartBottom)
            canvas.drawRoundRect(barRect, barWidth / 4f, barWidth / 4f, barPaint)

            if (shouldShowLabel(index, points.size)) {
                val label = dateFormat.format(Date(point.dayStartMillis))
                val baseline = height - 4f * resources.displayMetrics.density
                canvas.drawText(label, centerX, baseline, labelPaint)
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
