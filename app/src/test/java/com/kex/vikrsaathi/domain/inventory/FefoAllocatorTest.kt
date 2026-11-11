package com.kex.vikrsaathi.domain.inventory

import org.junit.Assert.assertEquals
import org.junit.Test

class FefoAllocatorTest {
    private val today = 20_000L

    @Test
    fun earliestExpiryIsUsedFirstAndUnknownComesLast() {
        val plan = FefoAllocator.plan(
            lots = listOf(
                lot(1, expiry = today + 10, qty = 2),
                lot(2, expiry = null, qty = 5, state = BatchState.UNKNOWN),
                lot(3, expiry = today + 1, qty = 3)
            ),
            needed = 4,
            today = today
        )
        assertEquals(listOf(3L to 3, 1L to 1), plan.taken.map { it.lotId to it.quantity })
        assertEquals(0, plan.shortfall)
        assertEquals(10, plan.eligible)
    }

    @Test
    fun expiredAndQuarantineAreNotSold() {
        val plan = FefoAllocator.plan(
            lots = listOf(
                lot(1, expiry = today - 1, qty = 4),
                lot(2, expiry = null, qty = 2, state = BatchState.QUARANTINE),
                lot(3, expiry = today, qty = 1)
            ),
            needed = 3,
            today = today
        )
        assertEquals(listOf(3L to 1), plan.taken.map { it.lotId to it.quantity })
        assertEquals(2, plan.shortfall)
        assertEquals(1, plan.eligible)
        assertEquals(4, plan.expired)
    }

    @Test
    fun alertWindowIncludesTodayAndTheLastDay() {
        assertEquals(true, CivilDate.inAlertWindow(10, 10, 1))
        assertEquals(false, CivilDate.inAlertWindow(11, 10, 1))
        assertEquals(true, CivilDate.inAlertWindow(39, 10, 30))
        assertEquals(false, CivilDate.inAlertWindow(40, 10, 30))
    }

    private fun lot(id: Long, expiry: Long?, qty: Int, state: String = BatchState.ACTIVE) =
        FefoAllocator.Lot(id, expiry, qty, state, createdAt = id)
}
