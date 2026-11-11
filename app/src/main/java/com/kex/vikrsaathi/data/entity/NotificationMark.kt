package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_marks",
    indices = [Index(value = ["slotKey"], unique = true)]
)
data class NotificationMark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotKey: String,
    val outcome: String,
    val createdAt: Long
)
