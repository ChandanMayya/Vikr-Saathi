package com.kex.vikrsaathi.domain.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockDeltaCalculatorTest {

    @Test
    fun aggregateQuantities_sumsByItemId() {
        val result = StockDeltaCalculator.aggregateQuantities(
            listOf(
                1L to 2,
                2L to 1,
                1L to 3,
                null to 5,
                0L to 4
            )
        )
        assertEquals(mapOf(1L to 5, 2L to 1), result)
    }

    @Test
    fun netStockDeltas_newSaleReducesStock() {
        val deltas = StockDeltaCalculator.netStockDeltas(
            oldQuantities = emptyMap(),
            newQuantities = mapOf(10L to 3)
        )
        assertEquals(mapOf(10L to -3), deltas)
    }

    @Test
    fun netStockDeltas_editAppliesNetOnly() {
        val deltas = StockDeltaCalculator.netStockDeltas(
            oldQuantities = mapOf(1L to 5, 2L to 2),
            newQuantities = mapOf(1L to 7, 3L to 1)
        )
        assertEquals(
            mapOf(
                1L to -2, // sold 2 more
                2L to 2,  // fully restored
                3L to -1  // new line
            ),
            deltas
        )
    }

    @Test
    fun netStockDeltas_unchangedReturnsEmpty() {
        val deltas = StockDeltaCalculator.netStockDeltas(
            oldQuantities = mapOf(1L to 4),
            newQuantities = mapOf(1L to 4)
        )
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun saleAndReversalHelpers() {
        assertEquals(mapOf(1L to -2), StockDeltaCalculator.saleDeltas(mapOf(1L to 2)))
        assertEquals(mapOf(1L to 2), StockDeltaCalculator.reversalDeltas(mapOf(1L to 2)))
    }
}
