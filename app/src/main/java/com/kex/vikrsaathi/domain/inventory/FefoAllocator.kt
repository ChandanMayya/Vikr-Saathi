package com.kex.vikrsaathi.domain.inventory

object BatchState {
    const val ACTIVE = "ACTIVE"
    const val UNKNOWN = "UNKNOWN"
    const val QUARANTINE = "QUARANTINE"
    const val EXPIRED = "EXPIRED"
}

object BatchSource {
    const val OPENING = "OPENING"
    const val GOODS_RECEIPT = "GOODS_RECEIPT"
    const val ADJUSTMENT = "ADJUSTMENT"
    const val IMPORT = "IMPORT"
    const val SALES_RETURN = "SALES_RETURN"
    const val SALE = "SALE"
}

/**
 * FEFO: earliest expiry first. Lots with no expiry are eligible but are used after dated lots.
 * Expired and quarantined lots are never sold from aggregate stock alone.
 */
object FefoAllocator {

    data class Lot(
        val id: Long,
        val expiryEpochDay: Long?,
        val quantity: Int,
        val state: String,
        val createdAt: Long
    )

    data class Take(val lotId: Long, val quantity: Int)

    data class Plan(
        val taken: List<Take>,
        val eligible: Int,
        val expired: Int,
        val shortfall: Int
    )

    fun plan(lots: List<Lot>, needed: Int, today: Long): Plan {
        val safeNeed = needed.coerceAtLeast(0)
        val expired = lots.filter { isExpired(it, today) }.sumOf { it.quantity.coerceAtLeast(0) }
        val eligibleLots = lots.filter { isEligible(it, today) }
            .sortedWith(
                compareBy<Lot> { it.expiryEpochDay == null }
                    .thenBy { it.expiryEpochDay ?: Long.MAX_VALUE }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
        val eligible = eligibleLots.sumOf { it.quantity }
        var left = safeNeed
        val taken = mutableListOf<Take>()
        for (lot in eligibleLots) {
            if (left == 0) break
            val use = minOf(lot.quantity, left)
            if (use > 0) {
                taken += Take(lot.id, use)
                left -= use
            }
        }
        return Plan(taken, eligible, expired, left)
    }

    fun isExpired(lot: Lot, today: Long): Boolean {
        if (lot.quantity <= 0) return false
        if (lot.state == BatchState.QUARANTINE) return false
        if (lot.state == BatchState.EXPIRED) return true
        return lot.expiryEpochDay != null && lot.expiryEpochDay < today
    }

    fun isEligible(lot: Lot, today: Long): Boolean {
        if (lot.quantity <= 0) return false
        if (lot.state == BatchState.QUARANTINE || lot.state == BatchState.EXPIRED) return false
        if (lot.expiryEpochDay != null && lot.expiryEpochDay < today) return false
        return lot.state == BatchState.ACTIVE || lot.state == BatchState.UNKNOWN
    }
}
