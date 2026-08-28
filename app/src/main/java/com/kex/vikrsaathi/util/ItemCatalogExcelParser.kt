package com.kex.vikrsaathi.util

import android.content.Context
import android.net.Uri
import java.util.Locale

object ItemCatalogExcelParser {

    fun parse(context: Context, uri: Uri): List<ItemCatalogRow> {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }.orEmpty().lowercase(Locale.getDefault())

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()

        return when {
            name.endsWith(".xlsx") -> tableRowsToCatalogRows(XlsxSpreadsheet.read(bytes.inputStream()))
            name.endsWith(".csv") -> parseCsv(bytes.toString(Charsets.UTF_8))
            else -> {
                try {
                    tableRowsToCatalogRows(XlsxSpreadsheet.read(bytes.inputStream()))
                } catch (_: Exception) {
                    parseCsv(bytes.toString(Charsets.UTF_8))
                }
            }
        }
    }

    private fun tableRowsToCatalogRows(table: List<List<String>>): List<ItemCatalogRow> {
        if (table.isEmpty()) return emptyList()
        val columnMap = buildColumnMap(table.first())
        if (!columnMap.containsKey(ItemCatalogColumns.ITEM_NAME)) return emptyList()
        return table.drop(1).mapIndexedNotNull { index, cells ->
            rowFromCells(rowNumber = index + 2, cells = cells, columnMap = columnMap)
        }
    }

    private fun parseCsv(text: String): List<ItemCatalogRow> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val header = parseCsvLine(lines.first())
        val columnMap = buildColumnMap(header)
        if (!columnMap.containsKey(ItemCatalogColumns.ITEM_NAME)) return emptyList()

        return lines.drop(1).mapIndexedNotNull { index, line ->
            val cells = parseCsvLine(line)
            rowFromCells(rowNumber = index + 2, cells = cells, columnMap = columnMap)
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

    private fun buildColumnMap(headers: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        headers.forEachIndexed { index, header ->
            map[ItemCatalogColumns.normalizeHeader(header)] = index
        }
        return map
    }

    private fun rowFromCells(
        rowNumber: Int,
        cells: List<String>,
        columnMap: Map<String, Int>
    ): ItemCatalogRow? {
        fun cell(name: String): String =
            columnMap[name]?.let { cells.getOrNull(it)?.trim().orEmpty() }.orEmpty()

        val itemName = cell(ItemCatalogColumns.ITEM_NAME)
        if (itemName.isBlank()) return null

        val barcode = cell(ItemCatalogColumns.BARCODE).ifBlank { null }
        val sellingPriceText = cell(ItemCatalogColumns.SELLING_PRICE)

        return ItemCatalogRow(
            rowNumber = rowNumber,
            name = itemName,
            barcode = barcode,
            mrp = cell(ItemCatalogColumns.MRP).toDoubleOrNull() ?: 0.0,
            discount = cell(ItemCatalogColumns.DISCOUNT_PERCENT).toDoubleOrNull() ?: 0.0,
            sellingPrice = sellingPriceText.toDoubleOrNull(),
            remarks = cell(ItemCatalogColumns.UNIT),
            stockQty = cell(ItemCatalogColumns.STOCK_QTY).toIntOrNull()?.coerceAtLeast(0) ?: 0
        )
    }
}
