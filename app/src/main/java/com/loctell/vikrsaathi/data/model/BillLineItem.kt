package com.loctell.vikrsaathi.data.model

import com.loctell.vikrsaathi.util.PriceCalculator

/**
 * In-memory line item used while creating or editing a bill.
 */
data class BillLineItem(
    val itemId: Long? = null,
    val name: String,
    val mrp: Double,
    val discount: Double,
    var quantity: Int = 1
) {
    val unitPriceAfterDiscount: Double
        get() = PriceCalculator.priceAfterDiscount(mrp, discount)

    val lineTotal: Double
        get() = unitPriceAfterDiscount * quantity
}
