package com.kex.vikrsaathi.domain.finance

/**
 * Purchase quantities are entered in a buying unit. Inventory and request
 * quantities stay in the item's base unit. Factor 1 with a blank unit is the
 * legacy meaning: the stored quantity is already the base quantity.
 */
object PackMath {

    data class BundleCoverage(
        val packagesNeeded: Int,
        val excessBase: Int,
        val availableBase: Long,
        val canCover: Boolean
    )

    fun validateTransaction(unitName: String, unitsPerBundle: Int): FinanceError? {
        if (unitsPerBundle <= 0) return FinanceError.INVALID_PACK
        if (unitsPerBundle > 1 && unitName.isBlank()) return FinanceError.INVALID_PACK
        return null
    }

    fun validateItemPack(purchaseUnit: String, unitsPerPurchase: Int): FinanceError? {
        if (purchaseUnit.isBlank()) return null
        if (unitsPerPurchase <= 0) return FinanceError.INVALID_PACK
        return null
    }

    fun toBaseLong(enteredQty: Int, unitsPerBundle: Int): Long? {
        if (enteredQty < 0 || unitsPerBundle <= 0) return null
        return enteredQty.toLong() * unitsPerBundle.toLong()
    }

    fun toBase(enteredQty: Int, unitsPerBundle: Int): Int? {
        val wide = toBaseLong(enteredQty, unitsPerBundle) ?: return null
        if (wide > Int.MAX_VALUE) return null
        return wide.toInt()
    }

    fun multiply(amount: Long, count: Int): Long? {
        if (count < 0) return null
        return try {
            Math.multiplyExact(amount, count.toLong())
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun packagesNeeded(requestedBase: Int, unitsPerBundle: Int): Int? {
        if (requestedBase < 0 || unitsPerBundle <= 0) return null
        if (requestedBase == 0) return 0
        val whole = requestedBase / unitsPerBundle
        return if (requestedBase % unitsPerBundle == 0) whole else whole + 1
    }

    fun coverage(requestedBase: Int, availablePackages: Int, unitsPerBundle: Int): BundleCoverage? {
        if (availablePackages < 0) return null
        val needed = packagesNeeded(requestedBase, unitsPerBundle) ?: return null
        val covered = toBaseLong(needed, unitsPerBundle) ?: return null
        val excess = covered - requestedBase.toLong()
        if (excess > Int.MAX_VALUE) return null
        val availableBase = toBaseLong(availablePackages, unitsPerBundle) ?: return null
        return BundleCoverage(
            packagesNeeded = needed,
            excessBase = excess.toInt(),
            availableBase = availableBase,
            canCover = availableBase >= requestedBase.toLong()
        )
    }

    /** Negative when [rateA] per [factorA] is cheaper. Null when the cross-multiply overflows. */
    fun comparePerBase(rateA: Long, factorA: Int, rateB: Long, factorB: Int): Int? {
        if (rateA < 0 || rateB < 0 || factorA <= 0 || factorB <= 0) return null
        val left = multiply(rateA, factorB) ?: return null
        val right = multiply(rateB, factorA) ?: return null
        return left.compareTo(right)
    }

    fun perBasePaise(ratePaise: Long, unitsPerBundle: Int): Long? {
        if (ratePaise < 0 || unitsPerBundle <= 0) return null
        return ratePaise / unitsPerBundle
    }

    /** Entered quantity times the rate for that entered unit. */
    fun lineTotalPaise(enteredQty: Int, ratePaise: Long): Long? {
        if (enteredQty < 0 || ratePaise < 0) return null
        return multiply(ratePaise, enteredQty)
    }

    fun addBase(current: Int, enteredQty: Int, unitsPerBundle: Int): Int {
        val base = toBase(enteredQty, unitsPerBundle) ?: return Int.MAX_VALUE
        val sum = current.toLong() + base.toLong()
        return if (sum > Int.MAX_VALUE) Int.MAX_VALUE else sum.toInt()
    }

    fun formatQuantity(enteredQty: Int, unitName: String, unitsPerBundle: Int, baseUnit: String = ""): String {
        val unit = unitName.trim()
        if (unitsPerBundle <= 1) {
            return if (unit.isBlank()) enteredQty.toString() else "$enteredQty $unit"
        }
        val base = toBase(enteredQty, unitsPerBundle)
        val baseLabel = baseUnit.trim().ifBlank { unit }
        val entered = if (unit.isBlank()) enteredQty.toString() else "$enteredQty $unit"
        val converted = if (base == null) "?" else if (baseLabel.isBlank()) base.toString() else "$base $baseLabel"
        val per = if (baseLabel.isBlank()) unitsPerBundle.toString() else "$unitsPerBundle $baseLabel"
        return "$entered × $per = $converted"
    }
}
