package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address1: String = "",
    val address2: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val phone: String = "",
    val remarks: String = ""
) {
    fun formattedAddress(): String {
        return listOf(address1, address2, city, state, pincode)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }
}
