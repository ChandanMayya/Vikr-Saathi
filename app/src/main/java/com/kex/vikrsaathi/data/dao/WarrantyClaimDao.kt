package com.kex.vikrsaathi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kex.vikrsaathi.data.entity.WarrantyClaim
import com.kex.vikrsaathi.data.entity.WarrantyClaimEvent
import com.kex.vikrsaathi.data.model.WarrantySaleLine

@Dao
interface WarrantyClaimDao {

    @Query(
        """
        SELECT bi.id AS billItemId, bi.billId AS billId, bi.itemName AS itemName,
               b.billNumber AS billNumber, b.date AS saleDateMillis,
               c.name AS customerName, c.phone AS customerPhone,
               bi.warrantyPeriod AS warrantyPeriod, bi.warrantyUnit AS warrantyUnit,
               bi.warrantyProvider AS warrantyProvider, bi.warrantyProviderName AS warrantyProviderName,
               bi.warrantyTerms AS warrantyTerms, bi.warrantyStartEpochDay AS warrantyStartEpochDay,
               bi.warrantyEndEpochDay AS warrantyEndEpochDay
        FROM bill_items bi
        INNER JOIN bills b ON b.id = bi.billId
        LEFT JOIN customers c ON c.id = b.customerId
        WHERE :query = ''
           OR b.billNumber LIKE '%' || :query || '%'
           OR bi.itemName LIKE '%' || :query || '%'
           OR IFNULL(c.name, '') LIKE '%' || :query || '%'
        ORDER BY b.date DESC, bi.id DESC
        """
    )
    suspend fun searchSaleLines(query: String): List<WarrantySaleLine>

    @Query(
        """
        SELECT bi.id AS billItemId, bi.billId AS billId, bi.itemName AS itemName,
               b.billNumber AS billNumber, b.date AS saleDateMillis,
               c.name AS customerName, c.phone AS customerPhone,
               bi.warrantyPeriod AS warrantyPeriod, bi.warrantyUnit AS warrantyUnit,
               bi.warrantyProvider AS warrantyProvider, bi.warrantyProviderName AS warrantyProviderName,
               bi.warrantyTerms AS warrantyTerms, bi.warrantyStartEpochDay AS warrantyStartEpochDay,
               bi.warrantyEndEpochDay AS warrantyEndEpochDay
        FROM bill_items bi
        INNER JOIN bills b ON b.id = bi.billId
        LEFT JOIN customers c ON c.id = b.customerId
        WHERE bi.id = :billItemId
        """
    )
    suspend fun saleLine(billItemId: Long): WarrantySaleLine?

    @Query("SELECT * FROM warranty_claims ORDER BY createdAt DESC, id DESC")
    suspend fun allClaims(): List<WarrantyClaim>

    @Query("SELECT * FROM warranty_claims WHERE id = :id")
    suspend fun getClaim(id: Long): WarrantyClaim?

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM warranty_claims")
    suspend fun maxSequence(): Int

    @Query("SELECT COUNT(*) FROM warranty_claims WHERE reference = :reference")
    suspend fun countReference(reference: String): Int

    @Insert
    suspend fun insertClaim(claim: WarrantyClaim): Long

    @Update
    suspend fun updateClaim(claim: WarrantyClaim)

    @Insert
    suspend fun insertEvent(event: WarrantyClaimEvent): Long

    @Query("SELECT * FROM warranty_claim_events WHERE claimId = :claimId ORDER BY createdAt ASC, id ASC")
    suspend fun eventsFor(claimId: Long): List<WarrantyClaimEvent>

    @Query("SELECT * FROM warranty_claim_events ORDER BY id ASC")
    suspend fun allEvents(): List<WarrantyClaimEvent>

    @Query("DELETE FROM warranty_claim_events")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM warranty_claims")
    suspend fun deleteAll()
}
