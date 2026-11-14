package com.kex.vikrsaathi.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_batches",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itemId"), Index(value = ["sourceType", "sourceId"])]
)
data class InventoryBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val lotNumber: String? = null,
    val internalRef: String,
    /** Null means the expiry was never known. Not a formatted date string. */
    val expiryEpochDay: Long? = null,
    val quantityOnHand: Int,
    val createdAt: Long,
    val sourceType: String,
    val sourceId: Long? = null,
    val state: String
)

@Entity(
    tableName = "inventory_batch_movements",
    foreignKeys = [
        ForeignKey(
            entity = InventoryBatch::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("batchId"), Index(value = ["referenceType", "referenceId"])]
)
data class InventoryBatchMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val delta: Int,
    val quantityAfter: Int,
    val type: String,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bill_batch_allocations",
    foreignKeys = [
        ForeignKey(
            entity = Bill::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InventoryBatch::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("billId"), Index("batchId"), Index("billItemId")]
)
data class BillBatchAllocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val billItemId: Long? = null,
    val batchId: Long,
    val quantity: Int,
    /** Lot identity at the time of the sale, so later edits to the lot do not rewrite this bill. */
    val lotNumber: String? = null,
    val internalRef: String? = null,
    val expiryEpochDay: Long? = null
)
