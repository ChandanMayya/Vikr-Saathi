package com.kex.vikrsaathi.domain.finance

/**
 * Decides which persisted finance rows a reset category may remove.
 * Opening balances and customer receipts survive a sales reset.
 * Bill-linked credit, returns, and their reversals do not.
 * Turning a feature off is not a reset and must not delete rows.
 */
object FinanceResetPolicy {

    fun isBillLinkedCustomerEntry(row: LedgerRow): Boolean {
        if (row.billId != null) return true
        return row.referenceType == FinanceReference.BILL ||
            row.referenceType == FinanceReference.SALES_RETURN
    }

    fun featureTogglePreservesData(): Boolean = true
}
