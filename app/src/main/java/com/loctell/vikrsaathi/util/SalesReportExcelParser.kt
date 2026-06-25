package com.loctell.vikrsaathi.util

import android.content.Context
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Locale

object SalesReportExcelParser {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    )

    fun parse(context: Context, uri: Uri): List<SalesReportRow> {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }.orEmpty().lowercase(Locale.getDefault())

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()

        return when {
            name.endsWith(".xlsx") -> tableRowsToSalesRows(XlsxSpreadsheet.read(bytes.inputStream()))
            name.endsWith(".csv") -> parseCsv(bytes.toString(Charsets.UTF_8))
            name.endsWith(".xls") || name.endsWith(".xml") ->
                tableRowsToSalesRows(parseLegacySpreadsheetXml(bytes.toString(Charsets.UTF_8)))
            else -> {
                try {
                    tableRowsToSalesRows(XlsxSpreadsheet.read(bytes.inputStream()))
                } catch (_: Exception) {
                    parseCsv(bytes.toString(Charsets.UTF_8))
                }
            }
        }
    }

    private fun tableRowsToSalesRows(table: List<List<String>>): List<SalesReportRow> {
        if (table.isEmpty()) return emptyList()
        val columnMap = buildColumnMap(table.first())
        if (!columnMap.containsKey(SalesReportColumns.BILL_NUMBER)) return emptyList()
        return table.drop(1).mapNotNull { cells ->
            rowFromCells(cells, columnMap)
        }
    }

    private fun parseCsv(text: String): List<SalesReportRow> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val header = parseCsvLine(lines.first())
        val columnMap = buildColumnMap(header)
        if (!columnMap.containsKey(SalesReportColumns.BILL_NUMBER)) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            val cells = parseCsvLine(line)
            rowFromCells(cells, columnMap)
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun parseLegacySpreadsheetXml(text: String): List<List<String>> {
        val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(text.reader())

        var event = parser.eventType
        var inRow = false
        var inCell = false
        var inData = false
        var currentRow = mutableListOf<String>()
        val rows = mutableListOf<List<String>>()

        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "Row" -> {
                        inRow = true
                        currentRow = mutableListOf()
                    }
                    "Cell" -> if (inRow) inCell = true
                    "Data" -> if (inCell) inData = true
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> if (inData) {
                    currentRow.add(parser.text.trim())
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                    "Data" -> inData = false
                    "Cell" -> inCell = false
                    "Row" -> {
                        if (currentRow.isNotEmpty()) rows.add(currentRow)
                        inRow = false
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun buildColumnMap(headers: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        headers.forEachIndexed { index, header ->
            map[header.trim()] = index
        }
        return map
    }

    private fun rowFromCells(cells: List<String>, columnMap: Map<String, Int>): SalesReportRow? {
        fun cell(name: String): String =
            columnMap[name]?.let { cells.getOrNull(it)?.trim().orEmpty() }.orEmpty()

        val billNumber = cell(SalesReportColumns.BILL_NUMBER)
        if (billNumber.isBlank()) return null

        val itemName = cell(SalesReportColumns.ITEM_NAME)
        if (itemName.isBlank()) return null

        val dateText = cell(SalesReportColumns.DATE)
        val dateMillis = parseDate(dateText) ?: System.currentTimeMillis()

        return SalesReportRow(
            billNumber = billNumber,
            invoiceCounter = cell(SalesReportColumns.INVOICE_COUNTER).toIntOrNull() ?: 0,
            dateMillis = dateMillis,
            customerName = cell(SalesReportColumns.CUSTOMER_NAME),
            customerPhone = cell(SalesReportColumns.CUSTOMER_PHONE),
            customerAddress = cell(SalesReportColumns.CUSTOMER_ADDRESS),
            itemName = itemName,
            quantity = cell(SalesReportColumns.QUANTITY).toIntOrNull()?.coerceAtLeast(1) ?: 1,
            mrp = cell(SalesReportColumns.MRP).toDoubleOrNull() ?: 0.0,
            discount = cell(SalesReportColumns.DISCOUNT_PERCENT).toDoubleOrNull() ?: 0.0,
            lineTotal = cell(SalesReportColumns.LINE_TOTAL).toDoubleOrNull() ?: 0.0,
            billTotal = cell(SalesReportColumns.BILL_TOTAL).toDoubleOrNull() ?: 0.0
        )
    }

    private fun parseDate(text: String): Long? {
        if (text.isBlank()) return null
        for (format in dateFormats) {
            try {
                return format.parse(text)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
