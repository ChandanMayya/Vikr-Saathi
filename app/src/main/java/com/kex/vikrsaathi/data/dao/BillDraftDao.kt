package com.kex.vikrsaathi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kex.vikrsaathi.data.entity.BillDraftEntity

@Dao
interface BillDraftDao {

    @Query("SELECT * FROM bill_drafts ORDER BY heldAt DESC")
    suspend fun getAll(): List<BillDraftEntity>

    @Query("SELECT * FROM bill_drafts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BillDraftEntity?

    @Insert
    suspend fun insert(draft: BillDraftEntity): Long

    @Query("DELETE FROM bill_drafts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bill_drafts")
    suspend fun deleteAll()
}
