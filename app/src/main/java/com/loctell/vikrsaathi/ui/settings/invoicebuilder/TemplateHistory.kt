package com.loctell.vikrsaathi.ui.settings.invoicebuilder

import com.loctell.vikrsaathi.data.model.template.InvoiceTemplate

class TemplateHistory(private val maxSize: Int = 40) {

    private val undoStack = ArrayDeque<InvoiceTemplate>()
    private val redoStack = ArrayDeque<InvoiceTemplate>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(state: InvoiceTemplate) {
        undoStack.addLast(state.copy())
        while (undoStack.size > maxSize) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(current: InvoiceTemplate): InvoiceTemplate? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current.copy())
        return undoStack.removeLast()
    }

    fun redo(current: InvoiceTemplate): InvoiceTemplate? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current.copy())
        return redoStack.removeLast()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
