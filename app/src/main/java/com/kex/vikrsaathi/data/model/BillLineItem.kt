package com.kex.vikrsaathi.data.model

import com.kex.vikrsaathi.util.PriceCalculator

/**
 * In-memory line item used while creating or editing a bill.
 *
 * [isNew] lines are not in the item master yet; they are created (with [openingStock]
 * and [barcode]) when the bill is saved, and [mrp] holds the per-unit rate entered.
 */
data class BillLineItem(
    val itemId: Long? = null,
    val name: String,
    val mrp: Double,
    val discount: Double,
    var quantity: Int = 1,
    val roundOff: Double = 0.0,
    val isNew: Boolean = false,
    val openingStock: Int? = null,
    val barcode: String? = null,
    /** Stable row identity. Unsaved catalog items have no [itemId] yet. */
    val lineKey: String = ""
) {
    val unitPriceAfterDiscount: Double
        get() = PriceCalculator.priceAfterDiscount(mrp, discount)

    val rawLineTotal: Double
        get() = unitPriceAfterDiscount * quantity

    val lineTotal: Double
        get() = rawLineTotal + roundOff
}
