package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A name chosen on this device. It is not an account or a verified identity. */
@Entity(tableName = "operators")
data class OperatorProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
