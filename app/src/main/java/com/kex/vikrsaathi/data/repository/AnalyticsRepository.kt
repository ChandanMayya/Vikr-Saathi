package com.kex.vikrsaathi.data.repository

import com.kex.vikrsaathi.data.dao.BillDao
import com.kex.vikrsaathi.data.dao.BillDraftDao
import com.kex.vikrsaathi.data.dao.CustomerLedgerDao
import com.kex.vikrsaathi.data.dao.ItemDao
import com.kex.vikrsaathi.data.entity.VendorLedgerEntry
import com.kex.vikrsaathi.data.model.analytics.AnalyticsDashboard
import com.kex.vikrsaathi.data.model.analytics.AnalyticsSummary
import com.kex.vikrsaathi.data.model.analytics.BillTotalRow
import com.kex.vikrsaathi.data.model.analytics.CustomerMixSummary
import com.kex.vikrsaathi.data.model.analytics.DayOfWeekPoint
import com.kex.vikrsaathi.data.model.analytics.DiscountImpactSummary
import com.kex.vikrsaathi.data.model.analytics.HeldBillSummary
import com.kex.vikrsaathi.data.model.analytics.InventoryValueSummary
import com.kex.vikrsaathi.data.model.analytics.PeakHourPoint
import com.kex.vikrsaathi.data.model.analytics.SalesTrendPoint
import com.kex.vikrsaathi.data.model.analytics.VendorAnalytics
import com.kex.vikrsaathi.domain.finance.LedgerRow
import com.kex.vikrsaathi.domain.finance.PackMath
import com.kex.vikrsaathi.domain.finance.ReturnReporting
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.util.AnalyticsDateRange
import com.kex.vikrsaathi.util.DayRange
import java.util.Calendar

