package com.kex.vikrsaathi.domain.template

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.data.model.template.TemplateJsonCodec

/**
 * Renders templates to PDF with multi-page support for overflowing item tables.
 */
object TemplatePdfRenderer {

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

        val rowSeparators = mutableListOf<Float>()
        var y = startY
        val sectionTop = startY
        val maxY = minOf(pageBottom, bounds.y + bounds.height)
        val borderWidthPt = TableBorderSettings.strokePt(element.content)

        if (includeHeader && element.content["showHeader"] == "true") {
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

        var index = startRowIndex
        while (index < rows.size) {
            val rowHeight = TableCellLayout.measureRowHeight(
                columns, rows[index].values, bounds.width, cellPaint
            )
            if (y + rowHeight > maxY) {
                TableCellLayout.drawTableGrid(
                    canvas, columns, bounds, sectionTop, y, rowSeparators, borderWidthPt
                )
                return TableSectionResult(index, needsNewPage = true)
            }
            TableCellLayout.drawTableRow(
                canvas,
                columns,
                rows[index].values,
                bounds,
                TableCellLayout.rowBaselineY(y, cellPaint),
                cellPaint
            )
            y += rowHeight
            rowSeparators.add(y)
            index++
        }

        val sectionBottom = y.coerceAtMost(maxY)
        TableCellLayout.drawTableGrid(
            canvas, columns, bounds, sectionTop, sectionBottom, rowSeparators, borderWidthPt
        )
        return TableSectionResult(index, needsNewPage = false)
    }

    private fun createPageInfo(template: InvoiceTemplate, pageNumber: Int): PdfDocument.PageInfo {
        return PdfDocument.PageInfo.Builder(
            template.pageWidthPt,
            template.pageHeightPt,
            pageNumber
        ).create()
    }
}
