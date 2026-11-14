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

/** Raised when a restore targets a bill row that is no longer in the shop. */
class BillNotFoundException : IllegalStateException("This bill is no longer in the shop.")

/** Raised when deleting a bill would leave a return or exchange inconsistent. */
class BillLinkedReturnException(
    val billId: Long
) : IllegalStateException("This bill is part of a return or exchange and cannot be deleted.")
