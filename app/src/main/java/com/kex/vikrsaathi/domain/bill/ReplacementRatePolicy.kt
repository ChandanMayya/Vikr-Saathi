package com.kex.vikrsaathi.domain.bill

import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.util.PriceCalculator

/**
 * Replacement prices stay on this return. Nothing here writes the item master or the original bill.
 */
object ReplacementRatePolicy {

    data class PriceQuote(
        val unitRate: Double,
        val discountPercent: Double
    ) {
        val net: Double get() = PriceCalculator.priceAfterDiscount(unitRate, discountPercent)
    }

    data class BilledPrice(
        val unitRate: Double,
        val discountPercent: Double,
        val billedUnit: Double
    )

    enum class PriceNote {
        NOT_ON_BILL,
        MATCHES,
        DIFFERS
    }

    data class PriceComparison(
        val note: PriceNote,
        val originals: List<BilledPrice>
    )

    data class Line(
        val key: Long,
        val itemId: Long,
        val name: String,
        val unitPrice: Double,
        val discountPercent: Double,
        val quantity: Int
    )

    data class Merged(
        val lines: List<Line>,
        val nextKey: Long
    )

    /**
     * Master proposal. An explicit selling price becomes the net once:
     * the unit rate stays the MRP and the discount is the gap to that selling price.
     */
    fun masterQuote(mrp: Double, discountPercent: Double, sellingPrice: Double?): PriceQuote {
        if (sellingPrice != null && sellingPrice > 0.0 && mrp > 0.0) {
            val gap = ((mrp - sellingPrice) / mrp) * 100.0
            return PriceQuote(normalize(mrp), gap.coerceIn(0.0, 100.0))
        }
        if (sellingPrice != null && sellingPrice > 0.0) {
            return PriceQuote(normalize(sellingPrice), 0.0)
        }
        return PriceQuote(normalize(mrp), discountPercent.coerceIn(0.0, 100.0))
    }

    fun catalogRate(mrp: Double, discountPercent: Double, sellingPrice: Double?): Double {
        return masterQuote(mrp, discountPercent, sellingPrice).net
    }

    fun sameRate(left: Double, right: Double): Boolean {
        return Money.rupeesToPaise(left) == Money.rupeesToPaise(right)
    }

    fun sameDiscount(left: Double, right: Double): Boolean {
        return Money.rupeesToPaise(left) == Money.rupeesToPaise(right)
    }

    fun compare(master: PriceQuote, billed: List<BilledPrice>): PriceComparison {
        val distinct = distinctBilled(billed)
        if (distinct.isEmpty()) return PriceComparison(PriceNote.NOT_ON_BILL, emptyList())
        if (distinct.size > 1) return PriceComparison(PriceNote.DIFFERS, distinct)
        val only = distinct.single()
        val note = if (sameRate(only.billedUnit, master.net)) PriceNote.MATCHES else PriceNote.DIFFERS
        return PriceComparison(note, distinct)
    }

    fun parseRate(text: String): Double? {
        val value = parseNumber(text) ?: return null
        if (value < 0.0) return null
        return normalize(value)
    }

    fun parseDiscount(text: String): Double? {
        val value = parseNumber(text.replace("%", "")) ?: return null
        if (value < 0.0 || value > 100.0) return null
        return value
    }

    fun lineTotal(unitRate: Double, discountPercent: Double, quantity: Int): Double {
        return PriceCalculator.priceAfterDiscount(unitRate, discountPercent) * quantity.coerceAtLeast(0)
    }

    /**
     * Quantities combine only when the item, approved rate, and approved discount all match.
     */
    fun merge(
        lines: List<Line>,
        itemId: Long,
        name: String,
        unitPrice: Double,
        discountPercent: Double,
        quantity: Int,
        nextKey: Long
    ): Merged {
        val added = quantity.coerceAtLeast(1)
        val rate = normalize(unitPrice)
        val discount = discountPercent.coerceIn(0.0, 100.0)
        val index = lines.indexOfFirst {
            it.itemId == itemId && sameRate(it.unitPrice, rate) && sameDiscount(it.discountPercent, discount)
        }
        if (index >= 0) {
            return Merged(
                lines = lines.mapIndexed { position, line ->
                    if (position == index) line.copy(name = name, quantity = line.quantity + added) else line
                },
                nextKey = nextKey
            )
        }
        return Merged(
            lines = lines + Line(nextKey, itemId, name, rate, discount, added),
            nextKey = nextKey + 1
        )
    }

    private fun distinctBilled(prices: List<BilledPrice>): List<BilledPrice> {
        val seen = LinkedHashSet<String>()
        val distinct = ArrayList<BilledPrice>()
        prices.forEach { price ->
            val key = "${Money.rupeesToPaise(price.unitRate)}:${Money.rupeesToPaise(price.discountPercent)}:${Money.rupeesToPaise(price.billedUnit)}"
            if (seen.add(key)) {
                distinct.add(
                    price.copy(
                        unitRate = normalize(price.unitRate),
                        billedUnit = normalize(price.billedUnit)
                    )
                )
            }
        }
        return distinct
    }

    private fun parseNumber(text: String): Double? {
        var cleaned = text.trim().replace("₹", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        cleaned = if (cleaned.contains('.')) cleaned.replace(",", "") else cleaned.replace(',', '.')
        val value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        return value
    }

    private fun normalize(value: Double): Double = Money.paiseToRupees(Money.rupeesToPaise(value))
}
