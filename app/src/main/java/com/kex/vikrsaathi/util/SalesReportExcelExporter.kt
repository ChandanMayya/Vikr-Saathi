package com.kex.vikrsaathi.util

import android.content.Context
import android.os.Environment
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.domain.bill.warrantySnapshot
import com.kex.vikrsaathi.domain.inventory.CivilDate
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SalesReportExcelExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun export(context: Context, bills: List<BillWithDetails>): File {
        val rows = buildRows(bills)
        val cellRows = rows.map { row ->
            listOf(
                XlsxSpreadsheet.CellValue.Text(row.billNumber),
                XlsxSpreadsheet.CellValue.IntNum(row.invoiceCounter),
                XlsxSpreadsheet.CellValue.Text(dateFormat.format(Date(row.dateMillis))),
                XlsxSpreadsheet.CellValue.Text(row.customerName),
                XlsxSpreadsheet.CellValue.Text(row.customerPhone),
                XlsxSpreadsheet.CellValue.Text(row.customerAddress),
                XlsxSpreadsheet.CellValue.Text(row.itemName),
                XlsxSpreadsheet.CellValue.IntNum(row.quantity),
                XlsxSpreadsheet.CellValue.Number(row.mrp),
                XlsxSpreadsheet.CellValue.Number(row.discount),
                XlsxSpreadsheet.CellValue.Number(row.lineTotal),
                XlsxSpreadsheet.CellValue.Number(row.billTotal),
                XlsxSpreadsheet.CellValue.Text(row.warrantyPeriod?.toString().orEmpty()),
                XlsxSpreadsheet.CellValue.Text(row.warrantyUnit),
                XlsxSpreadsheet.CellValue.Text(row.warrantyProvider),
                XlsxSpreadsheet.CellValue.Text(row.warrantyProviderName),
                XlsxSpreadsheet.CellValue.Text(row.warrantyTerms),
                XlsxSpreadsheet.CellValue.Text(row.warrantyStart),
                XlsxSpreadsheet.CellValue.Text(row.warrantyEnd)
            )
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, "Sales_Report_${System.currentTimeMillis()}.xlsx")
        XlsxSpreadsheet.write(file, "Sales", SalesReportColumns.HEADERS, cellRows)
        return file
    }

    private fun buildRows(bills: List<BillWithDetails>): List<SalesReportRow> {
        val result = mutableListOf<SalesReportRow>()
        bills.sortedByDescending { it.bill.date }.forEach { bill ->
            val customerName = bill.customer?.name.orEmpty()
            val customerPhone = bill.customer?.phone.orEmpty()
            val customerAddress = bill.customer?.formattedAddress().orEmpty()
            if (bill.items.isEmpty()) {
                result.add(
                    SalesReportRow(
                        billNumber = bill.bill.billNumber,
                        invoiceCounter = bill.bill.invoiceCounter,
                        dateMillis = bill.bill.date,
                        customerName = customerName,
                        customerPhone = customerPhone,
                        customerAddress = customerAddress,
                        itemName = "",
                        quantity = 0,
                        mrp = 0.0,
                        discount = 0.0,
                        lineTotal = 0.0,
                        billTotal = bill.bill.total
                    )
                )
            } else {
                bill.items.forEach { item ->
                    val warranty = item.warrantySnapshot()
                    result.add(
                        SalesReportRow(
                            billNumber = bill.bill.billNumber,
                            invoiceCounter = bill.bill.invoiceCounter,
                            dateMillis = bill.bill.date,
                            customerName = customerName,
                            customerPhone = customerPhone,
                            customerAddress = customerAddress,
                            itemName = item.itemName,
                            quantity = item.quantity,
                            mrp = item.mrp,
                            discount = item.discount,
                            lineTotal = item.finalPrice * item.quantity,
                            billTotal = bill.bill.total,
                            warrantyPeriod = warranty?.period,
                            warrantyUnit = warranty?.unit.orEmpty(),
                            warrantyProvider = warranty?.provider.orEmpty(),
                            warrantyProviderName = warranty?.providerName.orEmpty(),
                            warrantyTerms = warranty?.terms.orEmpty(),
                            warrantyStart = warranty?.startEpochDay?.let { CivilDate.iso(it) }.orEmpty(),
                            warrantyEnd = warranty?.endEpochDay?.let { CivilDate.iso(it) }.orEmpty()
                        )
                    )
                }
            }
        }
        return result
    }
}
