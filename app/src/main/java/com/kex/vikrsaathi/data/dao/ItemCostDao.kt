package com.kex.vikrsaathi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kex.vikrsaathi.data.entity.ItemCostLot

@Dao
interface ItemCostDao {
    @Insert
    suspend fun insert(lot: ItemCostLot): Long

    @Insert
    suspend fun insertAll(lots: List<ItemCostLot>)

    @Query(
        """
        SELECT * FROM item_cost_lots
        WHERE itemId = :itemId AND qtyRemaining > 0
        ORDER BY receivedAt ASC, id ASC
        """
    )
    suspend fun availableLots(itemId: Long): List<ItemCostLot>

    @Query(
        """
        SELECT * FROM item_cost_lots
        WHERE sourceType = :sourceType AND sourceId = :sourceId
        """
    )
    suspend fun lotsForSource(sourceType: String, sourceId: Long): List<ItemCostLot>

    @Query("SELECT * FROM item_cost_lots WHERE sourceType = :sourceType")
    suspend fun lotsForType(sourceType: String): List<ItemCostLot>

    @Query("UPDATE item_cost_lots SET qtyRemaining = :qtyRemaining WHERE id = :id")
    suspend fun updateRemaining(id: Long, qtyRemaining: Int)

    @Query("DELETE FROM item_cost_lots WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun deleteSource(sourceType: String, sourceId: Long)

    @Query("DELETE FROM item_cost_lots")
    suspend fun deleteAll()

    @Query("SELECT * FROM item_cost_lots ORDER BY id ASC")
    suspend fun getAll(): List<ItemCostLot>
}
