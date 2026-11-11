package com.kex.vikrsaathi.domain.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DashboardPeriodTest {

    private val zone = TimeZone.getTimeZone("Asia/Kolkata")

    @Test
    fun todayWeekAndMonthUseLocalBoundariesAndEqualPreviousWindows() {
        val now = calendar(2026, Calendar.NOVEMBER, 11, 15, 30).timeInMillis
        val today = DashboardPeriods.current(DashboardPeriod.TODAY, now, zone)
        val week = DashboardPeriods.current(DashboardPeriod.WEEK, now, zone)
        val month = DashboardPeriods.current(DashboardPeriod.MONTH, now, zone)

        assertEquals(calendar(2026, Calendar.NOVEMBER, 11, 0, 0).timeInMillis, today.startInclusive)
        assertEquals(calendar(2026, Calendar.NOVEMBER, 12, 0, 0).timeInMillis, today.endExclusive)
        assertEquals(calendar(2026, Calendar.NOVEMBER, 5, 0, 0).timeInMillis, week.startInclusive)
        assertEquals(calendar(2026, Calendar.NOVEMBER, 1, 0, 0).timeInMillis, month.startInclusive)

        listOf(today, week, month).forEach { window ->
            val previous = DashboardPeriods.previous(window, zone)
            assertEquals(window.startInclusive, previous.endExclusive)
            assertEquals(
                DashboardPeriods.calendarDays(window, zone),
                DashboardPeriods.calendarDays(previous, zone)
            )
        }
    }

    @Test
    fun zeroPriorSalesDoesNotProduceAPercentage() {
        val none = PeriodComparison.of(0.0, 0.0, 0, 0)
        assertNull(none.salesPercent)
        val firstSales = PeriodComparison.of(120.0, 0.0, 2, 0)
        assertNull(firstSales.salesPercent)
        assertEquals(120.0, firstSales.salesDelta, 0.001)
        val compared = PeriodComparison.of(110.0, 100.0, 4, 3)
        assertEquals(10.0, compared.salesPercent!!, 0.001)
    }

    @Test
    fun grossProfitUsesCapturedCostAndHidesATotalWhenCoverageIsMissing() {
        val complete = GrossProfitCalculator.calculate(
            sales = listOf(
                GrossProfitCalculator.SaleSlice(revenuePaise = 1800, quantity = 2, unitCostPaise = 500)
            ),
            billRoundOffPaise = 50,
            returns = listOf(
                GrossProfitCalculator.ReturnSlice(
                    refundPaise = 900,
                    lines = listOf(GrossProfitCalculator.SaleSlice(0, 1, 500))
                )
            )
        )
        assertEquals(GrossProfitCalculator.Coverage.COMPLETE, complete.coverage)
        assertEquals(950L, complete.revenuePaise)
        assertEquals(500L, complete.costPaise)
        assertEquals(450L, complete.profitPaise)

        val missing = GrossProfitCalculator.calculate(
            sales = listOf(GrossProfitCalculator.SaleSlice(2000, 2, null)),
            billRoundOffPaise = 0
        )
        assertEquals(GrossProfitCalculator.Coverage.INCOMPLETE, missing.coverage)
        assertNull(missing.profitPaise)
        assertNull(missing.costPaise)
        assertEquals(2, missing.uncoveredUnits)
    }

    @Test
    fun refundWithoutACostedLineIsIncomplete() {
        val result = GrossProfitCalculator.calculate(
            sales = listOf(GrossProfitCalculator.SaleSlice(1000, 1, 400)),
            billRoundOffPaise = 0,
            returns = listOf(GrossProfitCalculator.ReturnSlice(refundPaise = 1000))
        )
        assertEquals(GrossProfitCalculator.Coverage.INCOMPLETE, result.coverage)
        assertNull(result.profitPaise)
    }

    private fun calendar(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar {
        return Calendar.getInstance(zone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
