package com.kex.vikrsaathi.data.repository

class InsufficientStockException(
    val shortfalls: List<StockShortfall>
) : IllegalStateException(
    shortfalls.joinToString(separator = "\n") { shortfall ->
        "${shortfall.itemName} — available ${shortfall.available}, needed ${shortfall.required}"
    }
)

/** Raised when a bill delete would have to drop immutable version snapshots. */
class BillHistoryRetainedException(
    val billId: Long
) : IllegalStateException("Bill $billId has version history and cannot be deleted")
