package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    indices = [Index(value = ["barcode"], unique = true)]
)
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val mrp: Double,
    val discount: Double = 0.0,
    val sellingPrice: Double? = null,
    val remarks: String = ""
)
