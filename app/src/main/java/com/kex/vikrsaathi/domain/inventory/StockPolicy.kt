package com.kex.vikrsaathi.domain.inventory

import com.kex.vikrsaathi.util.InventoryMode

/**
 * Decides whether a bill save must stop because stock is short.
 * Off never blocks. Block always blocks. Warn blocks until the user continues.
 */
object StockPolicy {

    fun blocksSave(
        mode: InventoryMode,
        hasShortfall: Boolean,
        shortageAcknowledged: Boolean
    ): Boolean {
        if (!hasShortfall) return false
        return when (mode) {
            InventoryMode.OFF -> false
            InventoryMode.BLOCK -> true
            InventoryMode.WARN -> !shortageAcknowledged
        }
    }
}
