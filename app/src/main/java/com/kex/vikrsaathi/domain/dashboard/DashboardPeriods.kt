package com.kex.vikrsaathi.domain.dashboard

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class DashboardPeriod {
    TODAY,
    WEEK,
    MONTH
}

data class DateWindow(
    val startInclusive: Long,
    val endExclusive: Long
)

object DashboardPeriods {

    fun current(
        period: DashboardPeriod,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): DateWindow {
        val end = startOfDay(nowMillis, zone).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val start = startOfDay(nowMillis, zone).apply {
            when (period) {
                DashboardPeriod.TODAY -> Unit
                DashboardPeriod.WEEK -> add(Calendar.DAY_OF_MONTH, -6)
                DashboardPeriod.MONTH -> set(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return DateWindow(start.timeInMillis, end.timeInMillis)
    }

    /**
     * The window of the same number of local calendar days immediately before [current].
     */
    fun previous(current: DateWindow, zone: TimeZone = TimeZone.getDefault()): DateWindow {
        val days = calendarDays(current, zone).coerceAtLeast(1)
        val start = Calendar.getInstance(zone).apply {
            timeInMillis = current.startInclusive
            add(Calendar.DAY_OF_MONTH, -days)
        }
        return DateWindow(start.timeInMillis, current.startInclusive)
    }

    fun calendarDays(window: DateWindow, zone: TimeZone = TimeZone.getDefault()): Int {
        val cursor = Calendar.getInstance(zone).apply { timeInMillis = window.startInclusive }
        var days = 0
        while (cursor.timeInMillis < window.endExclusive && days < 400) {
            cursor.add(Calendar.DAY_OF_MONTH, 1)
            days++
        }
        return days
    }

    fun formatRange(
        window: DateWindow,
        zone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        val start = Calendar.getInstance(zone).apply { timeInMillis = window.startInclusive }
        val end = Calendar.getInstance(zone).apply {
            timeInMillis = window.endExclusive
            add(Calendar.MILLISECOND, -1)
        }
        val sameDay = start.get(Calendar.YEAR) == end.get(Calendar.YEAR) &&
            start.get(Calendar.DAY_OF_YEAR) == end.get(Calendar.DAY_OF_YEAR)
        val pattern = if (start.get(Calendar.YEAR) == end.get(Calendar.YEAR)) "d MMM" else "d MMM yyyy"
        val short = java.text.SimpleDateFormat(pattern, locale).apply { timeZone = zone }
        val full = java.text.SimpleDateFormat("d MMM yyyy", locale).apply { timeZone = zone }
        return if (sameDay) {
            full.format(start.time)
        } else {
            "${short.format(start.time)} – ${full.format(end.time)}"
        }
    }

    private fun startOfDay(millis: Long, zone: TimeZone): Calendar {
        return Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
