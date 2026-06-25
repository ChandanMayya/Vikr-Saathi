package com.kex.vikrsaathi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kex.vikrsaathi.data.entity.BillItem

@Dao
interface BillItemDao {

    @Insert
    suspend fun insertAll(items: List<BillItem>)

    @Insert
    suspend fun insert(item: BillItem): Long

    @Query("SELECT * FROM bill_items WHERE billId = :billId ORDER BY id ASC")
    suspend fun getItemsForBill(billId: Long): List<BillItem>

    @Query("DELETE FROM bill_items WHERE billId = :billId")
    suspend fun deleteItemsForBill(billId: Long)
}
