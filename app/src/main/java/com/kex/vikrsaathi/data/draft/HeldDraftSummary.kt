package com.kex.vikrsaathi.data.draft

data class HeldDraftSummary(
    val id: Long,
    val customerName: String,
    val itemCount: Int,
    val grandTotal: Double,
    val heldAt: Long
)

data class HeldBillRestore(
    val id: Long,
    val customerId: Long?,
    val customerName: String,
    val buyerAddress: String,
    val buyerPhone: String,
    val lineItems: List<com.kex.vikrsaathi.data.model.BillLineItem>,
    val roundOff: Double = 0.0
)
