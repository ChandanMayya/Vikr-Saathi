package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class StockMovementType {
    SALE,
    SALE_REVERSAL,
    ADJUSTMENT,
    OPENING,
    IMPORT,
    PURCHASE_RECEIPT,
    PURCHASE_REVERSAL,
    SALES_RETURN
}

object StockReferenceType {
    const val BILL = "BILL"
    const val MANUAL = "MANUAL"
    const val GOODS_RECEIPT = "GOODS_RECEIPT"
    const val SALES_RETURN = "SALES_RETURN"
}

@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["itemId", "createdAt"]),
        Index(value = ["referenceType", "referenceId"])
    ]
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Long,
    val delta: Int,
    val quantityAfter: Int,
    val type: String,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
