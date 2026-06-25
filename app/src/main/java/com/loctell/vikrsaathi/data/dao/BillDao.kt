package com.loctell.vikrsaathi.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.loctell.vikrsaathi.data.entity.Bill
import com.loctell.vikrsaathi.data.model.BillWithDetails

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

    @Query("SELECT COUNT(*) FROM bills WHERE billNumber = :billNumber")
    suspend fun countByBillNumber(billNumber: String): Int
}
