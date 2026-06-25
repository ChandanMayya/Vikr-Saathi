package com.loctell.vikrsaathi.util

import android.content.Context
import android.net.Uri
import com.loctell.vikrsaathi.data.entity.Customer
import com.loctell.vikrsaathi.data.entity.Item
import com.loctell.vikrsaathi.data.model.BillLineItem
import com.loctell.vikrsaathi.data.repository.BillRepository
import com.loctell.vikrsaathi.data.repository.CustomerRepository
import com.loctell.vikrsaathi.data.repository.ItemRepository

class SalesReportImporter(
    private val billRepository: BillRepository,
    private val customerRepository: CustomerRepository,
    private val itemRepository: ItemRepository
) {

    suspend fun import(context: Context, uri: Uri): SalesImportResult {
        val rows = SalesReportExcelParser.parse(context, uri)
        if (rows.isEmpty()) {
            return SalesImportResult(0, 0, listOf("No valid rows found. Check column headers and file format."))
        }

        val grouped = rows.groupBy { it.billNumber }
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        grouped.forEach { (billNumber, billRows) ->
            if (billRepository.billNumberExists(billNumber)) {
                skipped++
                errors.add("Skipped $billNumber (already exists)")
                return@forEach
            }

            val first = billRows.first()
            val customerId = resolveCustomer(first)
            val lineItems = billRows.mapNotNull { row ->
                val itemId = resolveItem(row)
                if (row.mrp <= 0.0) {
                    null
                } else {
                    BillLineItem(
                        itemId = itemId,
                        name = row.itemName,
                        mrp = row.mrp,
                        discount = row.discount,
                        quantity = row.quantity
                    )
                }
            }

            if (lineItems.isEmpty()) {
                skipped++
                errors.add("Skipped $billNumber (no valid line items)")
                return@forEach
            }

            val billId = billRepository.importBillFromBackup(
                billNumber = billNumber,
                invoiceCounter = first.invoiceCounter,
                date = first.dateMillis,
                customerId = customerId,
                lineItems = lineItems
            )

            if (billId == null) {
                skipped++
                errors.add("Failed to import $billNumber")
            } else {
                imported++
            }
        }

        return SalesImportResult(imported, skipped, errors)
    }

    private suspend fun resolveCustomer(row: SalesReportRow): Long? {
        val name = row.customerName.trim()
        if (name.isEmpty()) return null

        val existing = customerRepository.search(name)
            .find {
                it.name.equals(name, ignoreCase = true) &&
                    (row.customerPhone.isBlank() || it.phone == row.customerPhone)
            }
        if (existing != null) return existing.id

        return customerRepository.insert(
            Customer(
                name = name,
                address1 = row.customerAddress,
                phone = row.customerPhone
            )
        )
    }

    private suspend fun resolveItem(row: SalesReportRow): Long? {
        val name = row.itemName.trim()
        if (name.isEmpty()) return null

        val existing = itemRepository.searchByName(name)
            .find { it.name.equals(name, ignoreCase = true) }
        if (existing != null) return existing.id

        if (row.mrp <= 0.0) return null
        return itemRepository.insert(
            Item(
                name = name,
                mrp = row.mrp,
                discount = row.discount
            )
        )
    }
}
