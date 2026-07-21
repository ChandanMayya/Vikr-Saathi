package com.kex.vikrsaathi.util

import android.content.Context

/**
 * Resolves list view modes with precedence:
 * 1. User selection history for the page (explicit choice on that screen)
 * 2. Page-level default ([ListViewScreen.pageDefault], if any)
 * 3. App-level master (General Settings)
 */
class ListViewPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMasterMode(): ListViewMode =
        ListViewMode.fromStoredOrDefault(prefs.getString(KEY_MASTER, null))

    fun setMasterMode(mode: ListViewMode) {
        prefs.edit().putString(KEY_MASTER, mode.name).apply()
    }

    /** Effective mode for [screen] using the documented precedence. */
    fun getMode(screen: ListViewScreen): ListViewMode {
        getPageOverride(screen)?.let { return it }
        screen.pageDefault?.let { return it }
        return getMasterMode()
    }

    /** Persists a per-page choice (user selection history). */
    fun setMode(screen: ListViewScreen, mode: ListViewMode) {
        prefs.edit().putString(keyFor(screen), mode.name).apply()
    }

    fun getPageOverride(screen: ListViewScreen): ListViewMode? =
        ListViewMode.fromStored(prefs.getString(keyFor(screen), null))

    fun clearPageOverride(screen: ListViewScreen) {
        prefs.edit().remove(keyFor(screen)).apply()
    }

    private fun keyFor(screen: ListViewScreen): String = when (screen) {
        ListViewScreen.CUSTOMERS -> KEY_CUSTOMERS
        ListViewScreen.ITEMS -> KEY_ITEMS
        ListViewScreen.BILLS_HISTORY -> KEY_BILLS_HISTORY
        ListViewScreen.INVENTORY -> KEY_INVENTORY
    }

    companion object {
        private const val PREFS_NAME = "list_view_prefs"
        private const val KEY_MASTER = "app_master"
        private const val KEY_CUSTOMERS = "customers"
        private const val KEY_ITEMS = "items"
        private const val KEY_BILLS_HISTORY = "bills_history"
        private const val KEY_INVENTORY = "inventory"
    }
}
