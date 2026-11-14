package com.kex.vikrsaathi.domain.inventory

/** A lot the operator can sell from while batch tracking is on. */
data class SaleLot(
    val id: Long,
    val lotNumber: String?,
    val internalRef: String,
    val expiryEpochDay: Long?,
    val quantityOnHand: Int
)

/** Quantity already posted from one lot onto a saved bill line. */
data class SavedLotUse(
    val batchId: Long,
    val lotNumber: String?,
    val internalRef: String,
    val expiryEpochDay: Long?,
    val quantity: Int
)
