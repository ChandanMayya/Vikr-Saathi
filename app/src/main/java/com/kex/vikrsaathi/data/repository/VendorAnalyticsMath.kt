package com.kex.vikrsaathi.data.repository

import com.kex.vikrsaathi.data.model.analytics.DayAmount
import com.kex.vikrsaathi.data.model.analytics.DayPurchasePayment
import com.kex.vikrsaathi.data.model.analytics.DeliveryQualityRow
import com.kex.vikrsaathi.data.model.analytics.VendorAnalytics
import com.kex.vikrsaathi.data.model.analytics.VendorSpendRow
import com.kex.vikrsaathi.domain.finance.LedgerMath
import com.kex.vikrsaathi.domain.finance.LedgerRow
import com.kex.vikrsaathi.domain.finance.VendorLedgerType
import java.util.Calendar

/**
 * Period purchases come from purchase invoice totals.
 * Period payments come from unreversed vendor-payment ledger entries.
 * Those two sources are never added together.
 * Outstanding is supplied by the caller from the current payable balance.
 */
object VendorAnalyticsMath {

    const val TOP_LIMIT = 5

    data class InvoiceAmount(
        val vendorId: Long,
        val invoiceDate: Long,
        val totalPaise: Long
    )

    data class ReceiptQty(
        val vendorId: Long,
        val receivedAt: Long,
        val receivedQty: Int,
        val acceptedQty: Int,
        val damagedQty: Int,
        val rejectedQty: Int
    )

    fun compute(
        startInclusive: Long,
        endExclusive: Long,
        invoices: List<InvoiceAmount>,
        ledger: List<LedgerRow>,
        receipts: List<ReceiptQty>,
        vendorNames: Map<Long, String>,
        outstandingPaise: Long
    ): VendorAnalytics {
        val periodInvoices = invoices.filter { inPeriod(it.invoiceDate, startInclusive, endExclusive) }
        val purchasesByDay = periodInvoices.groupBy { dayStart(it.invoiceDate) }
            .mapValues { (_, rows) -> rows.sumOf { it.totalPaise } }
        val paidRows = LedgerMath.unreversed(ledger).filter {
            it.entryType == VendorLedgerType.VENDOR_PAYMENT &&
                inPeriod(it.occurredAt, startInclusive, endExclusive)
        }
        val paidByDay = paidRows.groupBy { dayStart(it.occurredAt) }
            .mapValues { (_, rows) -> rows.sumOf { -it.amountPaise } }
        val days = eachDay(startInclusive, endExclusive)
        val purchasesPaise = periodInvoices.sumOf { it.totalPaise }
        val paidPaise = paidRows.sumOf { -it.amountPaise }
        return VendorAnalytics(
            enabled = true,
            purchasesPaise = purchasesPaise,
            paidPaise = paidPaise,
            outstandingPaise = outstandingPaise,
            purchaseTrend = days.map { day -> DayAmount(day, purchasesByDay[day] ?: 0L) },
            spendByVendor = spendByVendor(periodInvoices, vendorNames),
            paymentsCompared = days.map { day ->
                DayPurchasePayment(day, purchasesByDay[day] ?: 0L, paidByDay[day] ?: 0L)
            },
            showPaymentsChart = purchasesPaise != 0L || paidPaise != 0L,
            delivery = delivery(receipts, startInclusive, endExclusive, vendorNames)
        )
    }

    private fun spendByVendor(
        invoices: List<InvoiceAmount>,
        vendorNames: Map<Long, String>
    ): List<VendorSpendRow> {
        val ranked = invoices.groupBy { it.vendorId }
            .map { (vendorId, rows) ->
                VendorSpendRow(
                    vendorId = vendorId,
                    name = vendorNames[vendorId].orEmpty().ifBlank { "#$vendorId" },
                    paise = rows.sumOf { it.totalPaise }
                )
            }
            .filter { it.paise != 0L }
            .sortedByDescending { it.paise }
        if (ranked.size <= TOP_LIMIT) return ranked
        val rest = ranked.drop(TOP_LIMIT).sumOf { it.paise }
        val top = ranked.take(TOP_LIMIT)
        return if (rest == 0L) top else top + VendorSpendRow(vendorId = null, name = "", paise = rest)
    }

    private fun delivery(
        receipts: List<ReceiptQty>,
        startInclusive: Long,
        endExclusive: Long,
        vendorNames: Map<Long, String>
    ): List<DeliveryQualityRow> {
        val inPeriod = receipts.filter { inPeriod(it.receivedAt, startInclusive, endExclusive) }
        if (inPeriod.isEmpty()) return emptyList()
        if (inPeriod.any { it.receivedQty < 0 || it.acceptedQty < 0 || it.damagedQty < 0 || it.rejectedQty < 0 }) {
            return emptyList()
        }
        val rows = inPeriod.groupBy { it.vendorId }.map { (vendorId, lines) ->
            DeliveryQualityRow(
                vendorId = vendorId,
                name = vendorNames[vendorId].orEmpty().ifBlank { "#$vendorId" },
                acceptedQty = lines.sumOf { it.acceptedQty },
                damagedQty = lines.sumOf { it.damagedQty },
                rejectedQty = lines.sumOf { it.rejectedQty },
                receivedQty = lines.sumOf { it.receivedQty }
            )
        }.filter { it.receivedQty > 0 }
        val reconciles = rows.all { it.acceptedQty + it.damagedQty + it.rejectedQty == it.receivedQty }
        if (!reconciles) return emptyList()
        return rows.sortedByDescending { it.receivedQty }.take(TOP_LIMIT)
    }

    private fun inPeriod(at: Long, startInclusive: Long, endExclusive: Long): Boolean {
        return at >= startInclusive && at < endExclusive
    }

    private fun dayStart(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun eachDay(startInclusive: Long, endExclusive: Long): List<Long> {
        val days = mutableListOf<Long>()
        val cursor = Calendar.getInstance().apply { timeInMillis = dayStart(startInclusive) }
        val end = Calendar.getInstance().apply { timeInMillis = endExclusive }
        while (cursor.before(end)) {
            days += cursor.timeInMillis
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        return days
    }
}
