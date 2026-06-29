package com.kex.vikrsaathi.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.kex.vikrsaathi.data.model.BillWithDetails
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SalesReportPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val ROW_HEIGHT = 22f

    private val dateFormat: DateFormat =
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun generate(
        context: Context,
        bills: List<BillWithDetails>,
        shopName: String,
        currencySymbol: String,
        headerImage: Bitmap?,
        filterSummary: String
    ): File {
        val document = PdfDocument()
        val sorted = bills.sortedByDescending { it.bill.date }
        var pageNumber = 1
        var y = MARGIN
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
            drawTableHeader(canvas, y)
            y += ROW_HEIGHT + 4f
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                newPage()
            }
        }

        y = drawHeader(canvas, shopName, headerImage, filterSummary, sorted.size, y)
        y += 16f
        drawTableHeader(canvas, y)
        y += ROW_HEIGHT + 4f

        var index = 1
        var grandTotal = 0.0
        sorted.forEach { bill ->
            grandTotal += bill.bill.total
            ensureSpace(ROW_HEIGHT)
            y = drawBillRow(canvas, index++, bill, currencySymbol, y)
        }

        ensureSpace(ROW_HEIGHT * 2)
        y += 8f
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#E65100")
        }
        canvas.drawText(
            "Total bills: ${sorted.size}    Grand total: ${PriceCalculator.formatAmount(grandTotal, currencySymbol)}",
            MARGIN,
            y + 14f,
            totalPaint
        )

        document.finishPage(page)

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, "Sales_Report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun drawHeader(
        canvas: Canvas,
        shopName: String,
        headerImage: Bitmap?,
        filterSummary: String,
        billCount: Int,
        startY: Float
    ): Float {
        var y = startY
        val contentWidth = PAGE_WIDTH - MARGIN * 2

        headerImage?.let { bitmap ->
            val maxH = 90f
            val scale = minOf(contentWidth / bitmap.width, maxH / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            val left = MARGIN + (contentWidth - w) / 2f
            val dest = RectF(left, y, left + w, y + h)
            val targetW = (w * PdfRenderQuality.IMAGE_SCALE).toInt().coerceAtLeast(1)
            val targetH = (h * PdfRenderQuality.IMAGE_SCALE).toInt().coerceAtLeast(1)
            val highRes = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(highRes, null, dest, paint)
            if (highRes !== bitmap) highRes.recycle()
            y += h + 12f
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#E65100")
        }
        canvas.drawText(shopName, MARGIN, y + 18f, titlePaint)
        y += 28f

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        canvas.drawText("Sales Report", MARGIN, y + 16f, subtitlePaint)
        y += 24f

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = Color.DKGRAY
        }
        canvas.drawText("Generated: ${dateFormat.format(Date())}", MARGIN, y + 12f, metaPaint)
        y += 16f
        canvas.drawText("Filter: $filterSummary", MARGIN, y + 12f, metaPaint)
        y += 16f
        canvas.drawText("Records: $billCount", MARGIN, y + 12f, metaPaint)
        y += 20f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, Paint().apply {
            color = Color.parseColor("#FFCC80")
            strokeWidth = 1.5f
        })
        return y + 8f
    }

    private fun drawTableHeader(canvas: Canvas, y: Float) {
        canvas.drawRect(
            MARGIN,
            y,
            PAGE_WIDTH - MARGIN,
            y + ROW_HEIGHT,
            Paint().apply { color = Color.parseColor("#FFF3E0") }
        )

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#E65100")
        }

        val cols = listOf("#" to 24f, "Bill No" to 110f, "Date" to 72f, "Customer" to 150f, "Total" to 80f)
        var x = MARGIN + 4f
        cols.forEach { (label, width) ->
            canvas.drawText(label, x, y + 15f, textPaint)
            x += width
        }
    }

    private fun drawBillRow(
        canvas: Canvas,
        index: Int,
        bill: BillWithDetails,
        currencySymbol: String,
        y: Float
    ): Float {
        if (index % 2 == 0) {
            canvas.drawRect(
                MARGIN,
                y,
                PAGE_WIDTH - MARGIN,
                y + ROW_HEIGHT,
                Paint().apply { color = Color.parseColor("#FFF8F0") }
            )
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f
            color = Color.BLACK
        }

        val customer = bill.customer?.name ?: "-"
        val cols = listOf(
            index.toString(),
            bill.bill.billNumber,
            dateFormat.format(Date(bill.bill.date)),
            customer,
            PriceCalculator.formatAmount(bill.bill.total, currencySymbol)
        )
        val widths = listOf(24f, 110f, 72f, 150f, 80f)
        var x = MARGIN + 4f
        cols.forEachIndexed { i, value ->
            val clipped = if (i == 3 && value.length > 22) value.take(21) + "…" else value
            canvas.drawText(clipped, x, y + 15f, textPaint)
            x += widths[i]
        }
        return y + ROW_HEIGHT
    }
}
