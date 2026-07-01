package com.kex.vikrsaathi.util

import java.util.Calendar

object DayRange {

    fun todayMillis(): Pair<Long, Long> {
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = startCal.timeInMillis
        val end = startCal.apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        return start to end
    }
}
