package com.kex.vikrsaathi.domain.bill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BillVersionDifferTest {

    private val tea = BillVersionDiffer.LineSnap(
        itemId = 1,
        name = "Tea",
        quantity = 2,
        mrp = 20.0,
        discount = 5.0,
        finalPrice = 19.0,
        roundOff = 0.0
    )

    @Test
    fun quantityAndDiscountSummariesStayConcise() {
        val before = BillVersionDiffer.State(
            customerName = "Walk-in customer",
            lines = listOf(tea)
        )
        val after = before.copy(
            lines = listOf(tea.copy(quantity = 3, discount = 10.0, finalPrice = 18.0))
        )

        val summary = BillVersionDiffer.summarize(before, after, "₹")

        assertEquals(
            "Quantity changed: Tea 2 → 3; Discount changed: Tea 5% → 10%",
            summary
        )
    }

    @Test
    fun extraChangesAreTruncatedWithoutDroppingTheFirstOnes() {
        val before = BillVersionDiffer.State(
            customerName = "Asha",
            customerPhone = "1",
            roundOff = 0.0,
            lines = listOf(tea),
            payments = listOf(BillVersionDiffer.PaymentSnap("CASH", 1000))
        )
        val after = before.copy(
            customerName = "Ravi",
            customerPhone = "2",
            roundOff = 1.0,
            lines = listOf(tea.copy(quantity = 4)),
            payments = listOf(BillVersionDiffer.PaymentSnap("UPI", 1000))
        )

        val summary = BillVersionDiffer.summarize(before, after, "₹")

        assertEquals(
            "Customer changed: Asha → Ravi; Quantity changed: Tea 2 → 4; Bill round-off changed: ₹0.00 → ₹1.00; +1 more",
            summary
        )
        assertFalse(summary.contains("Payment changed"))
    }

    @Test
    fun identicalSnapshotsDoNotInventAChange() {
        val state = BillVersionDiffer.State(customerName = "Asha", lines = listOf(tea))
        assertEquals("Details updated", BillVersionDiffer.summarize(state, state, "₹"))
    }
}
