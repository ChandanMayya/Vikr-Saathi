package com.kex.vikrsaathi.util

import android.content.Context
import android.os.Environment
import com.kex.vikrsaathi.data.model.analytics.AnalyticsDashboard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AnalyticsReportExcelExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun export(
        context: Context,
        dashboard: AnalyticsDashboard,
        rangeLabel: String,
        currencySymbol: String
    ): File {
        val rows = mutableListOf<List<XlsxSpreadsheet.CellValue>>()

        fun textRow(vararg cells: String) {
            rows += cells.map { XlsxSpreadsheet.CellValue.Text(it) }
        }

        textRow("Analysis report", rangeLabel)
        textRow("Generated", dateFormat.format(Date()))
        rows.add(emptyList())

        textRow("Summary")
        textRow("Total sales", PriceCalculator.formatAmount(dashboard.summary.totalSales, currencySymbol))
        textRow("Bill count", dashboard.summary.billCount.toString())
        textRow("Average bill", PriceCalculator.formatAmount(dashboard.summary.avgBillValue, currencySymbol))
        rows.add(emptyList())

        textRow("Discount impact")
        textRow("Gross at MRP", PriceCalculator.formatAmount(dashboard.discountImpact.grossAtMrp, currencySymbol))
        textRow("Net revenue", PriceCalculator.formatAmount(dashboard.discountImpact.netRevenue, currencySymbol))
        textRow("Discount given", PriceCalculator.formatAmount(dashboard.discountImpact.discountGiven, currencySymbol))
        textRow("Average discount %", String.format(Locale.getDefault(), "%.1f%%", dashboard.discountImpact.avgDiscountPercent))
        rows.add(emptyList())

        textRow("Inventory value")
        textRow("Total stock value", PriceCalculator.formatAmount(dashboard.inventoryValue.totalValue, currencySymbol))
        textRow("In-stock items", dashboard.inventoryValue.itemCount.toString())
        rows.add(emptyList())

        textRow("Sales trend", "Date", "Revenue", "Bills")
        dashboard.salesTrend.forEach { point ->
            textRow(
                dateFormat.format(Date(point.dayStartMillis)),
                PriceCalculator.formatAmount(point.revenue, currencySymbol),
                point.billCount.toString()
            )
        }
        rows.add(emptyList())

        textRow("Top products", "Item", "Qty", "Revenue")
        dashboard.topProducts.forEach { row ->
            textRow(row.itemName, row.totalQuantity.toString(), PriceCalculator.formatAmount(row.totalRevenue, currencySymbol))
        }
        rows.add(emptyList())

        textRow("Top customers", "Customer", "Bills", "Spend")
        dashboard.topCustomers.forEach { row ->
            textRow(row.customerName, row.billCount.toString(), PriceCalculator.formatAmount(row.totalSpend, currencySymbol))
        }
        rows.add(emptyList())

        textRow("Slow movers", "Item", "Stock", "Sold in period")
        dashboard.slowMovers.forEach { row ->
            textRow(row.itemName, row.stockQty.toString(), row.soldQty.toString())
        }
        rows.add(emptyList())

        textRow("Top stock by value", "Item", "Stock", "Unit price", "Stock value")
        dashboard.inventoryValue.topByValue.forEach { row ->
            textRow(
                row.itemName,
                row.stockQty.toString(),
                PriceCalculator.formatAmount(row.unitPrice, currencySymbol),
                PriceCalculator.formatAmount(row.stockValue, currencySymbol)
            )
        }
        rows.add(emptyList())

        textRow("Peak hours", "Hour", "Bills")
        dashboard.peakHours.filter { it.billCount > 0 }.forEach { row ->
            textRow(formatHourLabel(row.hour), row.billCount.toString())
        }
        rows.add(emptyList())

        textRow("Sales by day of week", "Day", "Bills", "Revenue")
        dashboard.salesByDayOfWeek.filter { it.billCount > 0 }.forEach { row ->
            textRow(
                formatDayLabel(row.dayOfWeek),
                row.billCount.toString(),
                PriceCalculator.formatAmount(row.revenue, currencySymbol)
            )
        }
        rows.add(emptyList())

        textRow("Customer mix")
        textRow(
            "Walk-in bills",
            dashboard.customerMix.walkInBills.toString(),
            PriceCalculator.formatAmount(dashboard.customerMix.walkInSales, currencySymbol)
        )
        textRow(
            "Registered bills",
            dashboard.customerMix.registeredBills.toString(),
            PriceCalculator.formatAmount(dashboard.customerMix.registeredSales, currencySymbol)
        )
        rows.add(emptyList())

        textRow("Held bills")
        textRow("Finalized in period", dashboard.heldBills.finalizedInPeriod.toString())
        textRow("Held in period", dashboard.heldBills.heldInPeriod.toString())
        textRow("Currently held", dashboard.heldBills.activeHeldNow.toString())
        textRow(
            "Completion rate",
            String.format(Locale.getDefault(), "%.0f%%", dashboard.heldBills.completionRatePercent)
        )

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, "Analysis_Report_${System.currentTimeMillis()}.xlsx")
        XlsxSpreadsheet.write(
            file = file,
            sheetName = "Analysis",
            headers = listOf("Section", "Value 1", "Value 2", "Value 3", "Value 4"),
            rows = rows
        )
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
        }.getDisplayName(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SHORT, Locale.getDefault()) ?: "?"
    }
}
