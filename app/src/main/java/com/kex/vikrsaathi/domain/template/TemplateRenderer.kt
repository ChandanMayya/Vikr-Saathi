package com.kex.vikrsaathi.domain.template

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.kex.vikrsaathi.data.model.template.ElementBinding
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.data.model.template.FontFamily
import com.kex.vikrsaathi.data.model.template.ImageScaleMode
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.TableColumn
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.data.model.template.TemplateJsonCodec
import com.kex.vikrsaathi.data.model.template.TextAlign
import com.kex.vikrsaathi.data.model.template.VerticalAlign

class TemplateRenderer(
    private val bindingResolver: TemplateBindingResolver = TemplateBindingResolver()
) {

    fun resolveTableRows(
        context: TemplateRenderContext,
        tableElement: TemplateElement? = null
    ): List<TableRowData> {
        if (tableElement == null) {
            return bindingResolver.resolveTableRows(context)
        }
        val columnsJson = tableElement.content["columns"].orEmpty()
        val columns = TemplateJsonCodec.tableColumnsFromJson(columnsJson)
        return bindingResolver.resolveTableRows(
            context = context,
            columns = columns,
            showTotalRow = TableTotalRowSettings.showTotalRow(tableElement.content),
            totalRowLabel = TableTotalRowSettings.totalRowLabel(tableElement.content)
        )
    }

    fun render(canvas: Canvas, template: InvoiceTemplate, context: TemplateRenderContext) {
        template.sortedElements.forEach { element ->
            canvas.withElementRotation(element) {
                when (element.kind) {
                    ElementKind.IMAGE -> drawImage(this, element, context)
                    ElementKind.TEXT -> drawText(this, element, context)
                    ElementKind.LINE -> drawLine(this, element)
                    ElementKind.RECT -> drawRect(this, element)
                    ElementKind.TABLE -> drawTable(this, element, context)
                    ElementKind.SPACER -> Unit
                }
            }
        }
    }

    private inline fun Canvas.withElementRotation(element: TemplateElement, block: Canvas.() -> Unit) {
        val degrees = element.rotationDegrees
        if (degrees == 0f) {
            block()
            return
        }
        val bounds = element.bounds
        val pivotX = bounds.x + bounds.width / 2f
        val pivotY = bounds.y + bounds.height / 2f
        save()
        rotate(degrees, pivotX, pivotY)
        block()
        restore()
    }

    private fun drawImage(canvas: Canvas, element: TemplateElement, context: TemplateRenderContext) {
        val bitmap = when (element.binding) {
            ElementBinding.DYNAMIC -> {
                val key = bindingResolver.parseBindingKey(element.content["bindingKey"]) ?: return
                bindingResolver.resolveImage(key, context)
            }
            ElementBinding.STATIC -> context.staticImages[element.id]
        } ?: return

        val bounds = element.bounds
        val clipBounds = RectF(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)
        val dest = computeImageDestRect(
            bitmap = bitmap,
            bounds = bounds,
            scaleMode = element.style.imageScaleMode,
            textAlign = element.style.textAlign,
            verticalAlign = element.style.verticalAlign
        )
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val renderScale = context.imageRenderScale.coerceAtLeast(1f)
        val targetW = (dest.width() * renderScale).toInt().coerceAtLeast(1)
        val targetH = (dest.height() * renderScale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)

        canvas.save()
        canvas.clipRect(clipBounds)
        canvas.drawBitmap(scaled, null, dest, bitmapPaint)
        canvas.restore()

        if (scaled !== bitmap) scaled.recycle()
    }

    private fun computeImageDestRect(
        bitmap: Bitmap,
        bounds: com.kex.vikrsaathi.data.model.template.ElementBounds,
        scaleMode: ImageScaleMode,
        textAlign: TextAlign,
        verticalAlign: VerticalAlign
    ): RectF {
        val bitmapW = bitmap.width.toFloat()
        val bitmapH = bitmap.height.toFloat()
        if (bitmapW <= 0f || bitmapH <= 0f) {
            return RectF(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)
        }

        val (drawW, drawH) = when (scaleMode) {
            ImageScaleMode.FIT -> {
                val ratio = minOf(bounds.width / bitmapW, bounds.height / bitmapH)
                bitmapW * ratio to bitmapH * ratio
            }
            ImageScaleMode.FILL -> {
                val ratio = maxOf(bounds.width / bitmapW, bounds.height / bitmapH)
                bitmapW * ratio to bitmapH * ratio
            }
            ImageScaleMode.STRETCH -> bounds.width to bounds.height
            ImageScaleMode.FIT_WIDTH -> {
                val ratio = bounds.width / bitmapW
                bitmapW * ratio to bitmapH * ratio
            }
            ImageScaleMode.FIT_HEIGHT -> {
                val ratio = bounds.height / bitmapH
                bitmapW * ratio to bitmapH * ratio
            }
        }

        val left = alignedContentX(drawW, bounds, textAlign)
        val top = alignedContentY(drawH, bounds, verticalAlign)
        return RectF(left, top, left + drawW, top + drawH)
    }

    private fun drawText(canvas: Canvas, element: TemplateElement, context: TemplateRenderContext) {
        val prefix = element.content["prefix"].orEmpty()
        val wrap = element.content["wrap"] == "true"
        val text = when (element.binding) {
            ElementBinding.STATIC -> element.content["text"].orEmpty()
            ElementBinding.DYNAMIC -> {
                val key = bindingResolver.parseBindingKey(element.content["bindingKey"]) ?: return
                prefix + bindingResolver.resolveText(key, context)
            }
        }
        if (text.isBlank()) return

        val paint = createTextPaint(element)
        val bounds = element.bounds
        if (wrap) {
            drawWrappedText(canvas, text, bounds, paint, element.style.textAlign)
        } else {
            val x = alignedX(paint, text, bounds, element.style.textAlign)
            val y = textBaselineY(paint, bounds, element.style.verticalAlign)
            canvas.drawText(text, x, y, paint)
        }
    }

    private fun drawLine(canvas: Canvas, element: TemplateElement) {
        val paint = Paint().apply {
            color = parseColor(element.style.color)
            strokeWidth = element.bounds.height.coerceAtLeast(1f)
        }
        val y = element.bounds.y
        canvas.drawLine(
            element.bounds.x,
            y,
            element.bounds.x + element.bounds.width,
            y,
            paint
        )
    }

    private fun drawRect(canvas: Canvas, element: TemplateElement) {
        val paint = Paint().apply {
            color = parseColor(element.style.color)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRect(
            element.bounds.x,
            element.bounds.y,
            element.bounds.x + element.bounds.width,
            element.bounds.y + element.bounds.height,
            paint
        )
    }

    private fun drawTable(canvas: Canvas, element: TemplateElement, context: TemplateRenderContext) {
        val columnsJson = element.content["columns"] ?: return
        val columns = TemplateJsonCodec.tableColumnsFromJson(columnsJson)
        if (columns.isEmpty()) return

        val rows = bindingResolver.resolveTableRows(
            context = context,
            columns = columns,
            showTotalRow = TableTotalRowSettings.showTotalRow(element.content),
            totalRowLabel = TableTotalRowSettings.totalRowLabel(element.content)
        )
        val bounds = element.bounds
        val baseSize = element.style.fontSize.coerceAtLeast(4f)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = baseSize
            color = Color.BLACK
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = baseSize
            color = Color.BLACK
        }
        val totalRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = baseSize
            color = Color.BLACK
        }

        val rowSeparators = mutableListOf<Float>()
        var y = bounds.y
        val tableTop = bounds.y
        val borderWidthPt = TableBorderSettings.strokePt(element.content)

        if (element.content["showHeader"] == "true") {
            val headerValues = columns.associate { it.key to it.label }
            val headerHeight = TableCellLayout.measureRowHeight(
                columns, headerValues, bounds.width, headerPaint
            )
            TableCellLayout.drawTableRow(
                canvas,
                columns,
                headerValues,
                bounds,
                TableCellLayout.rowBaselineY(y, headerPaint),
                headerPaint
            )
            y += headerHeight
            rowSeparators.add(y)
        }

        rows.forEach { row ->
            val rowPaint = if (row.isTotalRow) totalRowPaint else cellPaint
            val rowHeight = TableCellLayout.measureRowHeight(columns, row.values, bounds.width, rowPaint)
            if (y + rowHeight > bounds.y + bounds.height) {
                TableCellLayout.drawTableGrid(
                    canvas, columns, bounds, tableTop, y, rowSeparators, borderWidthPt
                )
                return
            }
            TableCellLayout.drawTableRow(
                canvas,
                columns,
                row.values,
                bounds,
                TableCellLayout.rowBaselineY(y, rowPaint),
                rowPaint
            )
            y += rowHeight
            rowSeparators.add(y)
        }

        val tableBottom = y.coerceAtMost(bounds.y + bounds.height)
        TableCellLayout.drawTableGrid(
            canvas, columns, bounds, tableTop, tableBottom, rowSeparators, borderWidthPt
        )
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        bounds: com.kex.vikrsaathi.data.model.template.ElementBounds,
        paint: Paint,
        align: TextAlign
    ) {
        val words = text.split(" ")
        var line = StringBuilder()
        var y = bounds.y + paint.textSize
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > bounds.width) {
                val lineText = line.toString()
                val x = alignedX(paint, lineText, bounds, align)
                canvas.drawText(lineText, x, y, paint)
                y += paint.textSize + 4f
                if (y > bounds.y + bounds.height) return
                line = StringBuilder(word)
            } else {
                line = StringBuilder(test)
            }
        }
        if (line.isNotEmpty() && y <= bounds.y + bounds.height) {
            val lineText = line.toString()
            val x = alignedX(paint, lineText, bounds, align)
            canvas.drawText(lineText, x, y, paint)
        }
    }

    private fun createTextPaint(element: TemplateElement): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = element.style.fontSize
            color = parseColor(element.style.color)
            typeface = createTypeface(element.style)
            isUnderlineText = element.style.underline
        }
    }

    private fun createTypeface(style: com.kex.vikrsaathi.data.model.template.ElementStyle): Typeface {
        val base = when (style.fontFamily) {
            FontFamily.SERIF -> Typeface.SERIF
            FontFamily.SANS_SERIF -> Typeface.SANS_SERIF
            FontFamily.MONOSPACE -> Typeface.MONOSPACE
            FontFamily.DEFAULT -> Typeface.DEFAULT
        }
        val faceStyle = when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, faceStyle)
    }

    private fun alignedX(
        paint: Paint,
        text: String,
        bounds: com.kex.vikrsaathi.data.model.template.ElementBounds,
        align: TextAlign
    ): Float {
        return alignedContentX(paint.measureText(text), bounds, align)
    }

    private fun alignedContentX(contentWidth: Float, bounds: com.kex.vikrsaathi.data.model.template.ElementBounds, align: TextAlign): Float {
        return when (align) {
            TextAlign.LEFT -> bounds.x
            TextAlign.CENTER -> bounds.x + (bounds.width - contentWidth) / 2f
            TextAlign.RIGHT -> bounds.x + bounds.width - contentWidth
        }
    }

    private fun alignedContentY(contentHeight: Float, bounds: com.kex.vikrsaathi.data.model.template.ElementBounds, align: VerticalAlign): Float {
        return when (align) {
            VerticalAlign.TOP -> bounds.y
            VerticalAlign.CENTER -> bounds.y + (bounds.height - contentHeight) / 2f
            VerticalAlign.BOTTOM -> bounds.y + bounds.height - contentHeight
        }
    }

    private fun textBaselineY(
        paint: Paint,
        bounds: com.kex.vikrsaathi.data.model.template.ElementBounds,
        verticalAlign: VerticalAlign
    ): Float {
        val textHeight = paint.descent() - paint.ascent()
        return when (verticalAlign) {
            VerticalAlign.TOP -> bounds.y - paint.ascent()
            VerticalAlign.CENTER -> bounds.y + (bounds.height - textHeight) / 2f - paint.ascent()
            VerticalAlign.BOTTOM -> bounds.y + bounds.height - paint.descent()
        }
    }

    private fun parseColor(hex: String): Int {
        return runCatching { Color.parseColor(hex) }.getOrDefault(Color.BLACK)
    }
}
