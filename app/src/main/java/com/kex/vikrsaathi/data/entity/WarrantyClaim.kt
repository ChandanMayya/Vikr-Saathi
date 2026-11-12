package com.kex.vikrsaathi.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy

/**
 * A warranty claim saved against one sold line. Names, invoice number, and warranty
 * dates are copies from that sale. Later edits to the item or the bill do not rewrite them.
 * [billId] and [billItemId] are links only. They are not foreign keys, so deleting or
 * replacing the sale still leaves this claim printable.
 */
@Entity(
    tableName = "warranty_claims",
    indices = [
        Index(value = ["reference"], unique = true),
        Index("billId"),
        Index("billItemId"),
        Index("status")
    ]
)
data class WarrantyClaim(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reference: String,
    val sequence: Int,
    val billId: Long? = null,
    val billItemId: Long? = null,
    val billNumber: String,
    val saleDateMillis: Long,
    val customerName: String = "",
    val customerPhone: String = "",
    val itemName: String,
    val warrantyPeriod: Int? = null,
    val warrantyUnit: String? = null,
    val warrantyProvider: String? = null,
    val warrantyProviderName: String? = null,
    val warrantyTerms: String? = null,
    val warrantyStartEpochDay: Long? = null,
    val warrantyEndEpochDay: Long? = null,
    val issue: String,
    val condition: String = "",
    val note: String = "",
    val status: String,
    val resolutionNote: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "''") val serviceCentre: String = "",
    @ColumnInfo(defaultValue = "''") val serviceReference: String = "",
    val serviceSentEpochDay: Long? = null,
    val providerOutcome: String? = null,
    @ColumnInfo(defaultValue = "''") val providerOutcomeNote: String = "",
    val providerResponseEpochDay: Long? = null,
    val handoverEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "''") val handoverRecipient: String = "",
    @ColumnInfo(defaultValue = "''") val handoverNote: String = ""
)

@Entity(
    tableName = "warranty_claim_events",
    foreignKeys = [
        ForeignKey(
            entity = WarrantyClaim::class,
            parentColumns = ["id"],
            childColumns = ["claimId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("claimId")]
)
data class WarrantyClaimEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val claimId: Long,
    val status: String,
    val note: String = "",
    val createdAt: Long,
    @ColumnInfo(defaultValue = "'STATUS'") val eventKind: String = WarrantyClaimPolicy.EVENT_STATUS
)
