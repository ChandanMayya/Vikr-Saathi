package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bill_drafts")
data class BillDraftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerId: Long?,
    val customerName: String,
    val buyerAddress: String,
    val buyerPhone: String,
    val lineItemsJson: String,
    val grandTotal: Double,
    val itemCount: Int,
    val heldAt: Long = System.currentTimeMillis()
)
