package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    indices = [Index(value = ["barcode"], unique = true), Index("preferredVendorId")]
)
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val mrp: Double,
    val discount: Double = 0.0,
    val sellingPrice: Double? = null,
    val remarks: String = "",
    val stockQty: Int = 0,
    /** Soft reference. Archived vendors stay linked; hard-delete clears this in code. */
    val preferredVendorId: Long? = null,
    /** Stock-unit label. Blank leaves sales and stock quantities unlabeled. */
    val baseUnit: String = "",
    /** Blank means this item has no purchase-unit default. */
    val purchaseUnit: String = "",
    val unitsPerPurchase: Int = 1
) {
    /** The optional item unit. Packaging no longer stores a second base-unit name. */
    fun stockUnit(): String = baseUnit.ifBlank { remarks }
}
