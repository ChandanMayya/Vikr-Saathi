package com.kex.vikrsaathi.util

import android.content.Context
import android.os.Environment
import com.kex.vikrsaathi.data.entity.Item
import java.io.File

object ItemCatalogExcelExporter {

    fun exportTemplate(context: Context): File {
        val sampleRow = listOf(
            XlsxSpreadsheet.CellValue.Text("Sample Item"),
            XlsxSpreadsheet.CellValue.Text("8901234567890"),
            XlsxSpreadsheet.CellValue.Number(100.0),
            XlsxSpreadsheet.CellValue.Number(10.0),
            XlsxSpreadsheet.CellValue.Number(90.0),
            XlsxSpreadsheet.CellValue.Text("pcs"),
            XlsxSpreadsheet.CellValue.IntNum(50)
        )
        return writeFile(
            context = context,
            fileName = "Inventory_Import_Template.xlsx",
            rows = listOf(sampleRow)
        )
    }

    fun exportItems(context: Context, items: List<Item>): File {
        val rows = items.sortedBy { it.name.lowercase() }.map { item ->
            listOf(
                XlsxSpreadsheet.CellValue.Text(item.name),
                XlsxSpreadsheet.CellValue.Text(item.barcode.orEmpty()),
                XlsxSpreadsheet.CellValue.Number(item.mrp),
                XlsxSpreadsheet.CellValue.Number(item.discount),
                XlsxSpreadsheet.CellValue.Number(item.sellingPrice ?: 0.0),
                XlsxSpreadsheet.CellValue.Text(item.remarks),
                XlsxSpreadsheet.CellValue.IntNum(item.stockQty)
            )
        }
        return writeFile(
            context = context,
            fileName = "Inventory_Export_${System.currentTimeMillis()}.xlsx",
            rows = rows
        )
    }

    private fun writeFile(
        context: Context,
        fileName: String,
        rows: List<List<XlsxSpreadsheet.CellValue>>
    ): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, fileName)
        XlsxSpreadsheet.write(file, "Inventory", ItemCatalogColumns.HEADERS, rows)
        return file
    }
}
