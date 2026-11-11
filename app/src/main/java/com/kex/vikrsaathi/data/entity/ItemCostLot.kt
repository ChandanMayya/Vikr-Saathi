package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object CostSourceType {
    const val PURCHASE_INVOICE = "PURCHASE_INVOICE"
    const val SALE_REVERSAL = "SALE_REVERSAL"
    const val SALES_RETURN = "SALES_RETURN"
}

/**
 * Remaining units whose cost came from a purchase invoice, a reversed sale, or a return.
 * Opening stock, imports, and goods receipts without an invoice never create a lot.
 */
@Entity(
    tableName = "item_cost_lots",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("itemId"),
        Index(value = ["sourceType", "sourceId"])
    ]
)
data class ItemCostLot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Long,
    val sourceType: String,
    val sourceId: Long,
    val qtyReceived: Int,
    val qtyRemaining: Int,
    val unitCostPaise: Long,
    val receivedAt: Long
)
