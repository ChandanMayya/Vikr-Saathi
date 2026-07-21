package com.kex.vikrsaathi.util

enum class ThemeMode {
    LIGHT,
    DARK,
    AUTO,
    SYSTEM;

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.find { it.name == value } ?: SYSTEM
    }
}
