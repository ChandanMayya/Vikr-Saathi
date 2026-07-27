package com.kex.vikrsaathi.util

enum class AnalyticsDateRange {
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_MONTH;

    fun labelRes(): Int = when (this) {
        TODAY -> com.kex.vikrsaathi.R.string.analysis_range_today
        LAST_7_DAYS -> com.kex.vikrsaathi.R.string.analysis_range_7d
        LAST_30_DAYS -> com.kex.vikrsaathi.R.string.analysis_range_30d
        THIS_MONTH -> com.kex.vikrsaathi.R.string.analysis_range_month
    }
}
