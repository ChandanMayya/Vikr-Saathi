package com.kex.vikrsaathi.data.repository

import com.kex.vikrsaathi.domain.finance.LedgerRow
import com.kex.vikrsaathi.domain.finance.VendorLedgerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class VendorAnalyticsMathTest {

    @Test
    fun purchasesUseInvoicesOnlyAndRespectTheDateRange() {
        val result = VendorAnalyticsMath.compute(
            startInclusive = start,
            endExclusive = end,
            invoices = listOf(
                invoice(1, at(10, 15), 10_000),
                invoice(2, at(12, 9), 5_000),
                invoice(1, at(9, 12), 7_000),
                invoice(1, end, 99_000)
            ),
            ledger = listOf(
                row(1, VendorLedgerType.PURCHASE_INVOICE, 10_000, at(10, 15)),
                row(2, VendorLedgerType.VENDOR_PAYMENT, -3_000, at(11, 10))
            ),
            receipts = emptyList(),
            vendorNames = mapOf(1L to "Shree", 2L to "Metro"),
            outstandingPaise = 42_000
        )

        assertEquals(15_000L, result.purchasesPaise)
        assertEquals(3_000L, result.paidPaise)
        assertEquals(42_000L, result.outstandingPaise)
        assertEquals(3, result.purchaseTrend.size)
        assertEquals(10_000L, result.purchaseTrend[0].paise)
        assertEquals(0L, result.purchaseTrend[1].paise)
        assertEquals(5_000L, result.purchaseTrend[2].paise)
        assertEquals(0L, result.paymentsCompared[1].purchasesPaise)
        assertEquals(3_000L, result.paymentsCompared[1].paidPaise)
        assertTrue(result.showPaymentsChart)
    }

    @Test
    fun reversedPaymentDoesNotCount() {
        val result = VendorAnalyticsMath.compute(
            startInclusive = start,
            endExclusive = end,
            invoices = emptyList(),
            ledger = listOf(
                row(10, VendorLedgerType.VENDOR_PAYMENT, -9_000, at(11, 8)),
                row(11, VendorLedgerType.REVERSAL, 9_000, at(11, 9), reversesEntryId = 10)
            ),
            receipts = emptyList(),
            vendorNames = emptyMap(),
            outstandingPaise = 1_500
        )

        assertEquals(0L, result.paidPaise)
        assertEquals(0L, result.purchasesPaise)
        assertEquals(1_500L, result.outstandingPaise)
        assertFalse(result.showPaymentsChart)
    }

    @Test
    fun spendKeepsTheTopFiveAndSumsTheRest() {
        val invoices = (1L..6L).map { id -> invoice(id, at(10, 12), id * 100) }
        val names = (1L..6L).associateWith { "Vendor $it" }
        val result = VendorAnalyticsMath.compute(
            start, end, invoices, emptyList(), emptyList(), names, outstandingPaise = 0L
        )
        assertEquals(6, result.spendByVendor.size)
        assertEquals(600L, result.spendByVendor.first().paise)
        assertEquals(null, result.spendByVendor.last().vendorId)
        assertEquals(100L, result.spendByVendor.last().paise)
    }

    @Test
    fun deliveryRequiresAcceptedDamagedAndRejectedToMatchReceived() {
        val sound = VendorAnalyticsMath.ReceiptQty(1, at(10, 11), 10, 8, 1, 1)
        val broken = VendorAnalyticsMath.ReceiptQty(2, at(11, 11), 9, 8, 1, 1)
        val omitted = VendorAnalyticsMath.compute(
            start, end, emptyList(), emptyList(), listOf(sound, broken),
            mapOf(1L to "Shree", 2L to "Metro"), 0L
        )
        assertTrue(omitted.delivery.isEmpty())

        val shown = VendorAnalyticsMath.compute(
            start, end, emptyList(), emptyList(), listOf(sound),
            mapOf(1L to "Shree"), 0L
        )
        assertEquals(1, shown.delivery.size)
        assertEquals(10, shown.delivery[0].receivedQty)
        assertEquals(8, shown.delivery[0].acceptedQty)
    }

    private fun invoice(vendorId: Long, at: Long, paise: Long) =
        VendorAnalyticsMath.InvoiceAmount(vendorId, at, paise)

    private fun row(
        id: Long,
        type: String,
        amountPaise: Long,
        at: Long,
        reversesEntryId: Long? = null
    ) = LedgerRow(
        id = id,
        partyId = 1L,
        entryType = type,
        amountPaise = amountPaise,
        occurredAt = at,
        reversesEntryId = reversesEntryId
    )

    private fun at(day: Int, hour: Int): Long {
        return Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private val start = at(10, 0)
    private val end = at(13, 0)
}