class AnalyticsRepository(
    private val billDao: BillDao,
    private val itemDao: ItemDao,
    private val billDraftDao: BillDraftDao,
    private val vendorRepository: VendorRepository,
    private val customerLedgerDao: CustomerLedgerDao? = null
) {

    suspend fun loadDashboard(
        range: AnalyticsDateRange,
        vendorManagementEnabled: Boolean = false
    ): AnalyticsDashboard {
        val (start, end) = DayRange.analyticsRange(range)
        val grossSales = billDao.getTotalSalesBetween(start, end)
        val returnsInRange = customerLedgerDao?.getAllReturns().orEmpty()
            .filter { it.voidedAt == null && it.voidsReturnId == null && it.occurredAt >= start && it.occurredAt < end }
        val returnPaise = returnsInRange.sumOf {
            ReturnReporting.merchandisePaise(it.returnValuePaise, it.moneyRefundedPaise, it.creditAdjustmentPaise)
        }
        val totalSales = ReturnReporting.netSalesRupees(grossSales, returnPaise)
        val billCount = billDao.getBillCountBetween(start, end)
        val avgBillValue = if (billCount > 0) totalSales / billCount else 0.0
        val billTotals = billDao.getBillTotalsBetween(start, end)
        val discountTotals = billDao.getDiscountTotalsBetween(start, end)
        val grossAtMrp = discountTotals.grossAtMrp
        val netRevenue = discountTotals.netRevenue
        val discountGiven = (grossAtMrp - netRevenue).coerceAtLeast(0.0)
        val avgDiscountPercent = if (grossAtMrp > 0) discountGiven / grossAtMrp * 100.0 else 0.0
        val topStock = itemDao.getTopStockValueItems()
        val customerMixRow = billDao.getCustomerMixBetween(start, end)
        val heldInPeriod = billDraftDao.countHeldBetween(start, end)
        return AnalyticsDashboard(
            summary = AnalyticsSummary(
                totalSales = totalSales,
                billCount = billCount,
                avgBillValue = avgBillValue
            ),
            salesTrend = buildSalesTrend(billTotals, start, end, returnsInRange),
            topProducts = netTopProducts(
                billDao.getTopProductsByRevenueBetween(start, end),
                returnsInRange
            ),
            topCustomers = billDao.getTopCustomersBetween(start, end),
            discountImpact = DiscountImpactSummary(
                grossAtMrp = grossAtMrp,
                netRevenue = netRevenue,
                discountGiven = discountGiven,
                avgDiscountPercent = avgDiscountPercent
            ),
            slowMovers = itemDao.getSlowMovers(start, end),
            inventoryValue = InventoryValueSummary(
                totalValue = itemDao.getTotalInventoryValue(),
                itemCount = itemDao.countInStockItems(),
                topByValue = topStock
            ),
            peakHours = buildPeakHours(billTotals),
            salesByDayOfWeek = buildDayOfWeek(billTotals),
            customerMix = CustomerMixSummary(
                walkInBills = customerMixRow.walkInBills,
                registeredBills = customerMixRow.registeredBills,
                walkInSales = customerMixRow.walkInSales,
                registeredSales = customerMixRow.registeredSales
            ),
            heldBills = HeldBillSummary(
                heldInPeriod = heldInPeriod,
                finalizedInPeriod = billCount,
                activeHeldNow = billDraftDao.countAllHeld()
            ),
            vendor = if (vendorManagementEnabled) loadVendor(start, end) else VendorAnalytics.DISABLED
        )
    }

    private suspend fun loadVendor(startInclusive: Long, endExclusive: Long): VendorAnalytics {
        val names = vendorRepository.getAll().associate { it.id to it.name }
        val receiptsById = vendorRepository.receipts().associateBy { it.id }
        val receiptLines = vendorRepository.allReceiptLines().mapNotNull { line ->
            val receipt = receiptsById[line.receiptId] ?: return@mapNotNull null
            VendorAnalyticsMath.ReceiptQty(
                vendorId = receipt.vendorId,
                receivedAt = receipt.receivedAt,
                receivedQty = PackMath.toBase(line.receivedQty, line.unitsPerBundle) ?: 0,
                acceptedQty = PackMath.toBase(line.acceptedQty, line.unitsPerBundle) ?: 0,
                damagedQty = PackMath.toBase(line.damagedQty, line.unitsPerBundle) ?: 0,
                rejectedQty = PackMath.toBase(line.rejectedQty, line.unitsPerBundle) ?: 0
            )
        }
        return VendorAnalyticsMath.compute(
            startInclusive = startInclusive,
            endExclusive = endExclusive,
            invoices = vendorRepository.invoices().map {
                VendorAnalyticsMath.InvoiceAmount(it.vendorId, it.invoiceDate, it.totalPaise)
            },
            ledger = vendorRepository.allLedger().map { it.toLedgerRow() },
            receipts = receiptLines,
            vendorNames = names,
            outstandingPaise = vendorRepository.totalPayablePaise()
        )
    }

    private fun buildPeakHours(rows: List<BillTotalRow>): List<PeakHourPoint> {
        val counts = IntArray(24)
        val revenue = DoubleArray(24)
        val calendar = Calendar.getInstance()
        rows.forEach { row ->
            calendar.timeInMillis = row.billDate
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            counts[hour]++
            revenue[hour] += row.billTotal
        }
        return (0..23).map { hour ->
            PeakHourPoint(hour, counts[hour], revenue[hour])
        }
    }

    private fun buildDayOfWeek(rows: List<BillTotalRow>): List<DayOfWeekPoint> {
        val counts = mutableMapOf<Int, Int>()
        val revenue = mutableMapOf<Int, Double>()
        val calendar = Calendar.getInstance()
        rows.forEach { row ->
            calendar.timeInMillis = row.billDate
            val day = calendar.get(Calendar.DAY_OF_WEEK)
            counts[day] = (counts[day] ?: 0) + 1
            revenue[day] = (revenue[day] ?: 0.0) + row.billTotal
        }
        return DAY_OF_WEEK_ORDER.map { day ->
            DayOfWeekPoint(
                dayOfWeek = day,
                billCount = counts[day] ?: 0,
                revenue = revenue[day] ?: 0.0
            )
        }
    }

    private suspend fun netTopProducts(
        rows: List<com.kex.vikrsaathi.data.model.analytics.TopProductRow>,
        returnsInRange: List<com.kex.vikrsaathi.data.entity.SalesReturn>
    ): List<com.kex.vikrsaathi.data.model.analytics.TopProductRow> {
        if (customerLedgerDao == null || returnsInRange.isEmpty()) return rows
        val lines = customerLedgerDao.getAllReturnLines().groupBy { it.returnId }
        val qty = mutableMapOf<String, Int>()
        val value = mutableMapOf<String, Long>()
        returnsInRange.forEach { entry ->
            lines[entry.id].orEmpty().forEach { line ->
                qty[line.itemName] = (qty[line.itemName] ?: 0) + line.quantity
                value[line.itemName] = (value[line.itemName] ?: 0L) + line.lineValuePaise
            }
        }
        return rows.map { row ->
            row.copy(
                totalQuantity = (row.totalQuantity - (qty[row.itemName] ?: 0)).coerceAtLeast(0),
                totalRevenue = row.totalRevenue - Money.paiseToRupees(value[row.itemName] ?: 0L)
            )
        }.filter { it.totalQuantity > 0 || it.totalRevenue > 0.0 }
    }

    private fun buildSalesTrend(
        rows: List<BillTotalRow>,
        startInclusive: Long,
        endExclusive: Long,
        returnsInRange: List<com.kex.vikrsaathi.data.entity.SalesReturn> = emptyList()
    ): List<SalesTrendPoint> {
        val returnByDay = returnsInRange.groupBy { dayStartMillis(it.occurredAt) }.mapValues { (_, entries) ->
            entries.sumOf {
                ReturnReporting.merchandisePaise(it.returnValuePaise, it.moneyRefundedPaise, it.creditAdjustmentPaise)
            }
        }
        val grouped = rows.groupBy { dayStartMillis(it.billDate) }
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
        val cursor = Calendar.getInstance().apply { timeInMillis = startInclusive }
        val endCal = Calendar.getInstance().apply { timeInMillis = endExclusive }
        while (cursor.before(endCal)) {
            val dayStart = cursor.timeInMillis
            points += grouped[dayStart] ?: SalesTrendPoint(
                dayStart,
                ReturnReporting.netSalesRupees(0.0, returnByDay[dayStart] ?: 0L),
                0
            )
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        return points
    }

    private fun dayStartMillis(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun VendorLedgerEntry.toLedgerRow(): LedgerRow = LedgerRow(
        id = id,
        partyId = vendorId,
        entryType = entryType,
        amountPaise = amountPaise,
        occurredAt = occurredAt,
        reversesEntryId = reversesEntryId,
        billId = invoiceId,
        referenceType = referenceType,
        referenceId = referenceId,
        method = method,
        note = note,
        reason = reason
    )

    companion object {
        private val DAY_OF_WEEK_ORDER = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
    }
}
