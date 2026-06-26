package com.kex.vikrsaathi.util

import android.content.Context

class InvoiceBuilderPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var livePreview: Boolean
        get() = prefs.getBoolean(KEY_LIVE_PREVIEW, false)
        set(value) = prefs.edit().putBoolean(KEY_LIVE_PREVIEW, value).apply()

    var snapToGrid: Boolean
        get() = prefs.getBoolean(KEY_SNAP_TO_GRID, true)
        set(value) = prefs.edit().putBoolean(KEY_SNAP_TO_GRID, value).apply()

    var showGrid: Boolean
        get() = prefs.getBoolean(KEY_SHOW_GRID, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_GRID, value).apply()

    var snapToGuides: Boolean
        get() = prefs.getBoolean(KEY_SNAP_TO_GUIDES, true)
        set(value) = prefs.edit().putBoolean(KEY_SNAP_TO_GUIDES, value).apply()

    var showGuides: Boolean
        get() = prefs.getBoolean(KEY_SHOW_GUIDES, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_GUIDES, value).apply()

    companion object {
        private const val PREFS_NAME = "invoice_builder_prefs"
        private const val KEY_LIVE_PREVIEW = "live_preview"
        private const val KEY_SNAP_TO_GRID = "snap_to_grid"
        private const val KEY_SHOW_GRID = "show_grid"
        private const val KEY_SNAP_TO_GUIDES = "snap_to_guides"
        private const val KEY_SHOW_GUIDES = "show_guides"
    }
}
