package com.kex.vikrsaathi.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kex.vikrsaathi.data.entity.Item

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getAllItems(): LiveData<List<Item>>

    @Query("SELECT * FROM items ORDER BY name ASC")
    suspend fun getAllItemsSync(): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: Long): Item?

    @Query("SELECT * FROM items WHERE barcode = :barcode LIMIT 1")
    suspend fun getItemByBarcode(barcode: String): Item?

    @Query("SELECT * FROM items WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT 20")
    suspend fun searchByName(query: String): List<Item>

    @Query("SELECT COUNT(*) FROM items WHERE barcode = :barcode AND id != :excludeId")
    suspend fun countByBarcode(barcode: String, excludeId: Long = 0): Int

    @Query("UPDATE items SET stockQty = :stockQty WHERE id = :itemId")
    suspend fun updateStockQty(itemId: Long, stockQty: Int)

    @Query("SELECT * FROM items WHERE stockQty <= :threshold ORDER BY stockQty ASC, name ASC")
    suspend fun getLowStockItems(threshold: Int): List<Item>

    @Query("SELECT COUNT(*) FROM items WHERE stockQty <= :threshold")
    suspend fun countLowStockItems(threshold: Int): Int

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}
