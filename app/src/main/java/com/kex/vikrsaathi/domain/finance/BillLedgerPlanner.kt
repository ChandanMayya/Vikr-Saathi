package com.kex.vikrsaathi.domain.finance

data class PlannedReversal(
    val partyId: Long,
    val amountPaise: Long,
    val reversesEntryId: Long,
    val billId: Long?,
    val reason: String
)

data class PlannedPosting(
    val partyId: Long,
    val entryType: String,
    val amountPaise: Long,
    val billId: Long?,
    val referenceType: String,
    val referenceId: Long?
)

data class LedgerPlan(
    val reversals: List<PlannedReversal>,
    val posting: PlannedPosting?
)

object BillLedgerPlanner {

    /**
     * Reverses active credit-sale entries for a bill when the customer or credit amount changes,
     * then posts the new credit portion. Cash and UPI portions are not posted.
     * A zero credit posts nothing new, so a walk-in or fully settled sale creates no debt.
     */
    fun planCreditSale(
        existing: List<LedgerRow>,
        newCustomerId: Long?,
        newCreditPaise: Long,
        billId: Long,
        reason: String
    ): LedgerPlan {
        val reversedIds = existing.mapNotNull { it.reversesEntryId }.toSet()
        val active = existing.filter {
            it.entryType == CustomerLedgerType.CREDIT_SALE &&
                it.billId == billId &&
                it.id !in reversedIds
        }
        val desiredCustomer = if (newCreditPaise > 0L) newCustomerId else null
        val unchanged = desiredCustomer != null &&
            active.size == 1 &&
            active[0].partyId == desiredCustomer &&
            active[0].amountPaise == newCreditPaise
        if (unchanged) return LedgerPlan(emptyList(), null)
        if (desiredCustomer == null && active.isEmpty()) return LedgerPlan(emptyList(), null)

        val reversals = active.map { row ->
            PlannedReversal(
                partyId = row.partyId,
                amountPaise = -row.amountPaise,
                reversesEntryId = row.id,
                billId = billId,
                reason = reason
            )
        }
        val posting = if (desiredCustomer != null && newCreditPaise > 0L) {
            PlannedPosting(
                partyId = desiredCustomer,
                entryType = CustomerLedgerType.CREDIT_SALE,
                amountPaise = newCreditPaise,
                billId = billId,
                referenceType = FinanceReference.BILL,
                referenceId = billId
            )
        } else {
            null
        }
        return LedgerPlan(reversals, posting)
    }

    fun planNegatingReversal(
        original: LedgerRow,
        reason: String
    ): PlannedReversal {
        if (reason.isBlank()) throw FinanceException(FinanceError.REASON_REQUIRED)
        return PlannedReversal(
            partyId = original.partyId,
            amountPaise = -original.amountPaise,
            reversesEntryId = original.id,
            billId = original.billId,
            reason = reason.trim()
        )
    }
}
