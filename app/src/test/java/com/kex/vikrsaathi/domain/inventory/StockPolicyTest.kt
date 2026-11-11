package com.kex.vikrsaathi.domain.inventory

import com.kex.vikrsaathi.util.InventoryMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockPolicyTest {

    @Test
    fun offNeverBlocksEvenWhenStockIsShort() {
        assertFalse(StockPolicy.blocksSave(InventoryMode.OFF, hasShortfall = true, shortageAcknowledged = false))
    }

    @Test
    fun blockAlwaysRefusesAShortage() {
        assertTrue(StockPolicy.blocksSave(InventoryMode.BLOCK, hasShortfall = true, shortageAcknowledged = false))
        assertTrue(StockPolicy.blocksSave(InventoryMode.BLOCK, hasShortfall = true, shortageAcknowledged = true))
    }

    @Test
    fun warnRequiresAnExplicitContinue() {
        assertTrue(StockPolicy.blocksSave(InventoryMode.WARN, hasShortfall = true, shortageAcknowledged = false))
        assertFalse(StockPolicy.blocksSave(InventoryMode.WARN, hasShortfall = true, shortageAcknowledged = true))
    }

    @Test
    fun enoughStockNeverBlocks() {
        InventoryMode.entries.forEach { mode ->
            assertFalse(StockPolicy.blocksSave(mode, hasShortfall = false, shortageAcknowledged = false))
        }
    }
}
