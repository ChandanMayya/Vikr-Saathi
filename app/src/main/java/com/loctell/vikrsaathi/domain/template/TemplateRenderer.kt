package com.loctell.vikrsaathi.domain.template

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.loctell.vikrsaathi.data.model.template.ElementBinding
import com.loctell.vikrsaathi.data.model.template.ElementKind
import com.loctell.vikrsaathi.data.model.template.FontFamily
import com.loctell.vikrsaathi.data.model.template.InvoiceTemplate
import com.loctell.vikrsaathi.data.model.template.TableColumn
import com.loctell.vikrsaathi.data.model.template.TemplateElement
import com.loctell.vikrsaathi.data.model.template.TemplateJsonCodec
import com.loctell.vikrsaathi.data.model.template.TextAlign
import com.loctell.vikrsaathi.data.model.template.VerticalAlign

class TemplateRenderer(
    private val bindingResolver: TemplateBindingResolver = TemplateBindingResolver()
) {

    fun resolveTableRows(context: TemplateRenderContext): List<TableRowData> {
        return bindingResolver.resolveTableRows(context)
    }

    fun render(canvas: Canvas, template: InvoiceTemplate, context: TemplateRenderContext) {
        template.sortedElements.forEach { element ->
            when (element.kind) {
                ElementKind.IMAGE -> drawImage(canvas, element, context)
                ElementKind.TEXT -> drawText(canvas, element, context)
                ElementKind.LINE -> drawLine(canvas, element)
                ElementKind.RECT -> drawRect(canvas, element)
                ElementKind.TABLE -> drawTable(canvas, element, context)
                ElementKind.SPACER -> Unit
            }
        }
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
        val scaled = scaleBitmap(bitmap, bounds.width.toInt(), bounds.height.toInt())
        val drawX = alignedContentX(scaled.width.toFloat(), bounds, element.style.textAlign)
        val drawY = alignedContentY(scaled.height.toFloat(), bounds, element.style.verticalAlign)
        canvas.drawBitmap(scaled, drawX, drawY, null)
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

        val rows = bindingResolver.resolveTableRows(context)
        val bounds = element.bounds
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
            color = Color.BLACK
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.BLACK
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1f
        }

        var y = bounds.y
        val rowHeight = 18f

        canvas.drawLine(bounds.x, y, bounds.x + bounds.width, y, linePaint)
        y += 16f

        if (element.content["showHeader"] == "true") {
            drawTableRow(canvas, columns, columns.associate { it.key to it.label }, bounds, y, headerPaint)
            y += 8f
            canvas.drawLine(bounds.x, y, bounds.x + bounds.width, y, linePaint)
            y += 16f
        }

        rows.forEach { row ->
            if (y + rowHeight > bounds.y + bounds.height) return
            drawTableRow(canvas, columns, row.values, bounds, y, cellPaint)
            y += rowHeight
        }

        canvas.drawLine(bounds.x, y.coerceAtMost(bounds.y + bounds.height), bounds.x + bounds.width, y, linePaint)
    }

    private fun drawTableRow(
        canvas: Canvas,
        columns: List<TableColumn>,
        values: Map<String, String>,
        bounds: com.loctell.vikrsaathi.data.model.template.ElementBounds,
        y: Float,
        paint: Paint
    ) {
        var x = bounds.x
        columns.forEach { column ->
            val colWidth = bounds.width * (column.widthPercent / 100f)
            val value = truncate(values[column.key].orEmpty(), 28)
            val drawX = when (column.align) {
                TextAlign.LEFT -> x + 2f
                TextAlign.CENTER -> x + (colWidth - paint.measureText(value)) / 2f
                TextAlign.RIGHT -> x + colWidth - paint.measureText(value) - 2f
            }
            canvas.drawText(value, drawX, y, paint)
            x += colWidth
        }
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        bounds: com.loctell.vikrsaathi.data.model.template.ElementBounds,
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

    private fun createTypeface(style: com.loctell.vikrsaathi.data.model.template.ElementStyle): Typeface {
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
        bounds: com.loctell.vikrsaathi.data.model.template.ElementBounds,
        align: TextAlign
    ): Float {
        return alignedContentX(paint.measureText(text), bounds, align)
    }

    private fun alignedContentX(contentWidth: Float, bounds: com.loctell.vikrsaathi.data.model.template.ElementBounds, align: TextAlign): Float {
        return when (align) {
            TextAlign.LEFT -> bounds.x
            TextAlign.CENTER -> bounds.x + (bounds.width - contentWidth) / 2f
            TextAlign.RIGHT -> bounds.x + bounds.width - contentWidth
        }
    }

    private fun alignedContentY(contentHeight: Float, bounds: com.loctell.vikrsaathi.data.model.template.ElementBounds, align: VerticalAlign): Float {
        return when (align) {
            VerticalAlign.TOP -> bounds.y
            VerticalAlign.CENTER -> bounds.y + (bounds.height - contentHeight) / 2f
            VerticalAlign.BOTTOM -> bounds.y + bounds.height - contentHeight
        }
    }

    private fun textBaselineY(
        paint: Paint,
        bounds: com.loctell.vikrsaathi.data.model.template.ElementBounds,
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

    private fun scaleBitmap(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (maxWidth <= 0 || maxHeight <= 0) return source
        val ratio = minOf(
            maxWidth.toFloat() / source.width,
            maxHeight.toFloat() / source.height
        )
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun truncate(text: String, max: Int): String {
        return if (text.length <= max) text else text.take(max - 3) + "..."
    }
}
