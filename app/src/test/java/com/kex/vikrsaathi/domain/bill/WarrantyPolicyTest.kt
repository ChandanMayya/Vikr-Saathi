package com.kex.vikrsaathi.domain.bill

import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.domain.inventory.CivilDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarrantyPolicyTest {
    @Test
    fun twelveMonthsFrom12Nov2026EndsOn11Nov2027() {
        val start = CivilDate.of(2026, 11, 12)
        val end = WarrantyPolicy.endEpochDay(start, 12, WarrantyPolicy.MONTHS)
        assertEquals(CivilDate.of(2027, 11, 11), end)
        val snapshot = WarrantyPolicy.Snapshot(
            period = 12,
            unit = WarrantyPolicy.MONTHS,
            provider = WarrantyPolicy.MANUFACTURER,
            startEpochDay = start,
            endEpochDay = end
        )
        assertEquals("Warranty: 12 months", WarrantyPolicy.headline(snapshot))
        assertEquals("Valid until 11 Nov 2027", WarrantyPolicy.validUntil(snapshot))
    }

    @Test
    fun monthAndYearBoundariesUseTheCalendarAndStayInclusive() {
        assertEquals(
            CivilDate.of(2024, 2, 28),
            WarrantyPolicy.endEpochDay(CivilDate.of(2024, 1, 31), 1, WarrantyPolicy.MONTHS)
        )
        assertEquals(
            CivilDate.of(2025, 2, 27),
            WarrantyPolicy.endEpochDay(CivilDate.of(2025, 1, 31), 1, WarrantyPolicy.MONTHS)
        )
        assertEquals(
            CivilDate.of(2025, 2, 27),
            WarrantyPolicy.endEpochDay(CivilDate.of(2024, 2, 29), 1, WarrantyPolicy.YEARS)
        )
        assertEquals(
            CivilDate.of(2026, 11, 12),
            WarrantyPolicy.endEpochDay(CivilDate.of(2026, 11, 12), 1, WarrantyPolicy.DAYS)
        )
    }

    @Test
    fun disabledShopSettingDoesNotCopyItemWarranty() {
        val item = warrantedItem()
        assertNull(WarrantyPolicy.forNewSale(item, shopEnabled = false))
        val captured = WarrantyPolicy.forNewSale(item, shopEnabled = true)
        assertEquals(12, captured?.period)
        assertEquals(WarrantyPolicy.MONTHS, captured?.unit)
        assertNull(captured?.endEpochDay)
    }

    @Test
    fun itemWithoutWarrantyProducesNothing() {
        val item = Item(name = "Pen", mrp = 10.0)
        assertNull(WarrantyPolicy.termsFrom(item))
        assertEquals("Pen", WarrantyPolicy.printText("Pen", null))
        assertEquals(WarrantyPolicy.NO_RECORD, "No warranty recorded")
    }

    @Test
    fun periodRejectsZeroAndNegativeValues() {
        assertNull(WarrantyPolicy.parsePeriod("0"))
        assertNull(WarrantyPolicy.parsePeriod("-3"))
        assertNull(WarrantyPolicy.parsePeriod("12.5"))
        assertEquals(12, WarrantyPolicy.parsePeriod("12"))
    }

    @Test
    fun datedSnapshotIsNotReplaced() {
        val original = WarrantyPolicy.Snapshot(
            6, WarrantyPolicy.MONTHS, WarrantyPolicy.SHOP,
            startEpochDay = 10L, endEpochDay = 20L
        )
        val resolved = WarrantyPolicy.resolve(original, saleMillis = 1_700_000_000_000L)
        assertEquals(10L, resolved?.startEpochDay)
        assertEquals(20L, resolved?.endEpochDay)
    }

    private fun warrantedItem() = Item(
        name = "Mixer",
        mrp = 4500.0,
        warrantyApplies = true,
        warrantyPeriod = 12,
        warrantyUnit = WarrantyPolicy.MONTHS,
        warrantyProvider = WarrantyPolicy.MANUFACTURER
    )
}
