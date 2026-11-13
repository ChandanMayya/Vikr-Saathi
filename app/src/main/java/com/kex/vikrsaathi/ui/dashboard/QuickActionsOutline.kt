package com.kex.vikrsaathi.ui.dashboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * The tray's only outline. The stroke is inset by half its width so the whole
 * line stays inside the tray, following the same top corners as the surface.
 * [visibleHeightPx] keeps the bottom edge on the part of the sheet that is on screen.
 */
internal class QuickActionsOutline(
    private val cornerRadiusPx: Float,
    private val thicknessPx: Float
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thicknessPx
    }
    private val path = Path()
    private val radii = FloatArray(8)

    var color: Int = Color.TRANSPARENT
        set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    /** Visible sheet height in pixels. Zero uses the full drawable bounds. */
    var visibleHeightPx: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        if (Color.alpha(color) == 0 || thicknessPx <= 0f) return
        val inset = thicknessPx / 2f
        val left = bounds.left + inset
        val top = bounds.top + inset
        val right = bounds.right - inset
        val visibleBottom = if (visibleHeightPx > 0f) {
            bounds.top + visibleHeightPx
        } else {
            bounds.bottom.toFloat()
        }
        val bottom = visibleBottom.coerceAtMost(bounds.bottom.toFloat()) - inset
        if (right <= left || bottom <= top) return
        val radius = (cornerRadiusPx - inset).coerceAtLeast(0f)
            .coerceAtMost((right - left) / 2f)
            .coerceAtMost((bottom - top) / 2f)
        radii[0] = radius
        radii[1] = radius
        radii[2] = radius
        radii[3] = radius
        radii[4] = 0f
        radii[5] = 0f
        radii[6] = 0f
        radii[7] = 0f
        path.rewind()
        path.addRoundRect(left, top, right, bottom, radii, Path.Direction.CW)
        paint.color = color
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
