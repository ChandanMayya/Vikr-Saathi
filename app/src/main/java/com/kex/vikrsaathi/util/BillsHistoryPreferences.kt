package com.kex.vikrsaathi.util

import android.content.Context

class BillsHistoryPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var searchFilterEnabled: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_FILTER, false)
        set(value) = prefs.edit().putBoolean(KEY_SEARCH_FILTER, value).apply()

    var dateRangeFilterEnabled: Boolean
        get() = prefs.getBoolean(KEY_DATE_RANGE_FILTER, false)
        set(value) = prefs.edit().putBoolean(KEY_DATE_RANGE_FILTER, value).apply()

    var counterRangeFilterEnabled: Boolean
        get() = prefs.getBoolean(KEY_COUNTER_RANGE_FILTER, false)
        set(value) = prefs.edit().putBoolean(KEY_COUNTER_RANGE_FILTER, value).apply()

    companion object {
        private const val PREFS_NAME = "bills_history_prefs"
        private const val KEY_SEARCH_FILTER = "search_filter"
        private const val KEY_DATE_RANGE_FILTER = "date_range_filter"
        private const val KEY_COUNTER_RANGE_FILTER = "counter_range_filter"
    }
}
