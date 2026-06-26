package com.kex.vikrsaathi.ui.settings.invoicebuilder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.GuideOrientation
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.data.model.template.TemplateGuide
import com.kex.vikrsaathi.domain.template.ElementBoundsHelper
import com.kex.vikrsaathi.domain.template.GridSnapper
import com.kex.vikrsaathi.domain.template.GuideSnapper
import com.kex.vikrsaathi.domain.template.AlignmentDistanceLabel
import com.kex.vikrsaathi.domain.template.ObjectAlignmentLine
import com.kex.vikrsaathi.domain.template.ObjectAlignmentSnapper
import com.kex.vikrsaathi.domain.template.AlignmentAxis
import com.kex.vikrsaathi.domain.template.TemplateRenderer
import com.kex.vikrsaathi.domain.template.TemplateRenderContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.min
import kotlin.math.round

class TemplateCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onElementSelected(elementId: String?)
        fun onToggleSelection(elementId: String)
        fun onClearSelection()
        fun onLockedElementTapped()
        fun onElementBoundsChangeStarted(elementId: String, isResize: Boolean)
        fun onElementBoundsChangeFinished(elementId: String, bounds: ElementBounds)
        fun onGuideSelected(guideId: String)
        fun onGuideDragStarted(guideId: String)
        fun onGuidePositionChanged(guideId: String, positionPt: Float)
        fun onGuideDragFinished(guideId: String, positionPt: Float)
    }

    var listener: Listener? = null
    var isGestureActive: Boolean = false
        private set

    var showPreview: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var previewGesturesEnabled: Boolean = false
    var showGrid: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var snapToGrid: Boolean = true
    var snapToGuides: Boolean = true
    var snapToObjects: Boolean = true
    var showGuides: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    var multiSelectMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var validationElementIds: Set<String> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            if (!isGestureActive) invalidate()
        }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val dragThresholdPx get() = touchSlop * 1.5f

    private var template: InvoiceTemplate? = null
    private var selectedIds: Set<String> = emptySet()
    private var selectedGuideId: String? = null
    private var renderContext: TemplateRenderContext? = null

    private var fitScale = 1f
    private var zoomFactor = 1f
    private var panOffsetX = 0f
    private var panOffsetY = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    private val scale: Float
        get() = fitScale * zoomFactor

    private var activeElementId: String? = null
    private var activeGuideId: String? = null
    private var liveGuidePositionPt: Float? = null
    private var mode = TouchMode.NONE
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var hasDragged = false
    private var gestureCommitted = false
    private var downHitId: String? = null
    private var dragAnchorScreenX = 0f
    private var dragAnchorScreenY = 0f
    private var smoothedTouchX = 0f
    private var smoothedTouchY = 0f
    private var touchFilterActive = false

    private var dragPageOffsetX = 0f
    private var dragPageOffsetY = 0f
    private var pendingDragX = 0f
    private var pendingDragY = 0f
    private var dragFrameScheduled = false

    private var dragTargetIds: Set<String> = emptySet()
    private var dragStartElementBounds: Map<String, ElementBounds> = emptyMap()
    private var dragStartUnion: ElementBounds? = null
    private var liveUnion: ElementBounds? = null
    private var activeAlignmentLines: List<ObjectAlignmentLine> = emptyList()

    private var isZoomGesture = false
    private var lastPinchFocusX = 0f
    private var lastPinchFocusY = 0f

    private val choreographer = Choreographer.getInstance()

    private val dragFrameCallback = Choreographer.FrameCallback {
        dragFrameScheduled = false
        if (!isGestureActive || mode != TouchMode.DRAG) return@FrameCallback
        val t = template ?: return@FrameCallback
        if (applyDragTouch(pendingDragX, pendingDragY, t.pageWidthPt, t.pageHeightPt)) {
            invalidate()
        }
    }

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                cancelActiveElementGesture()
                lastPinchFocusX = detector.focusX
                lastPinchFocusY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = scale
                zoomFactor = (zoomFactor * detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                val newScale = scale
                if (oldScale > 0f && newScale > 0f) {
                    val focusX = detector.focusX
                    val focusY = detector.focusY
                    val pageX = (focusX - offsetX) / oldScale
                    val pageY = (focusY - offsetY) / oldScale
                    val base = computeBaseOffset(newScale)
                    offsetX = focusX - pageX * newScale
                    offsetY = focusY - pageY * newScale
                    panOffsetX = offsetX - base.first
                    panOffsetY = offsetY - base.second
                }
                panBy(detector.focusX - lastPinchFocusX, detector.focusY - lastPinchFocusY)
                lastPinchFocusX = detector.focusX
                lastPinchFocusY = detector.focusY
                clampPan()
                invalidate()
                return true
            }
        }
    )

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
    private val groupPaint = Paint().apply {
        color = Color.parseColor("#7B1FA2")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
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
    private val lockLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        textSize = 14f
    }
    private val guidePaint = Paint().apply {
        color = Color.parseColor("#0288D1")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
    }
    private val selectedGuidePaint = Paint().apply {
        color = Color.parseColor("#E91E63")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val alignmentLinePaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val alignmentDimensionPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val alignmentLabelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val alignmentLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }

    private val templateRenderer = TemplateRenderer()

    private enum class TouchMode { NONE, DRAG, RESIZE, PAN, GUIDE_DRAG }

    fun setTemplate(
        template: InvoiceTemplate?,
        selectedElementIds: Set<String>,
        multiSelectMode: Boolean = false,
        selectedGuideId: String? = null
    ) {
        if (isGestureActive) return
        this.template = template
        this.selectedIds = selectedElementIds
        this.multiSelectMode = multiSelectMode
        this.selectedGuideId = selectedGuideId
        updateOffsets()
        invalidate()
    }

    fun setRenderContext(context: TemplateRenderContext?) {
        this.renderContext = context
        if (!isGestureActive) invalidate()
    }

    fun pageToScreen(bounds: ElementBounds): RectF {
        return RectF(
            offsetX + bounds.x * scale,
            offsetY + bounds.y * scale,
            offsetX + (bounds.x + bounds.width) * scale,
            offsetY + (bounds.y + bounds.height) * scale
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeFitScale()
    }

    private fun computeFitScale() {
        val t = template ?: return
        val availableW = width - paddingLeft - paddingRight
        val availableH = height - paddingTop - paddingBottom
        if (availableW <= 0 || availableH <= 0) return
        fitScale = min(
            availableW / t.pageWidthPt.toFloat(),
            availableH / t.pageHeightPt.toFloat()
        )
        clampPan()
        updateOffsets()
    }

    private fun computeBaseOffset(currentScale: Float = scale): Pair<Float, Float> {
        val t = template ?: return 0f to 0f
        val availableW = width - paddingLeft - paddingRight
        val availableH = height - paddingTop - paddingBottom
        val pageScreenW = t.pageWidthPt * currentScale
        val pageScreenH = t.pageHeightPt * currentScale
        val baseX = paddingLeft + (availableW - pageScreenW) / 2f
        val baseY = paddingTop + (availableH - pageScreenH) / 2f
        return baseX to baseY
    }

    private fun updateOffsets() {
        val (baseX, baseY) = computeBaseOffset()
        offsetX = baseX + panOffsetX
        offsetY = baseY + panOffsetY
    }

    private fun panBy(dx: Float, dy: Float) {
        panOffsetX += dx
        panOffsetY += dy
        updateOffsets()
    }

    private fun clampPan() {
        val t = template ?: return
        val availableW = width - paddingLeft - paddingRight
        val availableH = height - paddingTop - paddingBottom
        val pageScreenW = t.pageWidthPt * scale
        val pageScreenH = t.pageHeightPt * scale
        val (baseX, baseY) = computeBaseOffset()

        if (pageScreenW <= availableW) {
            panOffsetX = 0f
        } else {
            val minPan = availableW - pageScreenW - baseX
            val maxPan = -baseX
            panOffsetX = panOffsetX.coerceIn(minPan, maxPan)
        }

        if (pageScreenH <= availableH) {
            panOffsetY = 0f
        } else {
            val minPan = availableH - pageScreenH - baseY
            val maxPan = -baseY
            panOffsetY = panOffsetY.coerceIn(minPan, maxPan)
        }
        updateOffsets()
    }

    private fun cancelActiveElementGesture() {
        if (dragFrameScheduled) {
            choreographer.removeFrameCallback(dragFrameCallback)
            dragFrameScheduled = false
        }
        if (layerType != LAYER_TYPE_NONE) {
            setLayerType(LAYER_TYPE_NONE, null)
        }
        mode = TouchMode.NONE
        activeElementId = null
        activeGuideId = null
        liveGuidePositionPt = null
        dragTargetIds = emptySet()
        dragStartElementBounds = emptyMap()
        dragStartUnion = null
        liveUnion = null
        downHitId = null
        hasDragged = false
        gestureCommitted = false
        dragAnchorScreenX = 0f
        dragAnchorScreenY = 0f
        dragPageOffsetX = 0f
        dragPageOffsetY = 0f
        touchFilterActive = false
        activeAlignmentLines = emptyList()
        isGestureActive = false
    }

    private fun referenceBoundsExcludingDrag(): List<ElementBounds> {
        val t = template ?: return emptyList()
        return t.elements
            .filter { it.visible && it.id !in dragTargetIds }
            .map { it.bounds }
    }

    private fun resolvedLiveUnion(pageWidth: Int, pageHeight: Int): ElementBounds? {
        val start = dragStartUnion ?: return null
        var union = clampUnionDrag(
            start.copy(x = start.x + dragPageOffsetX, y = start.y + dragPageOffsetY),
            pageWidth,
            pageHeight
        )
        if (snapToObjects) {
            val result = ObjectAlignmentSnapper.snapBounds(
                union,
                referenceBoundsExcludingDrag(),
                enabled = true
            )
            union = result.bounds
            activeAlignmentLines = result.lines
        } else {
            activeAlignmentLines = emptyList()
        }
        if (snapToGuides) {
            val guides = template?.guides.orEmpty()
            union = GuideSnapper.snapBounds(union, guides, true)
        }
        return union
    }

    private fun applySnap(bounds: ElementBounds): ElementBounds {
        var snapped = GridSnapper.snapBounds(bounds, snapToGrid)
        if (snapToObjects) {
            snapped = ObjectAlignmentSnapper.snapBounds(
                snapped,
                referenceBoundsExcludingDrag(),
                enabled = true
            ).bounds
        }
        return GuideSnapper.snapBounds(snapped, template?.guides.orEmpty(), snapToGuides)
    }

    private fun scheduleDragUpdate(screenX: Float, screenY: Float) {
        pendingDragX = screenX
        pendingDragY = screenY
        if (!dragFrameScheduled) {
            dragFrameScheduled = true
            choreographer.postFrameCallback(dragFrameCallback)
        }
    }

    private fun smoothTouch(screenX: Float, screenY: Float): Pair<Float, Float> {
        if (!touchFilterActive) {
            smoothedTouchX = screenX
            smoothedTouchY = screenY
            touchFilterActive = true
            return screenX to screenY
        }
        smoothedTouchX += TOUCH_SMOOTHING_ALPHA * (screenX - smoothedTouchX)
        smoothedTouchY += TOUCH_SMOOTHING_ALPHA * (screenY - smoothedTouchY)
        return smoothedTouchX to smoothedTouchY
    }

    private fun commitDragAnchor(screenX: Float, screenY: Float) {
        dragAnchorScreenX = screenX
        dragAnchorScreenY = screenY
        dragPageOffsetX = 0f
        dragPageOffsetY = 0f
        touchFilterActive = false
    }

    private fun applyDragTouch(
        screenX: Float,
        screenY: Float,
        pageWidth: Int,
        pageHeight: Int
    ): Boolean {
        val start = dragStartUnion ?: return false
        val (sx, sy) = smoothTouch(screenX, screenY)
        val snappedScreenDx = round(sx - dragAnchorScreenX)
        val snappedScreenDy = round(sy - dragAnchorScreenY)
        val newOffsetX = snappedScreenDx / scale
        val newOffsetY = snappedScreenDy / scale
        if (newOffsetX == dragPageOffsetX && newOffsetY == dragPageOffsetY) {
            return false
        }
        dragPageOffsetX = newOffsetX
        dragPageOffsetY = newOffsetY
        liveUnion = resolvedLiveUnion(pageWidth, pageHeight)
        return true
    }

    private fun resolveDragTargets(hit: TemplateElement, template: InvoiceTemplate): Set<String> {
        return if (selectedIds.contains(hit.id) && selectedIds.isNotEmpty()) {
            selectedIds
        } else {
            setOf(hit.id)
        }
    }

    private fun beginElementGesture(anchorId: String) {
        if (gestureCommitted) return
        gestureCommitted = true
        isGestureActive = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
        listener?.onElementBoundsChangeStarted(anchorId, false)
    }

    private fun clampUnionDrag(union: ElementBounds, pageWidth: Int, pageHeight: Int): ElementBounds {
        return union.copy(
            x = union.x.coerceIn(0f, pageWidth - union.width),
            y = union.y.coerceIn(0f, pageHeight - union.height)
        )
    }

    private fun boundsForElement(element: TemplateElement): ElementBounds {
        if (!isGestureActive || element.id !in dragTargetIds) {
            return element.bounds
        }
        val start = dragStartElementBounds[element.id] ?: return element.bounds
        val t = template ?: return start
        val union = resolvedLiveUnion(t.pageWidthPt, t.pageHeightPt) ?: return start
        val dragStart = dragStartUnion ?: return start
        val dx = union.x - dragStart.x
        val dy = union.y - dragStart.y
        return clampUnionDrag(
            start.copy(x = start.x + dx, y = start.y + dy),
            t.pageWidthPt,
            t.pageHeightPt
        )
    }

    private fun selectionUnionBounds(template: InvoiceTemplate): ElementBounds? {
        if (isGestureActive) {
            return resolvedLiveUnion(template.pageWidthPt, template.pageHeightPt)
        }
        return ElementBoundsHelper.unionBounds(template.elements, selectedIds)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = template ?: return
        if (fitScale == 0f) computeFitScale()

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
            drawGuides(canvas, t)
        }

        canvas.restore()

        if (!showPreview) {
            drawSelectionOverlay(canvas, t)
            drawAlignmentLines(canvas)
        }
    }

    private fun drawAlignmentLines(canvas: Canvas) {
        if (activeAlignmentLines.isEmpty()) return
        activeAlignmentLines.forEach { line ->
            when (line.axis) {
                AlignmentAxis.VERTICAL -> canvas.drawLine(
                    offsetX + line.positionPt * scale,
                    offsetY + line.spanStartPt * scale,
                    offsetX + line.positionPt * scale,
                    offsetY + line.spanEndPt * scale,
                    alignmentLinePaint
                )
                AlignmentAxis.HORIZONTAL -> canvas.drawLine(
                    offsetX + line.spanStartPt * scale,
                    offsetY + line.positionPt * scale,
                    offsetX + line.spanEndPt * scale,
                    offsetY + line.positionPt * scale,
                    alignmentLinePaint
                )
            }
            line.distanceLabel?.let { label ->
                drawAlignmentDistanceLabel(canvas, line.axis, line.positionPt, label)
            }
        }
    }

    private fun drawAlignmentDistanceLabel(
        canvas: Canvas,
        alignmentAxis: AlignmentAxis,
        anchorPt: Float,
        label: AlignmentDistanceLabel
    ) {
        val text = label.distancePt.roundToInt().toString()
        val labelTextSize = (11f * scale).coerceIn(10f, 18f)
        val tickHalf = 4f * scale
        val dimOffset = 12f * scale

        when (alignmentAxis) {
            AlignmentAxis.VERTICAL -> {
                val dimX = offsetX + (anchorPt + 12f) * scale
                val y1 = offsetY + label.gapStartPt * scale
                val y2 = offsetY + label.gapEndPt * scale
                canvas.drawLine(dimX, y1, dimX, y2, alignmentDimensionPaint)
                canvas.drawLine(dimX - tickHalf, y1, dimX + tickHalf, y1, alignmentDimensionPaint)
                canvas.drawLine(dimX - tickHalf, y2, dimX + tickHalf, y2, alignmentDimensionPaint)
                drawAlignmentLabelPill(canvas, dimX + dimOffset, (y1 + y2) / 2f, text, labelTextSize)
            }
            AlignmentAxis.HORIZONTAL -> {
                val dimY = offsetY + (anchorPt + 12f) * scale
                val x1 = offsetX + label.gapStartPt * scale
                val x2 = offsetX + label.gapEndPt * scale
                canvas.drawLine(x1, dimY, x2, dimY, alignmentDimensionPaint)
                canvas.drawLine(x1, dimY - tickHalf, x1, dimY + tickHalf, alignmentDimensionPaint)
                canvas.drawLine(x2, dimY - tickHalf, x2, dimY + tickHalf, alignmentDimensionPaint)
                drawAlignmentLabelPill(canvas, (x1 + x2) / 2f, dimY - dimOffset, text, labelTextSize)
            }
        }
    }

    private fun drawAlignmentLabelPill(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        text: String,
        textSize: Float
    ) {
        alignmentLabelTextPaint.textSize = textSize
        val textWidth = alignmentLabelTextPaint.measureText(text)
        val padX = 6f * scale
        val padY = 3f * scale
        val fm = alignmentLabelTextPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val rect = RectF(
            centerX - textWidth / 2f - padX,
            centerY - textHeight / 2f - padY,
            centerX + textWidth / 2f + padX,
            centerY + textHeight / 2f + padY
        )
        val corner = 4f * scale
        canvas.drawRoundRect(rect, corner, corner, alignmentLabelBgPaint)
        canvas.drawText(
            text,
            centerX,
            centerY - (fm.ascent + fm.descent) / 2f,
            alignmentLabelTextPaint
        )
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
            val rect = boundsForElement(element)
            elementFillPaint.color = colorForKind(element.kind.name)
            canvas.drawRect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, elementFillPaint)
            elementBorderPaint.color = when {
                validationElementIds.contains(element.id) -> Color.parseColor("#D32F2F")
                element.locked -> Color.parseColor("#9E9E9E")
                else -> Color.GRAY
            }
            canvas.drawRect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, elementBorderPaint)
            canvas.drawText(
                element.kind.name,
                rect.x + 4f,
                rect.y + 14f,
                labelPaint.apply { textSize = 12f }
            )
            if (element.locked) {
                canvas.drawText(
                    "🔒",
                    rect.x + rect.width - 16f,
                    rect.y + 14f,
                    lockLabelPaint
                )
            }
            if (!element.groupId.isNullOrBlank() && element.id !in selectedIds) {
                canvas.drawRect(
                    rect.x,
                    rect.y,
                    rect.x + rect.width,
                    rect.y + rect.height,
                    groupPaint
                )
            }
        }
    }

    private fun drawGuides(canvas: Canvas, template: InvoiceTemplate) {
        if (!showGuides || template.guides.isEmpty()) return
        template.guides.forEach { guide ->
            val position = guidePositionForDraw(guide)
            val paint = if (guide.id == selectedGuideId || guide.id == activeGuideId) {
                selectedGuidePaint
            } else {
                guidePaint
            }
            when (guide.orientation) {
                GuideOrientation.VERTICAL -> canvas.drawLine(
                    position,
                    0f,
                    position,
                    template.pageHeightPt.toFloat(),
                    paint
                )
                GuideOrientation.HORIZONTAL -> canvas.drawLine(
                    0f,
                    position,
                    template.pageWidthPt.toFloat(),
                    position,
                    paint
                )
            }
        }
    }

    private fun guidePositionForDraw(guide: TemplateGuide): Float {
        if (guide.id == activeGuideId && liveGuidePositionPt != null) {
            return liveGuidePositionPt!!
        }
        return guide.positionPt
    }

    private fun drawSelectionOverlay(canvas: Canvas, template: InvoiceTemplate) {
        if (selectedIds.isEmpty()) return

        val union = selectionUnionBounds(template) ?: return
        val unionRect = pageToScreen(union)
        canvas.drawRect(unionRect, selectedPaint)

        if (!isGestureActive) {
            val selectedElements = template.elements.filter { it.id in selectedIds && it.visible }
            selectedElements.forEach { element ->
                if (validationElementIds.contains(element.id)) {
                    val rect = pageToScreen(element.bounds)
                    canvas.drawRect(rect, warningPaint)
                }
            }
        }

        val handle = 24f
        canvas.drawRect(
            unionRect.right - handle,
            unionRect.bottom - handle,
            unionRect.right,
            unionRect.bottom,
            handlePaint
        )
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
        if (showPreview && !previewGesturesEnabled) return false
        if (showPreview && previewGesturesEnabled) {
            return handlePreviewTouchEvent(event)
        }
        val t = template ?: return false

        if (event.pointerCount >= 2) {
            scaleGestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    isZoomGesture = true
                    cancelActiveElementGesture()
                    lastPinchFocusX = pinchFocusX(event)
                    lastPinchFocusY = pinchFocusY(event)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 && isZoomGesture) {
                    val focusX = pinchFocusX(event)
                    val focusY = pinchFocusY(event)
                    panBy(focusX - lastPinchFocusX, focusY - lastPinchFocusY)
                    lastPinchFocusX = focusX
                    lastPinchFocusY = focusY
                    clampPan()
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.pointerCount <= 1) {
                    isZoomGesture = false
                }
            }
        }

        if (isZoomGesture || event.pointerCount > 1) {
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTouchX = event.x
                downTouchY = event.y
                hasDragged = false
                gestureCommitted = false
                val hit = findElementAt(event.x, event.y, t)
                downHitId = hit?.id

                if (hit != null && hit.locked) {
                    downHitId = hit.id
                    activeElementId = hit.id
                    mode = TouchMode.NONE
                    if (!multiSelectMode) {
                        listener?.onElementSelected(hit.id)
                    }
                    invalidate()
                    return true
                }

                if (hit != null && multiSelectMode) {
                    activeElementId = hit.id
                    mode = TouchMode.NONE
                    return true
                }

                if (hit != null) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    activeElementId = hit.id
                    dragTargetIds = resolveDragTargets(hit, t)
                    if (dragTargetIds != selectedIds) {
                        listener?.onElementSelected(hit.id)
                    }
                    dragStartElementBounds = t.elements
                        .filter { it.id in dragTargetIds }
                        .associate { it.id to it.bounds }
                    dragStartUnion = ElementBoundsHelper.unionBounds(
                        t.elements.map { element ->
                            if (element.id in dragStartElementBounds) {
                                element.copy(bounds = dragStartElementBounds.getValue(element.id))
                            } else {
                                element
                            }
                        },
                        dragTargetIds
                    ) ?: hit.bounds
                    liveUnion = dragStartUnion
                    mode = TouchMode.DRAG
                } else {
                    activeElementId = null
                    dragTargetIds = emptySet()
                    dragStartElementBounds = emptyMap()
                    dragStartUnion = null
                    liveUnion = null
                    val guideHit = if (showGuides) findGuideAt(event.x, event.y, t) else null
                    if (guideHit != null) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        activeGuideId = guideHit.id
                        liveGuidePositionPt = guideHit.positionPt
                        mode = TouchMode.GUIDE_DRAG
                        listener?.onGuideSelected(guideHit.id)
                    } else {
                        activeGuideId = null
                        liveGuidePositionPt = null
                        mode = if (zoomFactor > 1.05f) TouchMode.PAN else TouchMode.NONE
                        if (!multiSelectMode && mode == TouchMode.NONE) {
                            listener?.onClearSelection()
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == TouchMode.GUIDE_DRAG) {
                    val guideId = activeGuideId ?: return true
                    val guide = t.guides.find { it.id == guideId } ?: return true
                    if (!hasDragged) {
                        val dist = hypot(event.x - downTouchX, event.y - downTouchY)
                        if (dist < dragThresholdPx) return true
                        hasDragged = true
                        isGestureActive = true
                        listener?.onGuideDragStarted(guideId)
                    }
                    val position = guidePositionFromTouch(event.x, event.y, guide, t)
                    liveGuidePositionPt = position
                    listener?.onGuidePositionChanged(guideId, position)
                    invalidate()
                    return true
                }

                if (mode == TouchMode.PAN) {
                    if (!hasDragged) {
                        val dist = hypot(event.x - downTouchX, event.y - downTouchY)
                        if (dist < dragThresholdPx) return true
                        hasDragged = true
                    }
                    panBy(event.x - downTouchX, event.y - downTouchY)
                    downTouchX = event.x
                    downTouchY = event.y
                    clampPan()
                    invalidate()
                    return true
                }

                val elementId = activeElementId ?: return true

                if (!hasDragged) {
                    val dist = hypot(event.x - downTouchX, event.y - downTouchY)
                    if (dist < dragThresholdPx) return true
                    hasDragged = true
                    if (multiSelectMode && downHitId != null) {
                        val hit = t.elements.find { it.id == downHitId } ?: return true
                        dragTargetIds = resolveDragTargets(hit, t)
                        dragStartElementBounds = t.elements
                            .filter { it.id in dragTargetIds }
                            .associate { it.id to it.bounds }
                        dragStartUnion = ElementBoundsHelper.unionBounds(t.elements, dragTargetIds) ?: hit.bounds
                        liveUnion = dragStartUnion
                    }
                    commitDragAnchor(event.x, event.y)
                    beginElementGesture(elementId)
                    scheduleDragUpdate(event.x, event.y)
                    return true
                }

                scheduleDragUpdate(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val elementId = activeElementId
                val guideId = activeGuideId
                val hitId = downHitId
                val wasPan = mode == TouchMode.PAN
                val wasGuideDrag = mode == TouchMode.GUIDE_DRAG

                if (!hasDragged) {
                    when {
                        hitId != null && multiSelectMode -> listener?.onToggleSelection(hitId)
                        hitId != null && !multiSelectMode && t.elements.find { it.id == hitId }?.locked == true ->
                            listener?.onLockedElementTapped()
                        hitId == null && multiSelectMode -> listener?.onClearSelection()
                        wasGuideDrag && guideId != null -> listener?.onGuideSelected(guideId)
                    }
                    cancelActiveElementGesture()
                } else if (wasGuideDrag && guideId != null) {
                    val guide = t.guides.find { it.id == guideId }
                    val position = liveGuidePositionPt
                        ?: guide?.let { guidePositionFromTouch(event.x, event.y, it, t) }
                    cancelActiveElementGesture()
                    if (position != null) {
                        listener?.onGuideDragFinished(guideId, position)
                    }
                } else if (!wasPan && elementId != null) {
                    template?.let {
                        applyDragTouch(event.x, event.y, it.pageWidthPt, it.pageHeightPt)
                    }
                    val bounds = template?.let { resolvedLiveUnion(it.pageWidthPt, it.pageHeightPt) }
                    if (bounds != null) {
                        val snapped = applySnap(bounds)
                        cancelActiveElementGesture()
                        listener?.onElementBoundsChangeFinished(elementId, snapped)
                    } else {
                        cancelActiveElementGesture()
                    }
                } else {
                    cancelActiveElementGesture()
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun pinchFocusX(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getX(i)
        }
        return sum / event.pointerCount
    }

    private fun pinchFocusY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getY(i)
        }
        return sum / event.pointerCount
    }

    private fun handlePreviewTouchEvent(event: MotionEvent): Boolean {
        if (template == null) return false

        if (event.pointerCount >= 2) {
            scaleGestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    isZoomGesture = true
                    lastPinchFocusX = pinchFocusX(event)
                    lastPinchFocusY = pinchFocusY(event)
                }
                return true
            }

            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downTouchX = event.x
                downTouchY = event.y
                hasDragged = false
                isZoomGesture = false
                mode = TouchMode.PAN
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 && isZoomGesture) {
                    val focusX = pinchFocusX(event)
                    val focusY = pinchFocusY(event)
                    panBy(focusX - lastPinchFocusX, focusY - lastPinchFocusY)
                    lastPinchFocusX = focusX
                    lastPinchFocusY = focusY
                    clampPan()
                    invalidate()
                    return true
                }
                if (event.pointerCount == 1 && mode == TouchMode.PAN) {
                    val dist = hypot(event.x - downTouchX, event.y - downTouchY)
                    if (dist >= touchSlop || hasDragged) {
                        hasDragged = true
                        panBy(event.x - downTouchX, event.y - downTouchY)
                        downTouchX = event.x
                        downTouchY = event.y
                        clampPan()
                        invalidate()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.pointerCount <= 1) {
                    isZoomGesture = false
                }
                mode = TouchMode.NONE
                hasDragged = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return isZoomGesture || event.pointerCount > 1
    }

    private fun findGuideAt(screenX: Float, screenY: Float, template: InvoiceTemplate): TemplateGuide? {
        return template.guides
            .sortedByDescending { it.positionPt }
            .firstOrNull { guide ->
                when (guide.orientation) {
                    GuideOrientation.VERTICAL -> {
                        val guideX = offsetX + guide.positionPt * scale
                        abs(screenX - guideX) <= GUIDE_HIT_TOLERANCE_PX &&
                            screenY in offsetY..(offsetY + template.pageHeightPt * scale)
                    }
                    GuideOrientation.HORIZONTAL -> {
                        val guideY = offsetY + guide.positionPt * scale
                        abs(screenY - guideY) <= GUIDE_HIT_TOLERANCE_PX &&
                            screenX in offsetX..(offsetX + template.pageWidthPt * scale)
                    }
                }
            }
    }

    private fun guidePositionFromTouch(
        screenX: Float,
        screenY: Float,
        guide: TemplateGuide,
        template: InvoiceTemplate
    ): Float {
        return when (guide.orientation) {
            GuideOrientation.VERTICAL -> {
                ((screenX - offsetX) / scale).coerceIn(0f, template.pageWidthPt.toFloat())
            }
            GuideOrientation.HORIZONTAL -> {
                ((screenY - offsetY) / scale).coerceIn(0f, template.pageHeightPt.toFloat())
            }
        }
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

    companion object {
        private const val MIN_ZOOM = 0.5f
        private const val MAX_ZOOM = 4f
        private const val TOUCH_SMOOTHING_ALPHA = 0.22f
        private const val GUIDE_HIT_TOLERANCE_PX = 18f
    }
}
