package com.kex.vikrsaathi.util

object ItemCatalogColumns {
    const val ITEM_NAME = "Item Name"
    const val BARCODE = "Barcode"
    const val MRP = "MRP"
    const val DISCOUNT_PERCENT = "Discount %"
    const val SELLING_PRICE = "Selling Price"
    const val UNIT = "Unit"
    const val STOCK_QTY = "Stock Qty"

    val HEADERS = listOf(
        ITEM_NAME,
        BARCODE,
        MRP,
        DISCOUNT_PERCENT,
        SELLING_PRICE,
        UNIT,
        STOCK_QTY
    )

    private val ALIASES = mapOf(
        "name" to ITEM_NAME,
        "item" to ITEM_NAME,
        "product name" to ITEM_NAME,
        "bar code" to BARCODE,
        "sku" to BARCODE,
        "price" to MRP,
        "discount" to DISCOUNT_PERCENT,
        "discount percent" to DISCOUNT_PERCENT,
        "discount %" to DISCOUNT_PERCENT,
        "selling price" to SELLING_PRICE,
        "sale price" to SELLING_PRICE,
        "remarks" to UNIT,
        "unit" to UNIT,
        "stock" to STOCK_QTY,
        "stock qty" to STOCK_QTY,
        "quantity" to STOCK_QTY,
        "qty" to STOCK_QTY
    )

    fun normalizeHeader(header: String): String {
        val trimmed = header.trim()
        return ALIASES[trimmed.lowercase()] ?: trimmed
    }
}

data class ItemCatalogRow(
    val rowNumber: Int,
    val name: String,
    val barcode: String?,
    val mrp: Double,
    val discount: Double,
    val sellingPrice: Double?,
    val remarks: String,
    val stockQty: Int
)

data class ItemCatalogImportResult(
    val added: Int,
    val updated: Int,
    val errors: List<String>
)
