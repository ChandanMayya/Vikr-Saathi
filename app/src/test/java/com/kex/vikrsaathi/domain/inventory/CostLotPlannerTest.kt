package com.kex.vikrsaathi.domain.inventory

import org.junit.Assert.assertEquals
import org.junit.Test

class CostLotPlannerTest {

    @Test
    fun editReplacesOnlyUnsoldQuantity() {
        val existing = listOf(
            CostLotPlanner.ExistingLot(sourceId = 4, itemId = 1, qtyReceived = 10, qtyRemaining = 6)
        )
        val plan = CostLotPlanner.replacePurchase(
            existing,
            listOf(CostLotPlanner.Incoming(sourceId = 9, itemId = 1, baseQty = 8, unitCostPaise = 250))
        )
        assertEquals(listOf(4L), plan.deleteSourceIds)
        assertEquals(1, plan.inserts.size)
        assertEquals(8, plan.inserts[0].qtyReceived)
        assertEquals(4, plan.inserts[0].qtyRemaining)
        assertEquals(250L, plan.inserts[0].unitCostPaise)
    }

    @Test
    fun packRateBecomesCostPerBaseUnit() {
        assertEquals(6, CostLotPlanner.baseQuantity(3, 2))
        assertEquals(250L, CostLotPlanner.unitCostPerBase(500, 2))
    }
}
