package com.kex.vikrsaathi.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Immutable snapshot of a bill. Rows are inserted when a bill is edited and are not updated.
 * Deleting the bill is rejected while these rows exist, so history is not cascade-erased.
 */
@Entity(
    tableName = "bill_versions",
    foreignKeys = [
        ForeignKey(
            entity = Bill::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("billId"),
        Index(value = ["billId", "versionNumber"], unique = true)
    ]
)
data class BillVersion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val billNumber: String,
    val versionNumber: Int,
    val createdAt: Long,
    val saleDate: Long,
    val customerId: Long? = null,
    val customerName: String,
    val customerPhone: String = "",
    val customerAddress: String = "",
    val total: Double,
    val roundOff: Double = 0.0,
    val paymentStatus: String,
    val paymentLabel: String,
    val changeSummary: String,
    @ColumnInfo(defaultValue = "'This device'")
    val actorLabel: String = "This device"
)

@Entity(
    tableName = "bill_version_items",
    foreignKeys = [
        ForeignKey(
            entity = BillVersion::class,
            parentColumns = ["id"],
            childColumns = ["versionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("versionId")]
)
data class BillVersionItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val versionId: Long,
    val itemId: Long? = null,
    val itemName: String,
    val quantity: Int,
    val mrp: Double,
    val discount: Double,
    val finalPrice: Double,
    val roundOff: Double = 0.0,
    val unitCostPaise: Long? = null,
    val warrantyPeriod: Int? = null,
    val warrantyUnit: String? = null,
    val warrantyProvider: String? = null,
    val warrantyProviderName: String? = null,
    val warrantyTerms: String? = null,
    val warrantyStartEpochDay: Long? = null,
    val warrantyEndEpochDay: Long? = null
)

@Entity(
    tableName = "bill_version_payments",
    foreignKeys = [
        ForeignKey(
            entity = BillVersion::class,
            parentColumns = ["id"],
            childColumns = ["versionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("versionId")]
)
data class BillVersionPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val versionId: Long,
    val method: String,
    val amountPaise: Long
)
