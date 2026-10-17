package com.kex.vikrsaathi.ui.finance

import com.kex.vikrsaathi.domain.finance.PackMath

/**
 * Labels for quote comparison. Money figures come from [PackMath] and the
 * request comparison; this only formats those results.
 */
object QuoteCompareFormat {

    fun perUnit(amount: String, unit: String): String = "$amount / $unit"

    fun packageSize(unitsPerBundle: Int, baseUnit: String, packageUnit: String): String =
        "$unitsPerBundle $baseUnit / $packageUnit"

    fun extra(extraBase: Int, baseUnit: String): String =
        if (extraBase <= 0) "0 extra" else "$extraBase $baseUnit extra"

    fun packages(needed: Int, packageUnit: String, extraLabel: String): String =
        "$needed $packageUnit · $extraLabel"

    fun charges(goods: String, delivery: String, discount: String, total: String): String =
        "Goods $goods · Delivery $delivery · Discount −$discount · Total $total"

    fun openCharges(delivery: String, discount: String): String =
        "Delivery $delivery · Discount −$discount"

    /** Package price times packages needed. Null when the offer cannot cover the request. */
    fun materialGoodsPaise(ratePaise: Long, packagesNeeded: Int, canCover: Boolean): Long? {
        if (!canCover) return null
        return PackMath.multiply(ratePaise, packagesNeeded)
    }

    /** Goods plus delivery, minus the quote discount. Null when the sum overflows. */
    fun landedPaise(goodsPaise: Long, deliveryPaise: Long, discountPaise: Long): Long? {
        return try {
            Math.subtractExact(Math.addExact(goodsPaise, deliveryPaise), discountPaise)
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Indexes of the lowest comparable landed totals.
     * A single comparable quote is not marked, and a missing total is ignored.
     */
    fun lowestIndexes(landedPaise: List<Long?>): Set<Int> {
        val comparable = landedPaise.mapIndexedNotNull { index, value -> value?.let { index to it } }
        if (comparable.size < 2) return emptySet()
        val lowest = comparable.minOf { it.second }
        return comparable.filter { it.second == lowest }.map { it.first }.toSet()
    }
}
