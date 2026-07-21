package com.kex.vikrsaathi.util

enum class InventoryMode {
    OFF,
    WARN,
    BLOCK;

    companion object {
        fun fromStored(value: String?): InventoryMode =
            entries.find { it.name == value } ?: WARN
    }
}
