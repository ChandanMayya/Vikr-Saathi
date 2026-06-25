package com.loctell.vikrsaathi.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.loctell.vikrsaathi.data.model.BillLineItem
import com.loctell.vikrsaathi.data.model.BillWithDetails
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun generateBillPdf(
        context: Context,
        bill: BillWithDetails,
        shopName: String,
        currencySymbol: String,
        headerImage: Bitmap?,
        signatureImage: Bitmap?
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        var y = MARGIN
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        headerImage?.let { bitmap ->
            val scaled = scaleBitmap(bitmap, PAGE_WIDTH - (MARGIN * 2).toInt(), 120)
            canvas.drawBitmap(scaled, MARGIN, y, null)
            y += scaled.height + 20f
        }

        paint.textSize = 18f
        bold.textSize = 20f
        canvas.drawText(shopName, MARGIN, y, bold)
        y += 28f

        paint.textSize = 12f
        canvas.drawText("Bill No: ${bill.bill.billNumber}", MARGIN, y, paint)
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        canvas.drawText(
            "Date: ${dateFormat.format(Date(bill.bill.date))}",
            PAGE_WIDTH - MARGIN - 180f,
            y,
            paint
        )
        y += 24f

        bold.textSize = 14f
        canvas.drawText("Buyer Details", MARGIN, y, bold)
        y += 20f
        paint.textSize = 12f
        val customerName = bill.customer?.name ?: "-"
        canvas.drawText("Name: $customerName", MARGIN, y, paint)
        y += 16f
        canvas.drawText("Address: ${bill.customer?.formattedAddress() ?: "-"}", MARGIN, y, paint)
        y += 16f
        canvas.drawText("Phone: ${bill.customer?.phone ?: "-"}", MARGIN, y, paint)
        y += 28f

        y = drawTable(canvas, y, bill.items.map {
            BillLineItem(
                itemId = it.itemId,
                name = it.itemName,
                mrp = it.mrp,
                discount = it.discount,
                quantity = it.quantity
            )
        }, currencySymbol)

        y += 20f
        bold.textSize = 14f
        canvas.drawText(
            "Total: ${PriceCalculator.formatAmount(bill.bill.total, currencySymbol)}",
            MARGIN,
            y,
            bold
        )
        y += 20f
        paint.textSize = 11f
        val words = NumberToWords.convert(bill.bill.total)
        y = drawWrappedText(canvas, "Amount in Words: $words", MARGIN, y, PAGE_WIDTH - MARGIN * 2, paint)
        y += 30f

        signatureImage?.let { bitmap ->
            val scaled = scaleBitmap(bitmap, 150, 60)
            canvas.drawBitmap(scaled, MARGIN, y, null)
            y += scaled.height + 8f
        }
        paint.textSize = 12f
        canvas.drawText("Authorised Signature", MARGIN, y, paint)

        document.finishPage(page)

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val file = File(dir, "Bill_${bill.bill.billNumber}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun drawTable(
        canvas: Canvas,
        startY: Float,
        items: List<BillLineItem>,
        currencySymbol: String
    ): Float {
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val linePaint = Paint().apply { color = Color.BLACK; strokeWidth = 1f }

        val colSl = MARGIN
        val colName = MARGIN + 30f
        val colMrp = PAGE_WIDTH - MARGIN - 200f
        val colDisc = PAGE_WIDTH - MARGIN - 140f
        val colPrice = PAGE_WIDTH - MARGIN - 70f

        var y = startY
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 16f
        canvas.drawText("Sl", colSl, y, headerPaint)
        canvas.drawText("Particulars", colName, y, headerPaint)
        canvas.drawText("MRP", colMrp, y, headerPaint)
        canvas.drawText("Disc%", colDisc, y, headerPaint)
        canvas.drawText("Price", colPrice, y, headerPaint)
        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 16f

        items.forEachIndexed { index, item ->
            canvas.drawText("${index + 1}", colSl, y, cellPaint)
            val name = if (item.quantity > 1) "${item.name} (x${item.quantity})" else item.name
            canvas.drawText(truncate(name, 28), colName, y, cellPaint)
            canvas.drawText(
                PriceCalculator.formatAmount(item.mrp, currencySymbol),
                colMrp,
                y,
                cellPaint
            )
            canvas.drawText(String.format("%.1f", item.discount), colDisc, y, cellPaint)
            canvas.drawText(
                PriceCalculator.formatAmount(item.lineTotal, currencySymbol),
                colPrice,
                y,
                cellPaint
            )
            y += 18f
        }

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        return y
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint
    ): Float {
        val words = text.split(" ")
        var line = StringBuilder()
        var y = startY
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxWidth) {
                canvas.drawText(line.toString(), x, y, paint)
                y += 14f
                line = StringBuilder(word)
            } else {
                line = StringBuilder(test)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, y, paint)
            y += 14f
        }
        return y
    }

    private fun scaleBitmap(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = minOf(
            maxWidth.toFloat() / source.width,
            maxHeight.toFloat() / source.height
        )
        val width = (source.width * ratio).toInt()
        val height = (source.height * ratio).toInt()
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun truncate(text: String, max: Int): String {
        return if (text.length <= max) text else text.take(max - 3) + "..."
    }
}
