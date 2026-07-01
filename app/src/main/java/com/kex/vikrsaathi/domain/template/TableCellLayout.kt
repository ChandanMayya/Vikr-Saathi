package com.kex.vikrsaathi.domain.template

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.TableColumn
import com.kex.vikrsaathi.data.model.template.TextAlign

object TableCellLayout {

    private const val CELL_HORIZONTAL_PADDING = 4f
    private const val LINE_GAP = 2f
    private const val ROW_EXTRA_PADDING = 6f
    private val wrapColumnKeys = setOf("name")

    fun normalizeColumns(columns: List<TableColumn>): List<TableColumn> {
        val total = columns.sumOf { it.widthPercent.toDouble() }.toFloat()
        if (total <= 0f || kotlin.math.abs(total - 100f) < 0.01f) return columns
        return columns.map { column ->
            column.copy(widthPercent = column.widthPercent / total * 100f)
        }
    }

    fun columnWidth(tableWidth: Float, column: TableColumn): Float =
        tableWidth * (column.widthPercent / 100f)

    fun lineHeight(paint: Paint): Float = paint.textSize + LINE_GAP

    fun rowBaselineY(rowTopY: Float, paint: Paint): Float =
        rowTopY + ROW_EXTRA_PADDING / 2f + paint.textSize

    fun drawTableGrid(
        canvas: Canvas,
        columns: List<TableColumn>,
        bounds: ElementBounds,
        topY: Float,
        bottomY: Float,
        rowSeparatorYs: List<Float>,
        borderWidthPt: Float
    ) {
        if (bottomY <= topY) return
        val strokeWidth = borderWidthPt.coerceAtLeast(0.25f)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.strokeWidth = strokeWidth
            style = Paint.Style.STROKE
        }
        val left = bounds.x
        val right = bounds.x + bounds.width
        canvas.drawRect(left, topY, right, bottomY, borderPaint)

        rowSeparatorYs
            .filter { separatorY -> separatorY > topY + 0.5f && separatorY < bottomY - 0.5f }
            .forEach { separatorY ->
                canvas.drawLine(left, separatorY, right, separatorY, borderPaint)
            }

        val normalized = normalizeColumns(columns)
        var x = left
        normalized.dropLast(1).forEach { column ->
            x += columnWidth(bounds.width, column)
            canvas.drawLine(x, topY, x, bottomY, borderPaint)
        }
    }

    fun measureRowHeight(
        columns: List<TableColumn>,
        values: Map<String, String>,
        tableWidth: Float,
        paint: Paint
    ): Float {
        val normalized = normalizeColumns(columns)
        val maxLines = normalized.maxOf { column ->
            val colWidth = columnWidth(tableWidth, column)
            val text = values[column.key].orEmpty()
            if (shouldWrap(column.key)) {
                wrapTextToLines(text, paint, colWidth).size
            } else {
                1
            }
        }.coerceAtLeast(1)
        return ROW_EXTRA_PADDING + maxLines * lineHeight(paint)
    }

    fun drawTableRow(
        canvas: Canvas,
        columns: List<TableColumn>,
        values: Map<String, String>,
        bounds: ElementBounds,
        baselineY: Float,
        paint: Paint
    ) {
        val normalized = normalizeColumns(columns)
        var x = bounds.x
        normalized.forEach { column ->
            val colWidth = columnWidth(bounds.width, column)
            val text = values[column.key].orEmpty()
            val lines = if (shouldWrap(column.key)) {
                wrapTextToLines(text, paint, colWidth)
            } else {
                listOf(text)
            }
            var lineY = baselineY
            lines.forEach { line ->
                val drawX = cellAlignedX(paint, line, x, colWidth, column.align)
                canvas.drawText(line, drawX, lineY, paint)
                lineY += lineHeight(paint)
            }
            x += colWidth
        }
    }

    fun wrapTextToLines(text: String, paint: Paint, columnWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val maxWidth = (columnWidth - CELL_HORIZONTAL_PADDING).coerceAtLeast(1f)
        if (paint.measureText(text) <= maxWidth) return listOf(text)

        val lines = mutableListOf<String>()
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        var current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder()
            }
        }

        for (word in words) {
            if (paint.measureText(word) > maxWidth) {
                flush()
                var start = 0
                while (start < word.length) {
                    var end = start + 1
                    while (end <= word.length &&
                        paint.measureText(word.substring(start, end)) <= maxWidth
                    ) {
                        end++
                    }
                    val pieceEnd = if (end > start + 1) end - 1 else start + 1
                    lines.add(word.substring(start, pieceEnd))
                    start = pieceEnd
                }
                continue
            }

            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth) {
                flush()
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        flush()
        return lines.ifEmpty { listOf(text) }
    }

    private fun shouldWrap(columnKey: String): Boolean = columnKey in wrapColumnKeys

    private fun cellAlignedX(
        paint: Paint,
        text: String,
        columnLeft: Float,
        columnWidth: Float,
        align: TextAlign
    ): Float {
        val textWidth = paint.measureText(text)
        val innerLeft = columnLeft + 2f
        return when (align) {
            TextAlign.LEFT -> innerLeft
            TextAlign.CENTER -> columnLeft + (columnWidth - textWidth) / 2f
            TextAlign.RIGHT -> columnLeft + columnWidth - textWidth - 2f
        }
    }
}
