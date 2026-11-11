package com.kex.vikrsaathi.domain.inventory

import java.util.Calendar
import java.util.TimeZone

/**
 * A calendar date stored as days since 1970-01-01 UTC.
 * The number is the date the operator picked, not a clock time, so it does not shift with the timezone.
 */
object CivilDate {
    private const val DAY_MS = 86_400_000L

    fun parse(text: String): Long? {
        val parts = text.trim().split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (year < 1970 || month !in 1..12 || day !in 1..31) return null
        return of(year, month, day)
    }

    fun of(year: Int, month: Int, day: Int): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.clear()
        utc.set(year, month - 1, day, 0, 0, 0)
        utc.set(Calendar.MILLISECOND, 0)
        return utc.timeInMillis / DAY_MS
    }

    fun today(nowMillis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Long {
        val local = Calendar.getInstance(zone)
        local.timeInMillis = nowMillis
        return of(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH) + 1,
            local.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun format(epochDay: Long): String {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = epochDay * DAY_MS
        val day = utc.get(Calendar.DAY_OF_MONTH)
        val month = utc.getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.getDefault())
        val year = utc.get(Calendar.YEAR)
        return "$day $month $year"
    }

    /** Inclusive window: today and the following [windowDays] - 1 dates. */
    fun inAlertWindow(expiryEpochDay: Long, today: Long, windowDays: Int): Boolean {
        if (windowDays <= 0) return expiryEpochDay == today
        return expiryEpochDay in today..(today + windowDays - 1)
    }
}
