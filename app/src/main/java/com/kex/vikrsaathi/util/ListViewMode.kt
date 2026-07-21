package com.kex.vikrsaathi.util

enum class ListViewMode {
    COMFORTABLE,
    COMPACT,
    DETAILS;

    companion object {
        fun fromStored(value: String?): ListViewMode? {
            if (value.isNullOrBlank()) return null
            return entries.find { it.name == value }
        }

        fun fromStoredOrDefault(value: String?, default: ListViewMode = COMFORTABLE): ListViewMode =
            fromStored(value) ?: default
    }
}

/**
 * Screens that support list density modes.
 *
 * [pageDefault] is used when the user has never chosen a mode on that screen,
 * but before falling back to the app-wide master (null = inherit master).
 */
enum class ListViewScreen(val pageDefault: ListViewMode? = null) {
    CUSTOMERS,
    ITEMS,
    BILLS_HISTORY,
    INVENTORY
}
