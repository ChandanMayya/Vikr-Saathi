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
import com.kex.vikrsaathi.data.model.analytics.BillTotalRow
import com.kex.vikrsaathi.data.model.analytics.CustomerMixRow
import com.kex.vikrsaathi.data.model.analytics.DiscountTotalsRow
import com.kex.vikrsaathi.data.model.analytics.TopCustomerRow
import com.kex.vikrsaathi.data.model.analytics.TopProductRow

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

    @Query(
        """
        SELECT date AS billDate, total AS billTotal FROM bills
        WHERE date >= :startInclusive AND date < :endExclusive
        ORDER BY date ASC
        """
    )
    suspend fun getBillTotalsBetween(startInclusive: Long, endExclusive: Long): List<BillTotalRow>

    @Query(
        """
        SELECT bi.itemName AS itemName,
               CAST(SUM(bi.quantity) AS INTEGER) AS totalQuantity,
               SUM(bi.quantity * bi.finalPrice) AS totalRevenue
        FROM bill_items bi
        INNER JOIN bills b ON b.id = bi.billId
        WHERE b.date >= :startInclusive AND b.date < :endExclusive
        GROUP BY bi.itemName
        ORDER BY totalRevenue DESC
        LIMIT 10
        """
    )
    suspend fun getTopProductsByRevenueBetween(
        startInclusive: Long,
        endExclusive: Long
    ): List<TopProductRow>

    @Query(
        """
        SELECT CASE
                   WHEN b.customerId IS NULL THEN 'Walk-in'
                   ELSE COALESCE(c.name, 'Unknown')
               END AS customerName,
               COUNT(*) AS billCount,
               SUM(b.total) AS totalSpend
        FROM bills b
        LEFT JOIN customers c ON c.id = b.customerId
        WHERE b.date >= :startInclusive AND b.date < :endExclusive
        GROUP BY b.customerId
        ORDER BY totalSpend DESC
        LIMIT 10
        """
    )
    suspend fun getTopCustomersBetween(
        startInclusive: Long,
        endExclusive: Long
    ): List<TopCustomerRow>

    @Query(
        """
        SELECT COALESCE(SUM(bi.quantity * bi.mrp), 0) AS grossAtMrp,
               COALESCE(SUM(bi.quantity * bi.finalPrice), 0) AS netRevenue
        FROM bill_items bi
        INNER JOIN bills b ON b.id = bi.billId
        WHERE b.date >= :startInclusive AND b.date < :endExclusive
        """
    )
    suspend fun getDiscountTotalsBetween(
        startInclusive: Long,
        endExclusive: Long
    ): DiscountTotalsRow

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN customerId IS NULL THEN 1 ELSE 0 END), 0) AS walkInBills,
               COALESCE(SUM(CASE WHEN customerId IS NOT NULL THEN 1 ELSE 0 END), 0) AS registeredBills,
               COALESCE(SUM(CASE WHEN customerId IS NULL THEN total ELSE 0 END), 0) AS walkInSales,
               COALESCE(SUM(CASE WHEN customerId IS NOT NULL THEN total ELSE 0 END), 0) AS registeredSales
        FROM bills
        WHERE date >= :startInclusive AND date < :endExclusive
        """
    )
    suspend fun getCustomerMixBetween(
        startInclusive: Long,
        endExclusive: Long
    ): CustomerMixRow
}
