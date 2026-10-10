package com.kex.vikrsaathi.domain.finance

data class LedgerRow(
    val id: Long,
    val partyId: Long,
    val entryType: String,
    val amountPaise: Long,
    val occurredAt: Long,
    val reversesEntryId: Long? = null,
    val billId: Long? = null,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val method: String? = null,
    val note: String? = null,
    val reason: String? = null
)

data class StatementLine(
    val row: LedgerRow,
    val runningBalancePaise: Long
)

data class LedgerStatement(
    val openingPaise: Long,
    val closingPaise: Long,
    val lines: List<StatementLine>
)

data class OutstandingBill(
    val billId: Long,
    val occurredAt: Long,
    val outstandingPaise: Long
)

data class AllocationLine(
    val billId: Long,
    val amountPaise: Long
)

data class AllocationPlan(
    val lines: List<AllocationLine>,
    val unallocatedPaise: Long
)

enum class AgeBucket {
    CURRENT,
    DAYS_31_60,
    DAYS_61_90,
    OVER_90
}

object LedgerMath {

    fun balance(rows: List<LedgerRow>): Long = rows.sumOf { it.amountPaise }

    fun unreversed(rows: List<LedgerRow>): List<LedgerRow> {
        val reversedIds = rows.mapNotNull { it.reversesEntryId }.toSet()
        return rows.filter { it.id !in reversedIds && it.entryType != CustomerLedgerType.REVERSAL && it.entryType != VendorLedgerType.REVERSAL }
    }

    /**
     * Opening balance is the sum of entries strictly before [fromInclusive].
     * Closing balance is the opening plus entries through [toInclusive].
     */
    fun statement(rows: List<LedgerRow>, fromInclusive: Long, toInclusive: Long): LedgerStatement {
        val ordered = rows.sortedWith(compareBy({ it.occurredAt }, { it.id }))
        val opening = ordered.filter { it.occurredAt < fromInclusive }.sumOf { it.amountPaise }
        var running = opening
        val lines = ordered
            .filter { it.occurredAt in fromInclusive..toInclusive }
            .map { row ->
                running += row.amountPaise
                StatementLine(row, running)
            }
        return LedgerStatement(opening, running, lines)
    }

    /**
     * Applies [paymentPaise] to [preferredBillId] first, then oldest outstanding bill.
     * Any remainder stays unallocated and still reduces the party balance via the receipt entry.
     */
    fun allocateOldestFirst(
        outstanding: List<OutstandingBill>,
        paymentPaise: Long,
        preferredBillId: Long? = null
    ): AllocationPlan {
        require(paymentPaise >= 0L)
        val open = outstanding.filter { it.outstandingPaise > 0L }
        val ordered = buildList {
            if (preferredBillId != null) {
                open.firstOrNull { it.billId == preferredBillId }?.let { add(it) }
            }
            addAll(open.filter { it.billId != preferredBillId }.sortedBy { it.occurredAt })
        }
        var remaining = paymentPaise
        val lines = mutableListOf<AllocationLine>()
        for (bill in ordered) {
            if (remaining <= 0L) break
            val take = minOf(bill.outstandingPaise, remaining)
            if (take > 0L) {
                lines += AllocationLine(bill.billId, take)
                remaining -= take
            }
        }
        return AllocationPlan(lines, remaining)
    }

    fun billOutstanding(
        creditPaise: Long,
        allocatedPaise: Long,
        returnCreditPaise: Long
    ): Long = (creditPaise - allocatedPaise - returnCreditPaise).coerceAtLeast(0L)

    fun ageBucket(anchorAt: Long, now: Long): AgeBucket {
        val days = ((now - anchorAt) / MILLIS_PER_DAY).coerceAtLeast(0L)
        return when {
            days <= 30L -> AgeBucket.CURRENT
            days <= 60L -> AgeBucket.DAYS_31_60
            days <= 90L -> AgeBucket.DAYS_61_90
            else -> AgeBucket.OVER_90
        }
    }

    /**
     * Age from the oldest bill that still has an outstanding amount.
     * If the balance is only an opening balance, [openingAt] is the anchor.
     */
    fun ageingAnchor(
        balancePaise: Long,
        outstandingBills: List<OutstandingBill>,
        openingAt: Long?
    ): Long? {
        if (balancePaise <= 0L) return null
        val oldestBill = outstandingBills
            .filter { it.outstandingPaise > 0L }
            .minOfOrNull { it.occurredAt }
        return oldestBill ?: openingAt
    }

    private const val MILLIS_PER_DAY = 86_400_000L
}
