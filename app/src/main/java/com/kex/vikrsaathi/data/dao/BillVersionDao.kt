package com.kex.vikrsaathi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kex.vikrsaathi.data.entity.BillVersion
import com.kex.vikrsaathi.data.entity.BillVersionItem
import com.kex.vikrsaathi.data.entity.BillVersionPayment

/**
 * Insert-only access to bill snapshots. There is no update or per-version delete.
 * [deleteAllVersions] exists so a full sales reset can remove history explicitly.
 */
@Dao
interface BillVersionDao {

    @Insert
    suspend fun insertVersion(version: BillVersion): Long

    @Insert
    suspend fun insertItems(items: List<BillVersionItem>)

    @Insert
    suspend fun insertPayments(payments: List<BillVersionPayment>)

    @Query("SELECT COUNT(*) FROM bill_versions WHERE billId = :billId")
    suspend fun countForBill(billId: Long): Int

    @Query("SELECT COALESCE(MAX(versionNumber), 0) FROM bill_versions WHERE billId = :billId")
    suspend fun maxVersionNumber(billId: Long): Int

    @Query("SELECT * FROM bill_versions WHERE billId = :billId ORDER BY versionNumber DESC")
    suspend fun getVersionsForBill(billId: Long): List<BillVersion>

    @Query("SELECT * FROM bill_versions WHERE id = :id")
    suspend fun getVersion(id: Long): BillVersion?

    @Query("SELECT * FROM bill_version_items WHERE versionId = :versionId ORDER BY id ASC")
    suspend fun getItems(versionId: Long): List<BillVersionItem>

    @Query("SELECT * FROM bill_version_payments WHERE versionId = :versionId ORDER BY id ASC")
    suspend fun getPayments(versionId: Long): List<BillVersionPayment>

    @Query("DELETE FROM bill_version_payments")
    suspend fun deleteAllPayments()

    @Query("DELETE FROM bill_version_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM bill_versions")
    suspend fun deleteAllVersions()
}
