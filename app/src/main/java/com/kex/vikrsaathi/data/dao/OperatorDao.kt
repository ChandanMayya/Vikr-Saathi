package com.kex.vikrsaathi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kex.vikrsaathi.data.entity.OperatorProfile

@Dao
interface OperatorDao {
    @Insert
    suspend fun insert(profile: OperatorProfile): Long

    @Query("SELECT * FROM operators ORDER BY name COLLATE NOCASE, id ASC")
    suspend fun getAll(): List<OperatorProfile>

    @Query("SELECT * FROM operators WHERE id = :id")
    suspend fun getById(id: Long): OperatorProfile?

    @Query("SELECT * FROM operators WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): OperatorProfile?

    @Query("DELETE FROM operators")
    suspend fun deleteAll()
}
