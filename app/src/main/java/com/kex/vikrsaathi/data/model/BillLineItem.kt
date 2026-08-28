package com.kex.vikrsaathi.data.model

import com.kex.vikrsaathi.util.PriceCalculator

/**
 * In-memory line item used while creating or editing a bill.
 */
data class BillLineItem(
    val itemId: Long? = null,
    val name: String,
    val mrp: Double,
    val discount: Double,
    var quantity: Int = 1,
    val roundOff: Double = 0.0
) {
    val unitPriceAfterDiscount: Double
        get() = PriceCalculator.priceAfterDiscount(mrp, discount)

    val rawLineTotal: Double
        get() = unitPriceAfterDiscount * quantity

    val lineTotal: Double
        get() = rawLineTotal + roundOff
}
