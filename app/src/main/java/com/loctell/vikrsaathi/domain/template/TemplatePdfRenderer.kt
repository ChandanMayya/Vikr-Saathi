package com.loctell.vikrsaathi.domain.template

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.loctell.vikrsaathi.data.model.template.ElementBounds
import com.loctell.vikrsaathi.data.model.template.ElementKind
import com.loctell.vikrsaathi.data.model.template.InvoiceTemplate
import com.loctell.vikrsaathi.data.model.template.TableColumn
import com.loctell.vikrsaathi.data.model.template.TemplateElement
import com.loctell.vikrsaathi.data.model.template.TemplateJsonCodec
import com.loctell.vikrsaathi.data.model.template.TextAlign

/**
 * Renders templates to PDF with multi-page support for overflowing item tables.
 */
object TemplatePdfRenderer {

    private const val ROW_HEIGHT = 18f

    fun render(document: PdfDocument, template: InvoiceTemplate, context: TemplateRenderContext) {
        val renderer = TemplateRenderer()
        val tableElements = template.elements.filter { it.kind == ElementKind.TABLE && it.visible }
        val otherElements = template.elements.filter { it.kind != ElementKind.TABLE && it.visible }

        var pageNumber = 1
        var page = document.startPage(createPageInfo(template, pageNumber))
        var canvas = page.canvas

        val templateWithoutTables = template.copy(elements = otherElements)
        renderer.render(canvas, templateWithoutTables, context)

        tableElements.forEach { tableElement ->
            val rows = renderer.resolveTableRows(context)
            var rowIndex = 0
            var startY = tableElement.bounds.y

            while (rowIndex < rows.size) {
                val result = drawTableSection(
                    canvas = canvas,
                    element = tableElement,
                    rows = rows,
                    startRowIndex = rowIndex,
                    startY = startY,
                    pageBottom = template.pageHeightPt - template.marginBottom,
                    includeHeader = rowIndex == 0 || startY == template.marginTop
                )
                rowIndex = result.nextRowIndex
                if (result.needsNewPage && rowIndex < rows.size) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(createPageInfo(template, pageNumber))
                    canvas = page.canvas
                    startY = template.marginTop
                } else {
                    break
                }
            }
        }

        document.finishPage(page)
    }

    private data class TableSectionResult(
        val nextRowIndex: Int,
        val needsNewPage: Boolean
    )

    private fun drawTableSection(
        canvas: Canvas,
        element: TemplateElement,
        rows: List<TableRowData>,
        startRowIndex: Int,
        startY: Float,
        pageBottom: Float,
        includeHeader: Boolean
    ): TableSectionResult {
        val columnsJson = element.content["columns"] ?: return TableSectionResult(rows.size, false)
        val columns = TemplateJsonCodec.tableColumnsFromJson(columnsJson)
        if (columns.isEmpty()) return TableSectionResult(rows.size, false)

        val bounds = element.bounds
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFakeBoldText = true
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

        var y = startY
        val maxY = minOf(pageBottom, bounds.y + bounds.height)

        if (includeHeader) {
            canvas.drawLine(bounds.x, y, bounds.x + bounds.width, y, linePaint)
            y += 16f
            if (element.content["showHeader"] == "true") {
                drawTableRow(canvas, columns, columns.associate { it.key to it.label }, bounds, y, headerPaint)
                y += 8f
                canvas.drawLine(bounds.x, y, bounds.x + bounds.width, y, linePaint)
                y += 16f
            }
        }

        var index = startRowIndex
        while (index < rows.size) {
            if (y + ROW_HEIGHT > maxY) {
                return TableSectionResult(index, needsNewPage = true)
            }
            drawTableRow(canvas, columns, rows[index].values, bounds, y, cellPaint)
            y += ROW_HEIGHT
            index++
        }

        canvas.drawLine(bounds.x, y.coerceAtMost(maxY), bounds.x + bounds.width, y, linePaint)
        return TableSectionResult(index, needsNewPage = false)
    }

    private fun drawTableRow(
        canvas: Canvas,
        columns: List<TableColumn>,
        values: Map<String, String>,
        bounds: ElementBounds,
        y: Float,
        paint: Paint
    ) {
        var x = bounds.x
        columns.forEach { column ->
            val colWidth = bounds.width * (column.widthPercent / 100f)
            val value = truncate(values[column.key].orEmpty(), 28)
            val drawX = when (column.align) {
                TextAlign.LEFT -> x + 2f
                TextAlign.CENTER ->
                    x + (colWidth - paint.measureText(value)) / 2f
                TextAlign.RIGHT ->
                    x + colWidth - paint.measureText(value) - 2f
            }
            canvas.drawText(value, drawX, y, paint)
            x += colWidth
        }
    }

    private fun createPageInfo(template: InvoiceTemplate, pageNumber: Int): PdfDocument.PageInfo {
        return PdfDocument.PageInfo.Builder(
            template.pageWidthPt,
            template.pageHeightPt,
            pageNumber
        ).create()
    }

    private fun truncate(text: String, max: Int): String {
        return if (text.length <= max) text else text.take(max - 3) + "..."
    }
}
