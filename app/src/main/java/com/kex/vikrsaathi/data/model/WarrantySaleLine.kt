package com.kex.vikrsaathi.data.model

import com.kex.vikrsaathi.domain.bill.WarrantyPolicy

/** One sold line as it exists on a bill now. Warranty fields are the sale snapshot, not the item master. */
data class WarrantySaleLine(
    val billItemId: Long,
    val billId: Long,
    val itemName: String,
    val billNumber: String,
    val saleDateMillis: Long,
    val customerName: String?,
    val customerPhone: String?,
    val warrantyPeriod: Int?,
    val warrantyUnit: String?,
    val warrantyProvider: String?,
    val warrantyProviderName: String?,
    val warrantyTerms: String?,
    val warrantyStartEpochDay: Long?,
    val warrantyEndEpochDay: Long?
) {
    fun snapshot(): WarrantyPolicy.Snapshot? = WarrantyPolicy.fromStored(
        warrantyPeriod,
        warrantyUnit,
        warrantyProvider,
        warrantyProviderName,
        warrantyTerms,
        warrantyStartEpochDay,
        warrantyEndEpochDay
    )
}
