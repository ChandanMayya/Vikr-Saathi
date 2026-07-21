package com.kex.vikrsaathi.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kex.vikrsaathi.data.entity.StockMovement

@Dao
interface StockMovementDao {

    @Insert
    suspend fun insert(movement: StockMovement): Long

    @Insert
    suspend fun insertAll(movements: List<StockMovement>)

    @Query(
        """
        SELECT * FROM stock_movements
        WHERE itemId = :itemId
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getForItem(itemId: Long, limit: Int = 50): List<StockMovement>

    @Query(
        """
        SELECT * FROM stock_movements
        WHERE itemId = :itemId
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit
        """
    )
    fun observeForItem(itemId: Long, limit: Int = 50): LiveData<List<StockMovement>>

    @Query("SELECT * FROM stock_movements ORDER BY createdAt ASC, id ASC")
    suspend fun getAllSync(): List<StockMovement>

    @Query("DELETE FROM stock_movements")
    suspend fun deleteAll()
}
