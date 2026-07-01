package com.kex.vikrsaathi.data.model

data class DashboardTopItem(
    val name: String,
    val quantity: Int
)

data class DashboardTodayStats(
    val totalSales: Double,
    val billCount: Int,
    val topItems: List<DashboardTopItem>
) {
    companion object {
        val EMPTY = DashboardTodayStats(0.0, 0, emptyList())
    }
}
