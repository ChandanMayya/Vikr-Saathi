package com.kex.vikrsaathi.data.model.analytics

import com.kex.vikrsaathi.data.entity.SalesReturn
import com.kex.vikrsaathi.domain.finance.ReturnReporting
import java.util.Calendar
import java.util.TimeZone

/**
 * Daily net sales for a window. Each local day in the window is one point, including days
 * with no bills. Returns reduce that day's sales the same way the period total does.
 */
object SalesTrendSeries {

    fun build(
        rows: List<BillTotalRow>,
        startInclusive: Long,
        endExclusive: Long,
        returnsInRange: List<SalesReturn> = emptyList(),
        zone: TimeZone = TimeZone.getDefault()
    ): List<SalesTrendPoint> {
        if (endExclusive <= startInclusive) return emptyList()
        val returnByDay = returnsInRange.groupBy { dayStartMillis(it.occurredAt, zone) }.mapValues { (_, entries) ->
            entries.sumOf {
                ReturnReporting.merchandisePaise(
                    it.returnValuePaise,
                    it.moneyRefundedPaise,
                    it.creditAdjustmentPaise
                )
            }
        }
        val grouped = rows.groupBy { dayStartMillis(it.billDate, zone) }
            .mapValues { (day, bills) ->
                SalesTrendPoint(
                    dayStartMillis = day,
                    revenue = ReturnReporting.netSalesRupees(
                        bills.sumOf { it.billTotal },
                        returnByDay[day] ?: 0L
                    ),
                    billCount = bills.size
                )
            }

        val points = mutableListOf<SalesTrendPoint>()
        val cursor = Calendar.getInstance(zone).apply { timeInMillis = startInclusive }
        val endCal = Calendar.getInstance(zone).apply { timeInMillis = endExclusive }
        var guard = 0
        while (cursor.before(endCal) && guard < 400) {
            val dayStart = dayStartMillis(cursor.timeInMillis, zone)
            points += grouped[dayStart] ?: SalesTrendPoint(
                dayStart,
                ReturnReporting.netSalesRupees(0.0, returnByDay[dayStart] ?: 0L),
                0
            )
            cursor.add(Calendar.DAY_OF_MONTH, 1)
            guard++
        }
        return points
    }

    private fun dayStartMillis(timestamp: Long, zone: TimeZone): Long {
        return Calendar.getInstance(zone).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
