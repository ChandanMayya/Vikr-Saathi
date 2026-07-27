package com.kex.vikrsaathi.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.kex.vikrsaathi.data.model.analytics.AnalyticsDashboard
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AnalyticsReportPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 16f

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun generate(
        context: Context,
        dashboard: AnalyticsDashboard,
        shopName: String,
        rangeLabel: String,
        currencySymbol: String
    ): File {
        val document = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = Color.BLACK
        }

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) newPage()
        }

        fun drawLine(text: String, paint: Paint = bodyPaint) {
            ensureSpace(LINE_HEIGHT)
            canvas.drawText(text, MARGIN, y, paint)
            y += LINE_HEIGHT
        }

        fun drawSection(title: String) {
            ensureSpace(LINE_HEIGHT * 2)
            y += 6f
            canvas.drawText(title, MARGIN, y, sectionPaint)
            y += LINE_HEIGHT + 4f
        }

        canvas.drawText(shopName.ifBlank { "Analysis" }, MARGIN, y, titlePaint)
        y += LINE_HEIGHT + 4f
        drawLine("Analysis report — $rangeLabel")
        drawLine("Generated ${dateFormat.format(Date())}")
        y += 8f

        drawSection("Summary")
        drawLine("Total sales: ${PriceCalculator.formatAmount(dashboard.summary.totalSales, currencySymbol)}")
        drawLine("Bill count: ${dashboard.summary.billCount}")
        drawLine("Average bill: ${PriceCalculator.formatAmount(dashboard.summary.avgBillValue, currencySymbol)}")

        drawSection("Discount impact")
        drawLine("Gross at MRP: ${PriceCalculator.formatAmount(dashboard.discountImpact.grossAtMrp, currencySymbol)}")
        drawLine("Net revenue: ${PriceCalculator.formatAmount(dashboard.discountImpact.netRevenue, currencySymbol)}")
        drawLine("Discount given: ${PriceCalculator.formatAmount(dashboard.discountImpact.discountGiven, currencySymbol)}")
        drawLine("Average discount: ${String.format(Locale.getDefault(), "%.1f%%", dashboard.discountImpact.avgDiscountPercent)}")

        drawSection("Inventory")
        drawLine("Total stock value: ${PriceCalculator.formatAmount(dashboard.inventoryValue.totalValue, currencySymbol)}")
        drawLine("In-stock items: ${dashboard.inventoryValue.itemCount}")

        drawSection("Top products")
        if (dashboard.topProducts.isEmpty()) {
            drawLine("No products sold in this period.")
        } else {
            dashboard.topProducts.forEach { row ->
                drawLine("${row.itemName} — qty ${row.totalQuantity}, ${PriceCalculator.formatAmount(row.totalRevenue, currencySymbol)}")
            }
        }

        drawSection("Top customers")
        if (dashboard.topCustomers.isEmpty()) {
            drawLine("No customer sales in this period.")
        } else {
            dashboard.topCustomers.forEach { row ->
                drawLine("${row.customerName} — ${row.billCount} bills, ${PriceCalculator.formatAmount(row.totalSpend, currencySymbol)}")
            }
        }

        drawSection("Slow movers")
        if (dashboard.slowMovers.isEmpty()) {
            drawLine("No in-stock items found.")
        } else {
            dashboard.slowMovers.forEach { row ->
                drawLine("${row.itemName} — stock ${row.stockQty}, sold ${row.soldQty}")
            }
        }

        drawSection("Top stock by value")
        if (dashboard.inventoryValue.topByValue.isEmpty()) {
            drawLine("No in-stock items found.")
        } else {
            dashboard.inventoryValue.topByValue.forEach { row ->
                drawLine("${row.itemName} — ${row.stockQty} × ${PriceCalculator.formatAmount(row.unitPrice, currencySymbol)} = ${PriceCalculator.formatAmount(row.stockValue, currencySymbol)}")
            }
        }

        drawSection("Sales trend")
        if (dashboard.salesTrend.all { it.revenue == 0.0 && it.billCount == 0 }) {
            drawLine("No sales in this period.")
        } else {
            dashboard.salesTrend.forEach { point ->
                drawLine("${dateFormat.format(Date(point.dayStartMillis))}: ${PriceCalculator.formatAmount(point.revenue, currencySymbol)} (${point.billCount} bills)")
            }
        }

        drawSection("Peak hours")
        val peakWithSales = dashboard.peakHours.filter { it.billCount > 0 }
        if (peakWithSales.isEmpty()) {
            drawLine("No bills in this period.")
        } else {
            peakWithSales.forEach { row ->
                drawLine("${formatHourLabel(row.hour)}: ${row.billCount} bills, ${PriceCalculator.formatAmount(row.revenue, currencySymbol)}")
            }
        }

        drawSection("Sales by day of week")
        val daysWithSales = dashboard.salesByDayOfWeek.filter { it.billCount > 0 }
        if (daysWithSales.isEmpty()) {
            drawLine("No bills in this period.")
        } else {
            daysWithSales.forEach { row ->
                drawLine("${formatDayLabel(row.dayOfWeek)}: ${row.billCount} bills, ${PriceCalculator.formatAmount(row.revenue, currencySymbol)}")
            }
        }

        drawSection("Customer mix")
        drawLine("Walk-in: ${dashboard.customerMix.walkInBills} bills, ${PriceCalculator.formatAmount(dashboard.customerMix.walkInSales, currencySymbol)}")
        drawLine("Registered: ${dashboard.customerMix.registeredBills} bills, ${PriceCalculator.formatAmount(dashboard.customerMix.registeredSales, currencySymbol)}")

        drawSection("Held bills")
        drawLine("Finalized in period: ${dashboard.heldBills.finalizedInPeriod}")
        drawLine("Held in period: ${dashboard.heldBills.heldInPeriod}")
        drawLine("Currently held: ${dashboard.heldBills.activeHeldNow}")
        drawLine("Completion rate: ${String.format(Locale.getDefault(), "%.0f%%", dashboard.heldBills.completionRatePercent)}")

        document.finishPage(page)
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, "Analysis_Report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun formatHourLabel(hour: Int): String {
        return when {
            hour == 0 -> "12 AM"
            hour < 12 -> "$hour AM"
            hour == 12 -> "12 PM"
            else -> "${hour - 12} PM"
        }
    }

    private fun formatDayLabel(dayOfWeek: Int): String {
        return java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, dayOfWeek)
        }.getDisplayName(java.util.Calendar.SHORT, java.util.Calendar.SHORT, Locale.getDefault()) ?: "?"
    }
}
