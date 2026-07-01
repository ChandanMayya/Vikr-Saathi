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

    var sortByDateAsc: Boolean
        get() = prefs.getBoolean(KEY_SORT_DATE_ASC, false)
        set(value) = prefs.edit().putBoolean(KEY_SORT_DATE_ASC, value).apply()

    var sortByInvoiceNumberAsc: Boolean
        get() = prefs.getBoolean(KEY_SORT_INVOICE_ASC, false)
        set(value) = prefs.edit().putBoolean(KEY_SORT_INVOICE_ASC, value).apply()

    companion object {
        private const val PREFS_NAME = "bills_history_prefs"
        private const val KEY_SEARCH_FILTER = "search_filter"
        private const val KEY_DATE_RANGE_FILTER = "date_range_filter"
        private const val KEY_COUNTER_RANGE_FILTER = "counter_range_filter"
        private const val KEY_SORT_DATE_ASC = "sort_date_asc"
        private const val KEY_SORT_INVOICE_ASC = "sort_invoice_asc"
    }
}
