package com.kex.vikrsaathi.util

import java.util.Calendar

object DayRange {

    fun todayMillis(): Pair<Long, Long> = analyticsRange(AnalyticsDateRange.TODAY)

    fun analyticsRange(range: AnalyticsDateRange): Pair<Long, Long> {
        val endExclusive = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }
        val startInclusive = Calendar.getInstance().apply {
            when (range) {
                AnalyticsDateRange.TODAY -> {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                AnalyticsDateRange.LAST_7_DAYS -> {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_MONTH, -6)
                }
                AnalyticsDateRange.LAST_30_DAYS -> {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_MONTH, -29)
                }
                AnalyticsDateRange.THIS_MONTH -> {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
        }
        return startInclusive.timeInMillis to endExclusive.timeInMillis
    }
}
