package com.kex.vikrsaathi.domain.dashboard

data class PeriodComparison(
    val currentSales: Double,
    val previousSales: Double,
    val currentBills: Int,
    val previousBills: Int
) {
    val salesDelta: Double = currentSales - previousSales

    /** Null when the previous period had no sales, so a percentage would divide by zero. */
    val salesPercent: Double? =
        if (previousSales == 0.0) null else (salesDelta / previousSales) * 100.0

    companion object {
        fun of(
            currentSales: Double,
            previousSales: Double,
            currentBills: Int,
            previousBills: Int
        ) = PeriodComparison(currentSales, previousSales, currentBills, previousBills)
    }
}
