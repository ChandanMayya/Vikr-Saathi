package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = Bill::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("billId"), Index("itemId")]
)
data class BillItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val itemId: Long? = null,
    val itemName: String,
    val quantity: Int = 1,
    val mrp: Double,
    val discount: Double,
    val finalPrice: Double,
    val roundOff: Double = 0.0,
    /** Purchase-invoice cost captured when this line was saved. Null means the cost was not known. */
    val unitCostPaise: Long? = null,
    /** Null means this sale has no saved warranty snapshot. Do not infer one from the item master. */
    val warrantyPeriod: Int? = null,
    val warrantyUnit: String? = null,
    val warrantyProvider: String? = null,
    val warrantyProviderName: String? = null,
    val warrantyTerms: String? = null,
    val warrantyStartEpochDay: Long? = null,
    val warrantyEndEpochDay: Long? = null
)
