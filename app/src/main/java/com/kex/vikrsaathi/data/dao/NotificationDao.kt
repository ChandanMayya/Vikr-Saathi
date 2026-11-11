package com.kex.vikrsaathi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kex.vikrsaathi.data.entity.NotificationMark

@Dao
interface NotificationDao {
    @Query("SELECT slotKey FROM notification_marks")
    suspend fun handledSlots(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(mark: NotificationMark): Long

    @Query("DELETE FROM notification_marks")
    suspend fun clear()
}
