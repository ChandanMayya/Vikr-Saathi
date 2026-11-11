package com.kex.vikrsaathi.data.model

import com.kex.vikrsaathi.domain.dashboard.DashboardPeriod
import com.kex.vikrsaathi.domain.dashboard.DateWindow
import com.kex.vikrsaathi.domain.dashboard.GrossProfitCalculator
import com.kex.vikrsaathi.domain.dashboard.PeriodComparison

data class DashboardPeriodStats(
    val period: DashboardPeriod,
    val window: DateWindow,
    val previousWindow: DateWindow,
    val totalSales: Double,
    val billCount: Int,
    val comparison: PeriodComparison,
    val topItems: List<DashboardTopItem>,
    val profit: GrossProfitCalculator.Result
)
