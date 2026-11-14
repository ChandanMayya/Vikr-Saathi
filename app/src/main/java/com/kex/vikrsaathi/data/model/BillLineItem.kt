package com.kex.vikrsaathi.data.model

import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.domain.inventory.SavedLotUse
import com.kex.vikrsaathi.domain.money.Money
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
    val lineKey: String = "",
    val unitCostPaise: Long? = null,
    /** Terms captured for this line. Dates are filled when the bill is saved. */
    val warranty: WarrantyPolicy.Snapshot? = null,
    /**
     * Lot chosen for this line. Null keeps earliest-expiry allocation.
     * Used only when batch tracking is on for the shop and the item.
     */
    val batchId: Long? = null,
    /** Lots already posted on a saved bill. Empty while the line is still a draft. */
    val savedLots: List<SavedLotUse> = emptyList()
) {
    val unitPriceAfterDiscount: Double
        get() = PriceCalculator.priceAfterDiscount(mrp, discount)

    val rawLineTotal: Double
        get() = Money.paiseToRupees(Money.rupeesToPaise(unitPriceAfterDiscount) * quantity)

    val lineTotal: Double
        get() = rawLineTotal + roundOff
}
