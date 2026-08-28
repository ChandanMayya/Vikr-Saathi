package com.kex.vikrsaathi.util

import android.content.Context
import android.net.Uri
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.repository.InventoryRepository
import com.kex.vikrsaathi.data.repository.ItemRepository

class ItemCatalogImporter(
    private val itemRepository: ItemRepository,
    private val inventoryRepository: InventoryRepository
) {

    suspend fun import(context: Context, uri: Uri): ItemCatalogImportResult {
        val rows = ItemCatalogExcelParser.parse(context, uri)
        if (rows.isEmpty()) {
            return ItemCatalogImportResult(
                added = 0,
                updated = 0,
                errors = listOf("No valid rows found. Check column headers and file format.")
            )
        }

        var added = 0
        var updated = 0
        val errors = mutableListOf<String>()

        for (row in rows) {
            when (val outcome = importRow(row)) {
                RowOutcome.Added -> added++
                RowOutcome.Updated -> updated++
                is RowOutcome.Error -> errors.add(outcome.message)
            }
        }

        return ItemCatalogImportResult(added, updated, errors)
    }

    private suspend fun importRow(row: ItemCatalogRow): RowOutcome {
        if (row.mrp <= 0.0) {
            return RowOutcome.Error("Row ${row.rowNumber}: ${row.name} — MRP must be greater than 0")
        }

        val barcode = row.barcode?.trim()?.ifEmpty { null }
        val existing = resolveExisting(row)

        return if (existing != null) {
            if (barcode != null && !itemRepository.isBarcodeUnique(barcode, existing.id)) {
                RowOutcome.Error("Row ${row.rowNumber}: ${row.name} — barcode already used by another item")
            } else {
                val updatedItem = existing.copy(
                    name = row.name.trim(),
                    barcode = barcode ?: existing.barcode,
                    mrp = row.mrp,
                    discount = row.discount,
                    sellingPrice = row.sellingPrice ?: existing.sellingPrice,
                    remarks = row.remarks.ifBlank { existing.remarks }
                )
                itemRepository.update(updatedItem.copy(stockQty = existing.stockQty))
                if (row.stockQty > 0) {
                    val stockResult = inventoryRepository.importStock(
                        itemId = existing.id,
                        delta = row.stockQty,
                        note = "Excel import"
                    )
                    if (stockResult.isFailure) {
                        return RowOutcome.Error(
                            "Row ${row.rowNumber}: ${row.name} — ${stockResult.exceptionOrNull()?.message ?: "stock update failed"}"
                        )
                    }
                }
                RowOutcome.Updated
            }
        } else {
            if (barcode != null && !itemRepository.isBarcodeUnique(barcode)) {
                RowOutcome.Error("Row ${row.rowNumber}: ${row.name} — barcode already exists")
            } else {
                val itemId = itemRepository.insert(
                    Item(
                        name = row.name.trim(),
                        barcode = barcode,
                        mrp = row.mrp,
                        discount = row.discount,
                        sellingPrice = row.sellingPrice,
                        remarks = row.remarks
                    )
                )
                if (row.stockQty > 0) {
                    val stockResult = inventoryRepository.importStock(
                        itemId = itemId,
                        delta = row.stockQty,
                        note = "Excel import"
                    )
                    if (stockResult.isFailure) {
                        return RowOutcome.Error(
                            "Row ${row.rowNumber}: ${row.name} — ${stockResult.exceptionOrNull()?.message ?: "stock update failed"}"
                        )
                    }
                }
                RowOutcome.Added
            }
        }
    }

    private suspend fun resolveExisting(row: ItemCatalogRow): Item? {
        val barcode = row.barcode?.trim()?.ifEmpty { null }
        if (barcode != null) {
            itemRepository.getByBarcode(barcode)?.let { return it }
        }
        return itemRepository.findByNameExact(row.name)
    }

    private sealed class RowOutcome {
        data object Added : RowOutcome()
        data object Updated : RowOutcome()
        data class Error(val message: String) : RowOutcome()
    }
}
