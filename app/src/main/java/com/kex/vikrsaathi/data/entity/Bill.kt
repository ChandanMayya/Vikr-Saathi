package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("customerId"), Index("billNumber"), Index("invoiceCounter")]
)
data class Bill(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billNumber: String,
    val invoiceCounter: Int = 0,
    val customerId: Long? = null,
    val total: Double,
    val roundOff: Double = 0.0,
    val date: Long = System.currentTimeMillis(),
    /** LEGACY bills have no payment rows and are not treated as customer debt. */
    val paymentStatus: String = com.kex.vikrsaathi.domain.finance.BillPaymentStatus.LEGACY,
    /** Machine code: LEGACY, CASH, UPI, CREDIT, or SPLIT. */
    val paymentLabel: String = com.kex.vikrsaathi.domain.finance.BillPaymentStatus.LABEL_LEGACY
)
