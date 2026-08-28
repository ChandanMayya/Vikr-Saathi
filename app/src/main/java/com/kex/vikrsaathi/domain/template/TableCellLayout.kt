package com.kex.vikrsaathi.domain.template

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.TableColumn
import com.kex.vikrsaathi.data.model.template.TextAlign

object TableCellLayout {

    private const val CELL_HORIZONTAL_PADDING = 6f
    private const val CURRENCY_CELL_HORIZONTAL_PADDING = 8f
    private const val LINE_GAP = 2f
    private const val ROW_EXTRA_PADDING = 6f
    private const val MIN_SCALED_TEXT_SIZE = 7f

    private val singleLineColumnKeys = setOf(
        "sl",
        "sno",
        "sr",
        "#",
        "quantity",
        "qty",
        "discount",
        "disc",
        "mrp",
        "discountAmount",
        "discAmt",
        "discount_amount",
        "roundOff",
        "round_off",
        "lineRoundOff",
        "lineTotal",
        "amount",
        "price",
        "total"
    )

    private data class ResolvedCell(
        val lines: List<String>,
        val paint: Paint
    )

    fun normalizeColumns(columns: List<TableColumn>): List<TableColumn> {
        val total = columns.sumOf { it.widthPercent.toDouble() }.toFloat()
        if (total <= 0f || kotlin.math.abs(total - 100f) < 0.01f) return columns
        return columns.map { column ->
            column.copy(widthPercent = column.widthPercent / total * 100f)
        }
    }

    fun columnWidth(tableWidth: Float, column: TableColumn): Float =
        tableWidth * (column.widthPercent / 100f)

    fun lineHeight(paint: Paint): Float =
        paint.textSize + (paint.textSize * 0.2f).coerceIn(1f, LINE_GAP)

    fun rowBaselineY(rowTopY: Float, paint: Paint): Float {
        val extra = rowExtraPadding(paint)
        return rowTopY + extra / 2f + paint.textSize
    }

    private fun rowExtraPadding(paint: Paint): Float =
        (paint.textSize * 0.55f).coerceIn(2f, ROW_EXTRA_PADDING)

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
        paint: Paint,
        headerRow: Boolean = false
    ): Float {
        val normalized = normalizeColumns(columns)
        val maxLines = normalized.maxOf { column ->
            val colWidth = columnWidth(tableWidth, column)
            val text = values[column.key].orEmpty()
            resolveCell(text, paint, colWidth, column.key, headerRow).lines.size
        }.coerceAtLeast(1)
        return rowExtraPadding(paint) + maxLines * lineHeight(paint)
    }

    fun drawTableRow(
        canvas: Canvas,
        columns: List<TableColumn>,
        values: Map<String, String>,
        bounds: ElementBounds,
        baselineY: Float,
        paint: Paint,
        headerRow: Boolean = false
    ) {
        val normalized = normalizeColumns(columns)
        var x = bounds.x
        normalized.forEach { column ->
            val colWidth = columnWidth(bounds.width, column)
            val text = values[column.key].orEmpty()
            val cell = resolveCell(text, paint, colWidth, column.key, headerRow)
            val rowTop = baselineY - cell.paint.textSize - 2f
            val rowBottom = baselineY + lineHeight(cell.paint) * cell.lines.size
            val save = canvas.save()
            canvas.clipRect(x, rowTop, x + colWidth, rowBottom)
            var lineY = baselineY
            cell.lines.forEach { line ->
                val drawX = cellAlignedX(cell.paint, line, x, colWidth, column.align, column.key)
                canvas.drawText(line, drawX, lineY, cell.paint)
                lineY += lineHeight(cell.paint)
            }
            canvas.restoreToCount(save)
            x += colWidth
        }
    }

    private fun resolveCell(
        text: String,
        paint: Paint,
        columnWidth: Float,
        columnKey: String,
        headerRow: Boolean = false
    ): ResolvedCell {
        return if (!headerRow && columnKey in singleLineColumnKeys) {
            fitSingleLine(text, paint, columnWidth, columnKey)
        } else {
            ResolvedCell(wrapLabelLines(text, paint, columnWidth, columnKey), paint)
        }
    }

    private fun fitSingleLine(
        text: String,
        paint: Paint,
        columnWidth: Float,
        columnKey: String
    ): ResolvedCell {
        if (text.isBlank()) return ResolvedCell(listOf(""), paint)

        val maxWidth = innerTextWidth(columnWidth, columnKey)
        if (paint.measureText(text) <= maxWidth) {
            return ResolvedCell(listOf(text), paint)
        }

        val scaled = Paint(paint)
        var size = paint.textSize
        while (scaled.measureText(text) > maxWidth && size > MIN_SCALED_TEXT_SIZE) {
            size -= 0.5f
            scaled.textSize = size
        }
        return ResolvedCell(listOf(text), scaled)
    }

    fun wrapLabelLines(
        text: String,
        paint: Paint,
        columnWidth: Float,
        columnKey: String = ""
    ): List<String> {
        if (text.isBlank()) return listOf("")

        val maxWidth = innerTextWidth(columnWidth, columnKey)
        val paragraphs = text.split("\n")
        val lines = mutableListOf<String>()
        paragraphs.forEach { paragraph ->
            lines.addAll(wrapWordsOnly(paragraph.trim(), paint, maxWidth))
        }
        return lines.ifEmpty { listOf(text) }
    }

    private fun wrapWordsOnly(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        if (paint.measureText(text) <= maxWidth) return listOf(text)

        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf(text)

        val lines = mutableListOf<String>()
        var current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder()
            }
        }

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                flush()
                current = StringBuilder(word)
            }
        }
        flush()
        return lines.ifEmpty { listOf(text) }
    }

    private fun innerTextWidth(columnWidth: Float, columnKey: String): Float {
        return (columnWidth - horizontalPadding(columnKey, columnWidth) * 2f).coerceAtLeast(1f)
    }

    private fun horizontalPadding(columnKey: String, columnWidth: Float = Float.MAX_VALUE): Float {
        val preferred = if (columnKey in singleLineColumnKeys) {
            CURRENCY_CELL_HORIZONTAL_PADDING
        } else {
            CELL_HORIZONTAL_PADDING
        }
        return preferred.coerceAtMost((columnWidth * 0.1f).coerceAtLeast(2f))
    }

    private fun cellAlignedX(
        paint: Paint,
        text: String,
        columnLeft: Float,
        columnWidth: Float,
        align: TextAlign,
        columnKey: String = ""
    ): Float {
        val textWidth = paint.measureText(text)
        val pad = horizontalPadding(columnKey, columnWidth)
        val innerLeft = columnLeft + pad
        val innerRight = columnLeft + columnWidth - pad
        val innerWidth = (innerRight - innerLeft).coerceAtLeast(1f)
        val rawX = when (align) {
            TextAlign.LEFT -> innerLeft
            TextAlign.CENTER -> innerLeft + (innerWidth - textWidth) / 2f
            TextAlign.RIGHT -> innerRight - textWidth
        }
        val maxX = (innerRight - textWidth).coerceAtLeast(innerLeft)
        return rawX.coerceIn(innerLeft, maxX)
    }
}
