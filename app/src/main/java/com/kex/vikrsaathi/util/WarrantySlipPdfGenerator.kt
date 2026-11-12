package com.kex.vikrsaathi.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.kex.vikrsaathi.data.entity.WarrantyClaim
import com.kex.vikrsaathi.domain.bill.WarrantySlip
import java.io.File
import java.io.FileOutputStream

object WarrantySlipPdfGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842f
    private const val MARGIN = 40f
    private const val LINE = 18f
    private const val VALUE_X = MARGIN + 130f

    internal enum class Kind { TITLE, CONTINUATION, ROW, FOOTER }

    internal data class SlipDraw(val kind: Kind, val text: String, val label: String = "")

    fun generate(context: Context, claim: WarrantyClaim, shopName: String): File {
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F57C00")
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11f
        }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        val body = mutableListOf<SlipDraw>()
        body += SlipDraw(Kind.TITLE, WarrantySlip.TITLE)
        val valueWidth = PAGE_WIDTH - VALUE_X - MARGIN
        WarrantySlip.rows(claim, shopName).forEach { row ->
            wrap(row.value, value, valueWidth).forEachIndexed { index, line ->
                body += SlipDraw(Kind.ROW, line, label = if (index == 0) row.label else "")
            }
        }
        body += SlipDraw(Kind.FOOTER, WarrantySlip.FOOTER)
        val pages = assignPages(body, "${WarrantySlip.TITLE} · ${claim.reference}")

        val document = PdfDocument()
        pages.forEachIndexed { index, lines ->
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), index + 1).create()
            val page = document.startPage(info)
            val canvas = page.canvas
            var y = MARGIN
            lines.forEach { draw ->
                val baseline = if (draw.kind == Kind.FOOTER) y + 8f else y
                when (draw.kind) {
                    Kind.TITLE, Kind.CONTINUATION -> canvas.drawText(draw.text, MARGIN, baseline, title)
                    Kind.ROW -> {
                        if (draw.label.isNotEmpty()) canvas.drawText(draw.label, MARGIN, baseline, label)
                        canvas.drawText(draw.text, VALUE_X, baseline, value)
                    }
                    Kind.FOOTER -> canvas.drawText(draw.text, MARGIN, baseline, footerPaint)
                }
                y += height(draw)
            }
            document.finishPage(page)
        }

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val safeRef = claim.reference.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "Warranty_$safeRef.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    /**
     * Places every row, then the footer. A line that does not fit starts a new page
     * instead of being dropped. Later pages begin with the claim reference.
     */
    internal fun assignPages(body: List<SlipDraw>, continuation: String): List<List<SlipDraw>> {
        if (body.isEmpty()) return emptyList()
        val pages = mutableListOf<MutableList<SlipDraw>>()
        var current = mutableListOf<SlipDraw>()
        var y = MARGIN
        fun breakPage() {
            pages += current
            current = mutableListOf()
            y = MARGIN
            val header = SlipDraw(Kind.CONTINUATION, continuation)
            current += header
            y += height(header)
        }
        body.forEach { item ->
            val need = height(item)
            val onlyHeader = current.size == 1 && current.first().kind == Kind.CONTINUATION
            if (current.isNotEmpty() && !onlyHeader && y + need > PAGE_HEIGHT - MARGIN) {
                breakPage()
            }
            current += item
            y += need
        }
        if (current.isNotEmpty()) pages += current
        return pages
    }

    private fun height(item: SlipDraw): Float = when (item.kind) {
        Kind.TITLE -> LINE + 10f
        Kind.CONTINUATION -> LINE + 8f
        Kind.FOOTER -> LINE + 8f
        Kind.ROW -> LINE
    }

    internal fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val source = text.trim().ifBlank { "—" }
        val lines = mutableListOf<String>()
        var current = ""
        source.split(Regex("\\s+")).forEach { word ->
            var remaining = word
            while (paint.measureText(remaining) > width && remaining.isNotEmpty()) {
                if (current.isNotEmpty()) {
                    lines += current
                    current = ""
                }
                val take = longestFit(remaining, paint, width)
                lines += remaining.substring(0, take)
                remaining = remaining.substring(take)
            }
            if (remaining.isEmpty()) return@forEach
            val next = if (current.isEmpty()) remaining else "$current $remaining"
            if (paint.measureText(next) <= width) {
                current = next
            } else {
                lines += current
                current = remaining
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines.ifEmpty { listOf(source) }
    }

    private fun longestFit(text: String, paint: Paint, width: Float): Int {
        var count = 1
        while (count < text.length && paint.measureText(text.substring(0, count + 1)) <= width) {
            count++
        }
        return count
    }
}
