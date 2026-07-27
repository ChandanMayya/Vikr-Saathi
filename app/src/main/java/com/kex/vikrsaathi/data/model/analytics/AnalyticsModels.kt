package com.kex.vikrsaathi.data.model.analytics

data class BillTotalRow(
    val billDate: Long,
    val billTotal: Double
)

data class AnalyticsSummary(
    val totalSales: Double,
    val billCount: Int,
    val avgBillValue: Double
) {
    companion object {
        val EMPTY = AnalyticsSummary(0.0, 0, 0.0)
    }
}

data class SalesTrendPoint(
    val dayStartMillis: Long,
    val revenue: Double,
    val billCount: Int
)

data class TopProductRow(
    val itemName: String,
    val totalQuantity: Int,
    val totalRevenue: Double
)

data class TopCustomerRow(
    val customerName: String,
    val billCount: Int,
    val totalSpend: Double
)

data class DiscountImpactSummary(
    val grossAtMrp: Double,
    val netRevenue: Double,
    val discountGiven: Double,
    val avgDiscountPercent: Double
) {
    companion object {
        val EMPTY = DiscountImpactSummary(0.0, 0.0, 0.0, 0.0)
    }
}

data class SlowMoverRow(
    val itemName: String,
    val stockQty: Int,
    val soldQty: Int
)

data class StockValueRow(
    val itemName: String,
    val stockQty: Int,
    val unitPrice: Double,
    val stockValue: Double
)

data class InventoryValueSummary(
    val totalValue: Double,
    val itemCount: Int,
    val topByValue: List<StockValueRow>
) {
    companion object {
        val EMPTY = InventoryValueSummary(0.0, 0, emptyList())
    }
}

data class PeakHourPoint(
    val hour: Int,
    val billCount: Int,
    val revenue: Double
)

data class DayOfWeekPoint(
    val dayOfWeek: Int,
    val billCount: Int,
    val revenue: Double
)

data class CustomerMixSummary(
    val walkInBills: Int,
    val registeredBills: Int,
    val walkInSales: Double,
    val registeredSales: Double
) {
    val totalBills: Int get() = walkInBills + registeredBills
    val totalSales: Double get() = walkInSales + registeredSales

    companion object {
        val EMPTY = CustomerMixSummary(0, 0, 0.0, 0.0)
    }
}

data class HeldBillSummary(
    val heldInPeriod: Int,
    val finalizedInPeriod: Int,
    val activeHeldNow: Int
) {
    val completionRatePercent: Double
        get() {
            val total = heldInPeriod + finalizedInPeriod
            return if (total > 0) finalizedInPeriod * 100.0 / total else 0.0
        }

    companion object {
        val EMPTY = HeldBillSummary(0, 0, 0)
    }
}

data class AnalyticsDashboard(
    val summary: AnalyticsSummary,
    val salesTrend: List<SalesTrendPoint>,
    val topProducts: List<TopProductRow>,
    val topCustomers: List<TopCustomerRow>,
    val discountImpact: DiscountImpactSummary,
    val slowMovers: List<SlowMoverRow>,
    val inventoryValue: InventoryValueSummary,
    val peakHours: List<PeakHourPoint>,
    val salesByDayOfWeek: List<DayOfWeekPoint>,
    val customerMix: CustomerMixSummary,
    val heldBills: HeldBillSummary
) {
    companion object {
        val EMPTY = AnalyticsDashboard(
            summary = AnalyticsSummary.EMPTY,
            salesTrend = emptyList(),
            topProducts = emptyList(),
            topCustomers = emptyList(),
            discountImpact = DiscountImpactSummary.EMPTY,
            slowMovers = emptyList(),
            inventoryValue = InventoryValueSummary.EMPTY,
            peakHours = emptyList(),
            salesByDayOfWeek = emptyList(),
            customerMix = CustomerMixSummary.EMPTY,
            heldBills = HeldBillSummary.EMPTY
        )
    }
}
