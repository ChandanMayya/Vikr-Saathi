package com.kex.vikrsaathi.data.model.analytics

import com.kex.vikrsaathi.data.entity.SalesReturn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SalesTrendSeriesTest {

    private val zone = TimeZone.getTimeZone("Asia/Kolkata")

    @Test
    fun fillsEachDayAndSubtractsThatDaysReturn() {
        val start = day(2026, Calendar.NOVEMBER, 10)
        val end = day(2026, Calendar.NOVEMBER, 12)
        val points = SalesTrendSeries.build(
            rows = listOf(BillTotalRow(start + 3_600_000L, 100.0)),
            startInclusive = start,
            endExclusive = end,
            returnsInRange = listOf(
                SalesReturn(reason = "Return", occurredAt = start + 4_000_000L, returnValuePaise = 2_500L)
            ),
            zone = zone
        )
        assertEquals(2, points.size)
        assertEquals(75.0, points[0].revenue, 0.001)
        assertEquals(1, points[0].billCount)
        assertEquals(0.0, points[1].revenue, 0.001)
        assertEquals(0, points[1].billCount)
    }

    @Test
    fun emptyWindowStaysEmpty() {
        val start = day(2026, Calendar.NOVEMBER, 10)
        assertTrue(SalesTrendSeries.build(emptyList(), start, start, zone = zone).isEmpty())
    }

    private fun day(year: Int, month: Int, date: Int): Long {
        return Calendar.getInstance(zone).apply {
            set(year, month, date, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
