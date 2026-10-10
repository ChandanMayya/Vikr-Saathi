package com.kex.vikrsaathi.domain.finance

/**
 * Backup restore inserts new rows and remaps legacy ids.
 * When a reset restores only one section, the referenced row may still exist
 * at its original id. Relationships are kept in either case.
 */
object IdRemapper {

    fun resolve(legacyId: Long?, mapped: Map<Long, Long>, stillPresent: Set<Long>): Long? {
        if (legacyId == null || legacyId <= 0L) return null
        mapped[legacyId]?.let { return it }
        if (legacyId in stillPresent) return legacyId
        return null
    }
}

/**
 * Remaps backup reference ids after the related rows have new primary keys.
 * Unknown reference types keep the stored id because they are not foreign keys.
 */
object FinanceRestoreLinks {

    fun customerReference(
        referenceType: String?,
        referenceId: Long?,
        billIds: Map<Long, Long>,
        presentBillIds: Set<Long>,
        receiptIds: Map<Long, Long>,
        returnIds: Map<Long, Long>
    ): Long? {
        if (referenceId == null) return null
        return when (referenceType) {
            FinanceReference.BILL -> IdRemapper.resolve(referenceId, billIds, presentBillIds)
            FinanceReference.CUSTOMER_RECEIPT -> receiptIds[referenceId]
            FinanceReference.SALES_RETURN -> returnIds[referenceId]
            else -> referenceId
        }
    }

    fun vendorReference(
        referenceType: String?,
        referenceId: Long?,
        invoiceIds: Map<Long, Long>,
        paymentIds: Map<Long, Long>
    ): Long? {
        if (referenceId == null) return null
        return when (referenceType) {
            FinanceReference.PURCHASE_INVOICE -> invoiceIds[referenceId]
            FinanceReference.VENDOR_PAYMENT -> paymentIds[referenceId]
            else -> referenceId
        }
    }
}
