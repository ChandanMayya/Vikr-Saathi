package com.kex.vikrsaathi.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.model.analytics.SlowMoverRow
import com.kex.vikrsaathi.data.model.analytics.StockValueRow

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

    @Query(
        """
        SELECT COALESCE(SUM(stockQty * COALESCE(sellingPrice, mrp)), 0)
        FROM items
        WHERE stockQty > 0
        """
    )
    suspend fun getTotalInventoryValue(): Double

    @Query("SELECT COUNT(*) FROM items WHERE stockQty > 0")
    suspend fun countInStockItems(): Int

    @Query(
        """
        SELECT name AS itemName,
               stockQty AS stockQty,
               COALESCE(sellingPrice, mrp) AS unitPrice,
               (stockQty * COALESCE(sellingPrice, mrp)) AS stockValue
        FROM items
        WHERE stockQty > 0
        ORDER BY stockValue DESC
        LIMIT 10
        """
    )
    suspend fun getTopStockValueItems(): List<StockValueRow>

    @Query(
        """
        SELECT i.name AS itemName,
               i.stockQty AS stockQty,
               COALESCE(sold.soldQty, 0) AS soldQty
        FROM items i
        LEFT JOIN (
            SELECT bi.itemId AS itemId, SUM(bi.quantity) AS soldQty
            FROM bill_items bi
            INNER JOIN bills b ON b.id = bi.billId
            WHERE b.date >= :startInclusive
              AND b.date < :endExclusive
              AND bi.itemId IS NOT NULL
            GROUP BY bi.itemId
        ) sold ON sold.itemId = i.id
        WHERE i.stockQty > 0
        ORDER BY soldQty ASC, i.stockQty DESC
        LIMIT 10
        """
    )
    suspend fun getSlowMovers(startInclusive: Long, endExclusive: Long): List<SlowMoverRow>
}
