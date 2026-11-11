package com.kex.vikrsaathi.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReturnValuationTest {

    @Test
    fun partialReturnsAbsorbLineRoundOffOnTheFinalUnits() {
        val line = ReturnValuation.SourceLine(1, 5, "Tea", 3, 20.0, 0.01)
        val total = ReturnValuation.lineTotalPaise(3, 20.0, 0.01)
        assertEquals(6001L, total)
        val first = ReturnValuation.value(listOf(line), emptyMap(), listOf(ReturnValuation.Request(1, 1)), 0.0)
        val second = ReturnValuation.value(
            listOf(line),
            mapOf(1L to 1),
            listOf(ReturnValuation.Request(1, 1)),
            0.0
        )
        val last = ReturnValuation.value(
            listOf(line),
            mapOf(1L to 2),
            listOf(ReturnValuation.Request(1, 1)),
            -0.01
        )
        assertEquals(2000L, first.lines.single().valuePaise)
        assertEquals(0L, first.billRoundOffPaise)
        assertEquals(2000L, second.lines.single().valuePaise)
        assertEquals(2001L, last.lines.single().valuePaise)
        assertEquals(-1L, last.billRoundOffPaise)
        assertEquals(6001L - 1L, first.totalPaise + second.totalPaise + last.totalPaise)
    }

    @Test
    fun repeatedLinesKeepTheirOwnPrices() {
        val cheap = ReturnValuation.SourceLine(1, 9, "Biscuit", 2, 10.0, 0.0)
        val dear = ReturnValuation.SourceLine(2, 9, "Biscuit", 2, 15.0, 0.0)
        val value = ReturnValuation.value(
            listOf(cheap, dear),
            emptyMap(),
            listOf(ReturnValuation.Request(2, 1)),
            0.0
        )
        assertEquals(1500L, value.totalPaise)
    }

    @Test
    fun legacyReturnsWithoutALineIdReduceTheMatchingItem() {
        val sources = listOf(
            ReturnValuation.SourceLine(1, 4, "Tea", 2, 10.0, 0.0),
            ReturnValuation.SourceLine(2, 4, "Tea", 1, 10.0, 0.0)
        )
        val already = ReturnValuation.alreadyByBillItem(
            sources,
            listOf(ReturnValuation.PriorLine(null, 4, "Tea", 2))
        )
        assertEquals(2, already[1])
        assertEquals(0, already[2])
    }

    @Test
    fun overQuantityAndNegativeQuantityAreRejected() {
        val line = ReturnValuation.SourceLine(1, 1, "Tea", 1, 10.0, 0.0)
        assertFails(FinanceError.RETURN_QTY_EXCEEDS_SOLD) {
            ReturnValuation.value(listOf(line), emptyMap(), listOf(ReturnValuation.Request(1, 2)), 0.0)
        }
        assertFails(FinanceError.RETURN_QTY_INVALID) {
            ReturnValuation.value(listOf(line), emptyMap(), listOf(ReturnValuation.Request(1, 0)), 0.0)
        }
        assertFails(FinanceError.RETURN_LINE_UNKNOWN) {
            ReturnValuation.value(listOf(line), emptyMap(), listOf(ReturnValuation.Request(9, 1)), 0.0)
        }
    }

    @Test
    fun storeCreditNeedsACustomerAndCannotExceedTheReturn() {
        val cash = ReturnSettlement.resolveRefund(2000L, RefundMethod.CASH, hasCustomer = false)
        assertEquals(2000L, cash.moneyRefundedPaise)
        assertEquals(0L, cash.creditAdjustmentPaise)
        assertFails(FinanceError.CREDIT_REQUIRES_CUSTOMER) {
            ReturnSettlement.resolveRefund(2000L, RefundMethod.STORE_CREDIT, hasCustomer = false)
        }
        val exchange = ReturnSettlement.resolveExchange(2000L, 1500L, RefundMethod.UPI, hasCustomer = false)
        assertEquals(1500L, exchange.exchangeCreditPaise)
        assertEquals(500L, exchange.shopRefundsPaise)
        assertEquals(RefundMethod.UPI, exchange.refund.method)
        assertNull(exchange.customerPayMethod)
        assertFails(FinanceError.CREDIT_REQUIRES_CUSTOMER) {
            ReturnSettlement.resolveExchange(1000L, 1800L, PaymentMethod.CREDIT, hasCustomer = false)
        }
    }

    private fun assertFails(code: FinanceError, block: () -> Unit) {
        try {
            block()
        } catch (error: FinanceException) {
            assertEquals(code, error.code)
            return
        }
        throw AssertionError("Expected $code")
    }
}
