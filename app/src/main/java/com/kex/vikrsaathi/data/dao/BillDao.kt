package com.kex.vikrsaathi.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.data.model.TopSoldItemRow

@Dao
interface BillDao {

    @Insert
    suspend fun insertBill(bill: Bill): Long

    @Update
    suspend fun updateBill(bill: Bill)

    @Delete
    suspend fun deleteBill(bill: Bill)

    @Query("SELECT * FROM bills ORDER BY date DESC")
    fun getAllBills(): LiveData<List<Bill>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBillById(id: Long): Bill?

    @Query(
        """
        SELECT * FROM bills 
        WHERE billNumber LIKE '%' || :query || '%' 
        ORDER BY date DESC
        """
    )
    fun searchBills(query: String): LiveData<List<Bill>>

    @Query("SELECT MAX(invoiceCounter) FROM bills")
    suspend fun getMaxInvoiceCounter(): Int?

    @Query("SELECT MAX(CAST(billNumber AS INTEGER)) FROM bills WHERE billNumber GLOB '[0-9]*'")
    suspend fun getMaxBillNumber(): Int?

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :billId")
    suspend fun getBillWithDetails(billId: Long): BillWithDetails?

    @Transaction
    @Query("SELECT * FROM bills ORDER BY date DESC")
    fun getAllBillsWithDetails(): LiveData<List<BillWithDetails>>

    @Transaction
    @Query("SELECT * FROM bills ORDER BY date DESC")
    suspend fun getAllBillsWithDetailsSync(): List<BillWithDetails>

    @Query("SELECT COUNT(*) FROM bills WHERE billNumber = :billNumber")
    suspend fun countByBillNumber(billNumber: String): Int

    @Query("DELETE FROM bills")
    suspend fun deleteAll()

    @Query(
        """
        SELECT COALESCE(SUM(total), 0) FROM bills
        WHERE date >= :startOfDay AND date < :endOfDay
        """
    )
    suspend fun getTotalSalesBetween(startOfDay: Long, endOfDay: Long): Double

    @Query(
        """
        SELECT COUNT(*) FROM bills
        WHERE date >= :startOfDay AND date < :endOfDay
        """
    )
    suspend fun getBillCountBetween(startOfDay: Long, endOfDay: Long): Int

    @Query(
        """
        SELECT bi.itemName AS itemName, SUM(bi.quantity) AS totalQuantity
        FROM bill_items bi
        INNER JOIN bills b ON b.id = bi.billId
        WHERE b.date >= :startOfDay AND b.date < :endOfDay
        GROUP BY bi.itemName
        ORDER BY totalQuantity DESC
        LIMIT 5
        """
    )
    suspend fun getTopSoldItemsBetween(startOfDay: Long, endOfDay: Long): List<TopSoldItemRow>
}
