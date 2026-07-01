package com.kex.vikrsaathi.ui.settings

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class SettingsAutoSave(
    private val onSave: () -> Unit,
    private val delayMs: Long = 400L
) {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    var suppress = false

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = schedule()
    }

    fun attach(vararg fields: EditText) {
        fields.forEach { it.addTextChangedListener(watcher) }
    }

    fun schedule() {
        if (suppress) return
        runnable?.let { handler.removeCallbacks(it) }
        runnable = Runnable { onSave() }
        handler.postDelayed(runnable!!, delayMs)
    }

    fun flush() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
        if (!suppress) onSave()
    }

    fun clear() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }
}
