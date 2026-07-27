package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invoice_templates")
data class InvoiceTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isDefault: Boolean,
    val sheetType: String = "A4",
    val pageWidthPt: Int,
    val pageHeightPt: Int,
    val marginLeft: Float,
    val marginTop: Float,
    val marginRight: Float,
    val marginBottom: Float,
    val elementsJson: String,
    val version: Int,
    val updatedAt: Long
)
