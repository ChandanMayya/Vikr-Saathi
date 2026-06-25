package com.kex.vikrsaathi.ui.settings.invoicebuilder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.domain.template.GridSnapper
import com.kex.vikrsaathi.domain.template.TemplateRenderer
import com.kex.vikrsaathi.domain.template.TemplateRenderContext
import kotlin.math.min

class TemplateCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onElementSelected(elementId: String?)
        fun onElementBoundsChangeStarted(elementId: String)
        fun onElementBoundsChanged(elementId: String, bounds: ElementBounds)
        fun onElementBoundsChangeFinished(elementId: String, bounds: ElementBounds)
    }

    var listener: Listener? = null
    var showPreview: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var showGrid: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var snapToGrid: Boolean = true
    var validationElementIds: Set<String> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    private var template: InvoiceTemplate? = null
    private var selectedId: String? = null
    private var renderContext: TemplateRenderContext? = null
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private var activeElementId: String? = null
    private var mode = TouchMode.NONE
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var activeStartBounds: ElementBounds? = null
    private var pendingBounds: ElementBounds? = null

    private val pagePaint = Paint().apply { color = Color.WHITE }
    private val pageBorderPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1f
    }
    private val elementFillPaint = Paint().apply { alpha = 60 }
    private val elementBorderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val selectedPaint = Paint().apply {
        color = Color.parseColor("#F57C00")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val warningPaint = Paint().apply {
        color = Color.parseColor("#D32F2F")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 24f
    }
    private val handlePaint = Paint().apply {
        color = Color.parseColor("#F57C00")
        style = Paint.Style.FILL
    }

    private val templateRenderer = TemplateRenderer()

    private enum class TouchMode { NONE, DRAG, RESIZE }

    fun setTemplate(template: InvoiceTemplate?, selectedElementId: String?) {
        this.template = template
        this.selectedId = selectedElementId
        computeScale()
        invalidate()
    }

    fun setRenderContext(context: TemplateRenderContext?) {
        this.renderContext = context
        invalidate()
    }

    fun pageToScreen(bounds: ElementBounds): RectF {
        return RectF(
            offsetX + bounds.x * scale,
            offsetY + bounds.y * scale,
            offsetX + (bounds.x + bounds.width) * scale,
            offsetY + (bounds.y + bounds.height) * scale
        )
    }

    fun screenToPage(x: Float, y: Float): Pair<Float, Float> {
        return Pair((x - offsetX) / scale, (y - offsetY) / scale)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeScale()
    }

    private fun computeScale() {
        val t = template ?: return
        val availableW = width - paddingLeft - paddingRight
        val availableH = height - paddingTop - paddingBottom
        if (availableW <= 0 || availableH <= 0) return
        scale = min(
            availableW / t.pageWidthPt.toFloat(),
            availableH / t.pageHeightPt.toFloat()
        )
        val pageScreenW = t.pageWidthPt * scale
        val pageScreenH = t.pageHeightPt * scale
        offsetX = paddingLeft + (availableW - pageScreenW) / 2f
        offsetY = paddingTop + (availableH - pageScreenH) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = template ?: return
        if (scale == 0f) computeScale()

        val pageRect = RectF(
            offsetX,
            offsetY,
            offsetX + t.pageWidthPt * scale,
            offsetY + t.pageHeightPt * scale
        )
        canvas.drawRect(pageRect, pagePaint)
        canvas.drawRect(pageRect, pageBorderPaint)

        if (showGrid && !showPreview) {
            drawGrid(canvas, t, pageRect)
        }

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        if (showPreview) {
            drawLivePreview(canvas, t)
        } else {
            drawBuilder(canvas, t)
        }

        canvas.restore()

        if (!showPreview) {
            drawSelectionOverlay(canvas, t)
        }
    }

    private fun drawGrid(canvas: Canvas, template: InvoiceTemplate, pageRect: RectF) {
        val grid = GridSnapper.GRID_SIZE * scale
        var x = pageRect.left
        while (x <= pageRect.right) {
            canvas.drawLine(x, pageRect.top, x, pageRect.bottom, gridPaint)
            x += grid
        }
        var y = pageRect.top
        while (y <= pageRect.bottom) {
            canvas.drawLine(pageRect.left, y, pageRect.right, y, gridPaint)
            y += grid
        }
    }

    private fun drawLivePreview(canvas: Canvas, template: InvoiceTemplate) {
        val ctx = renderContext ?: return
        templateRenderer.render(canvas, template, ctx)
    }

    private fun drawBuilder(canvas: Canvas, template: InvoiceTemplate) {
        template.elements.sortedBy { it.zIndex }.forEach { element ->
            if (!element.visible) return@forEach
            val rect = element.bounds
            elementFillPaint.color = colorForKind(element.kind.name)
            canvas.drawRect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, elementFillPaint)
            elementBorderPaint.color = if (validationElementIds.contains(element.id)) {
                Color.parseColor("#D32F2F")
            } else {
                Color.GRAY
            }
            canvas.drawRect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, elementBorderPaint)
            canvas.drawText(
                element.kind.name,
                rect.x + 4f,
                rect.y + 14f,
                labelPaint.apply { textSize = 12f }
            )
        }
    }

    private fun drawSelectionOverlay(canvas: Canvas, template: InvoiceTemplate) {
        val selected = template.elements.find { it.id == selectedId } ?: return
        val rect = pageToScreen(selected.bounds)
        canvas.drawRect(rect, selectedPaint)
        val handle = 24f
        canvas.drawRect(
            rect.right - handle,
            rect.bottom - handle,
            rect.right,
            rect.bottom,
            handlePaint
        )
        if (validationElementIds.contains(selected.id)) {
            canvas.drawRect(rect, warningPaint)
        }
    }

    private fun colorForKind(kind: String): Int {
        return when (kind) {
            "IMAGE" -> Color.parseColor("#BBDEFB")
            "TEXT" -> Color.parseColor("#C8E6C9")
            "TABLE" -> Color.parseColor("#FFE0B2")
            "LINE" -> Color.parseColor("#E0E0E0")
            "RECT" -> Color.parseColor("#F8BBD0")
            else -> Color.parseColor("#FFF9C4")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (showPreview) return false
        val t = template ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTouchX = event.x
                downTouchY = event.y
                val hit = findElementAt(event.x, event.y, t)
                if (hit != null) {
                    activeElementId = hit.id
                    selectedId = hit.id
                    listener?.onElementSelected(hit.id)
                    listener?.onElementBoundsChangeStarted(hit.id)
                    val rect = pageToScreen(hit.bounds)
                    val handle = 24f
                    val onHandle = event.x >= rect.right - handle && event.y >= rect.bottom - handle
                    mode = if (onHandle && hit.id == selectedId) TouchMode.RESIZE else TouchMode.DRAG
                    activeStartBounds = hit.bounds
                    pendingBounds = hit.bounds
                } else {
                    activeElementId = null
                    selectedId = null
                    mode = TouchMode.NONE
                    listener?.onElementSelected(null)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val elementId = activeElementId ?: return true
                val start = activeStartBounds ?: return true
                val totalDx = (event.x - downTouchX) / scale
                val totalDy = (event.y - downTouchY) / scale
                val newBounds = when (mode) {
                    TouchMode.DRAG -> start.copy(
                        x = (start.x + totalDx).coerceIn(0f, t.pageWidthPt - start.width),
                        y = (start.y + totalDy).coerceIn(0f, t.pageHeightPt - start.height)
                    )
                    TouchMode.RESIZE -> start.copy(
                        width = (start.width + totalDx).coerceAtLeast(20f)
                            .coerceAtMost(t.pageWidthPt - start.x),
                        height = (start.height + totalDy).coerceAtLeast(12f)
                            .coerceAtMost(t.pageHeightPt - start.y)
                    )
                    TouchMode.NONE -> return true
                }
                pendingBounds = newBounds
                listener?.onElementBoundsChanged(elementId, newBounds)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val elementId = activeElementId
                val finalBounds = pendingBounds
                if (elementId != null && finalBounds != null) {
                    val snapped = GridSnapper.snapBounds(finalBounds, snapToGrid)
                    listener?.onElementBoundsChanged(elementId, snapped)
                    listener?.onElementBoundsChangeFinished(elementId, snapped)
                }
                mode = TouchMode.NONE
                activeElementId = null
                activeStartBounds = null
                pendingBounds = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findElementAt(screenX: Float, screenY: Float, template: InvoiceTemplate): TemplateElement? {
        return template.elements
            .filter { it.visible }
            .sortedByDescending { it.zIndex }
            .firstOrNull { element ->
                val rect = pageToScreen(element.bounds)
                rect.contains(screenX, screenY)
            }
    }
}
