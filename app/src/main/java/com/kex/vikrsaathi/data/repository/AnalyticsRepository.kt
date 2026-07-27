package com.kex.vikrsaathi.data.repository

import com.kex.vikrsaathi.data.dao.BillDao
import com.kex.vikrsaathi.data.dao.BillDraftDao
import com.kex.vikrsaathi.data.dao.ItemDao
import com.kex.vikrsaathi.data.model.analytics.AnalyticsDashboard
import com.kex.vikrsaathi.data.model.analytics.AnalyticsSummary
import com.kex.vikrsaathi.data.model.analytics.BillTotalRow
import com.kex.vikrsaathi.data.model.analytics.CustomerMixSummary
import com.kex.vikrsaathi.data.model.analytics.DayOfWeekPoint
import com.kex.vikrsaathi.data.model.analytics.DiscountImpactSummary
import com.kex.vikrsaathi.data.model.analytics.HeldBillSummary
import com.kex.vikrsaathi.data.model.analytics.InventoryValueSummary
import com.kex.vikrsaathi.data.model.analytics.PeakHourPoint
import com.kex.vikrsaathi.data.model.analytics.SalesTrendPoint
import com.kex.vikrsaathi.util.AnalyticsDateRange
import com.kex.vikrsaathi.util.DayRange
import java.util.Calendar

class AnalyticsRepository(
    private val billDao: BillDao,
    private val itemDao: ItemDao,
    private val billDraftDao: BillDraftDao
) {

    suspend fun loadDashboard(range: AnalyticsDateRange): AnalyticsDashboard {
        val (start, end) = DayRange.analyticsRange(range)
        val totalSales = billDao.getTotalSalesBetween(start, end)
        val billCount = billDao.getBillCountBetween(start, end)
        val avgBillValue = if (billCount > 0) totalSales / billCount else 0.0
        val billTotals = billDao.getBillTotalsBetween(start, end)
        val discountTotals = billDao.getDiscountTotalsBetween(start, end)
        val grossAtMrp = discountTotals.grossAtMrp
        val netRevenue = discountTotals.netRevenue
        val discountGiven = (grossAtMrp - netRevenue).coerceAtLeast(0.0)
        val avgDiscountPercent = if (grossAtMrp > 0) discountGiven / grossAtMrp * 100.0 else 0.0
        val topStock = itemDao.getTopStockValueItems()
        val customerMixRow = billDao.getCustomerMixBetween(start, end)
        val heldInPeriod = billDraftDao.countHeldBetween(start, end)
        return AnalyticsDashboard(
            summary = AnalyticsSummary(
                totalSales = totalSales,
                billCount = billCount,
                avgBillValue = avgBillValue
            ),
            salesTrend = buildSalesTrend(billTotals, start, end),
            topProducts = billDao.getTopProductsByRevenueBetween(start, end),
            topCustomers = billDao.getTopCustomersBetween(start, end),
            discountImpact = DiscountImpactSummary(
                grossAtMrp = grossAtMrp,
                netRevenue = netRevenue,
                discountGiven = discountGiven,
                avgDiscountPercent = avgDiscountPercent
            ),
            slowMovers = itemDao.getSlowMovers(start, end),
            inventoryValue = InventoryValueSummary(
                totalValue = itemDao.getTotalInventoryValue(),
                itemCount = itemDao.countInStockItems(),
                topByValue = topStock
            ),
            peakHours = buildPeakHours(billTotals),
            salesByDayOfWeek = buildDayOfWeek(billTotals),
            customerMix = CustomerMixSummary(
                walkInBills = customerMixRow.walkInBills,
                registeredBills = customerMixRow.registeredBills,
                walkInSales = customerMixRow.walkInSales,
                registeredSales = customerMixRow.registeredSales
            ),
            heldBills = HeldBillSummary(
                heldInPeriod = heldInPeriod,
                finalizedInPeriod = billCount,
                activeHeldNow = billDraftDao.countAllHeld()
            )
        )
    }

    private fun buildPeakHours(rows: List<BillTotalRow>): List<PeakHourPoint> {
        val counts = IntArray(24)
        val revenue = DoubleArray(24)
        val calendar = Calendar.getInstance()
        rows.forEach { row ->
            calendar.timeInMillis = row.billDate
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            counts[hour]++
            revenue[hour] += row.billTotal
        }
        return (0..23).map { hour ->
            PeakHourPoint(hour, counts[hour], revenue[hour])
        }
    }

    private fun buildDayOfWeek(rows: List<BillTotalRow>): List<DayOfWeekPoint> {
        val counts = mutableMapOf<Int, Int>()
        val revenue = mutableMapOf<Int, Double>()
        val calendar = Calendar.getInstance()
        rows.forEach { row ->
            calendar.timeInMillis = row.billDate
            val day = calendar.get(Calendar.DAY_OF_WEEK)
            counts[day] = (counts[day] ?: 0) + 1
            revenue[day] = (revenue[day] ?: 0.0) + row.billTotal
        }
        return DAY_OF_WEEK_ORDER.map { day ->
            DayOfWeekPoint(
                dayOfWeek = day,
                billCount = counts[day] ?: 0,
                revenue = revenue[day] ?: 0.0
            )
        }
    }

    private fun buildSalesTrend(
        rows: List<BillTotalRow>,
        startInclusive: Long,
        endExclusive: Long
    ): List<SalesTrendPoint> {
        val grouped = rows.groupBy { dayStartMillis(it.billDate) }
            .mapValues { (day, bills) ->
                SalesTrendPoint(
                    dayStartMillis = day,
                    revenue = bills.sumOf { it.billTotal },
                    billCount = bills.size
                )
            }

        val points = mutableListOf<SalesTrendPoint>()
        val cursor = Calendar.getInstance().apply { timeInMillis = startInclusive }
        val endCal = Calendar.getInstance().apply { timeInMillis = endExclusive }
        while (cursor.before(endCal)) {
            val dayStart = cursor.timeInMillis
            points += grouped[dayStart] ?: SalesTrendPoint(dayStart, 0.0, 0)
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        return points
    }

    private fun dayStartMillis(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        private val DAY_OF_WEEK_ORDER = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
    }
}
