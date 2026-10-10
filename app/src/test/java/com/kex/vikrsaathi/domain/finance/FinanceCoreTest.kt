package com.kex.vikrsaathi.domain.finance

import com.kex.vikrsaathi.data.backup.BackupSchema
import com.kex.vikrsaathi.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceCoreTest {

    @Test
    fun rupeesConvertWithHalfUp() {
        assertEquals(1000L, Money.rupeesToPaise(10.0))
        assertEquals(1001L, Money.rupeesToPaise(10.005))
        assertEquals(1000L, Money.rupeesToPaise(10.004))
        assertEquals(10.01, Money.paiseToRupees(1001L), 0.0001)
    }

    @Test
    fun splitMustMatchExactlyAndCreditNeedsCustomer() {
        val total = Money.rupeesToPaise(100.0)
        val ok = PaymentSplitValidator.validate(
            total,
            listOf(
                PaymentPortion(PaymentMethod.CASH, 40_00),
                PaymentPortion(PaymentMethod.UPI, 60_00)
            ),
            hasRegisteredCustomer = false
        )
        assertEquals(BillPaymentStatus.LABEL_SPLIT, ok.label)
        assertEquals(0L, ok.creditPaise)

        assertFails(FinanceError.TOTAL_MISMATCH) {
            PaymentSplitValidator.validate(
                total,
                listOf(PaymentPortion(PaymentMethod.CASH, 99_99)),
                true
            )
        }
        assertFails(FinanceError.DUPLICATE_METHOD) {
            PaymentSplitValidator.validate(
                total,
                listOf(
                    PaymentPortion(PaymentMethod.CASH, 40_00),
                    PaymentPortion(PaymentMethod.CASH, 60_00)
                ),
                true
            )
        }
        assertFails(FinanceError.CREDIT_REQUIRES_CUSTOMER) {
            PaymentSplitValidator.validate(
                total,
                listOf(PaymentPortion(PaymentMethod.CREDIT, total)),
                false
            )
        }
        val credit = PaymentSplitValidator.validate(
            total,
            listOf(
                PaymentPortion(PaymentMethod.CASH, 25_00),
                PaymentPortion(PaymentMethod.CREDIT, 75_00)
            ),
            true
        )
        assertEquals(75_00L, credit.creditPaise)
    }

    @Test
    fun cashAndUpiDoNotIncreaseBalanceAndExcessPaymentIsAdvance() {
        val sale = LedgerRow(1, 1, CustomerLedgerType.CREDIT_SALE, 5000, 1_000)
        val payment = LedgerRow(2, 1, CustomerLedgerType.PAYMENT_RECEIVED, -8000, 2_000)
        assertEquals(-3000L, LedgerMath.balance(listOf(sale, payment)))
        val plan = LedgerMath.allocateOldestFirst(
            listOf(OutstandingBill(9, 1_000, 5000)),
            8000
        )
        assertEquals(5000L, plan.lines.single().amountPaise)
        assertEquals(3000L, plan.unallocatedPaise)
    }

    @Test
    fun allocationUsesPreferredBillThenOldest() {
        val bills = listOf(
            OutstandingBill(2, 2_000, 1000),
            OutstandingBill(1, 1_000, 1000)
        )
        val plan = LedgerMath.allocateOldestFirst(bills, 1500, preferredBillId = 2)
        assertEquals(2L, plan.lines[0].billId)
        assertEquals(1L, plan.lines[1].billId)
        assertEquals(0L, plan.unallocatedPaise)
    }

    @Test
    fun statementOpeningClosingAndRunningBalance() {
        val rows = listOf(
            LedgerRow(1, 1, CustomerLedgerType.OPENING_BALANCE, 1000, 100),
            LedgerRow(2, 1, CustomerLedgerType.CREDIT_SALE, 500, 200),
            LedgerRow(3, 1, CustomerLedgerType.PAYMENT_RECEIVED, -200, 300)
        )
        val statement = LedgerMath.statement(rows, 150, 250)
        assertEquals(1000L, statement.openingPaise)
        assertEquals(1500L, statement.closingPaise)
        assertEquals(1500L, statement.lines.single().runningBalancePaise)
    }

    @Test
    fun billEditReversesOnlyWhenCreditChangesAndDeleteClearsDebt() {
        val existing = listOf(LedgerRow(7, 4, CustomerLedgerType.CREDIT_SALE, 4000, 10, billId = 3))
        val unchanged = BillLedgerPlanner.planCreditSale(existing, 4, 4000, 3, ReversalReason.BILL_EDITED)
        assertTrue(unchanged.reversals.isEmpty())
        assertNull(unchanged.posting)

        val edited = BillLedgerPlanner.planCreditSale(existing, 4, 2500, 3, ReversalReason.BILL_EDITED)
        assertEquals(-4000L, edited.reversals.single().amountPaise)
        assertEquals(7L, edited.reversals.single().reversesEntryId)
        assertEquals(2500L, edited.posting!!.amountPaise)

        val deleted = BillLedgerPlanner.planCreditSale(existing, null, 0, 3, ReversalReason.BILL_DELETED)
        assertEquals(1, deleted.reversals.size)
        assertNull(deleted.posting)
        val after = LedgerMath.balance(
            existing + LedgerRow(
                8, 4, CustomerLedgerType.REVERSAL, deleted.reversals.single().amountPaise, 11,
                reversesEntryId = 7, billId = 3
            )
        )
        assertEquals(0L, after)
    }

    @Test
    fun walkInCashDoesNotPostCredit() {
        val plan = BillLedgerPlanner.planCreditSale(emptyList(), null, 0, 1, ReversalReason.BILL_EDITED)
        assertTrue(plan.reversals.isEmpty())
        assertNull(plan.posting)
    }

    @Test
    fun salesReturnCreditReducesBalanceWithoutCountingAsSale() {
        val rows = listOf(
            LedgerRow(1, 1, CustomerLedgerType.CREDIT_SALE, 10000, 1, billId = 5),
            LedgerRow(2, 1, CustomerLedgerType.SALES_RETURN, -2500, 2, billId = 5, referenceType = FinanceReference.SALES_RETURN)
        )
        assertEquals(7500L, LedgerMath.balance(rows))
        assertEquals(7500L, LedgerMath.billOutstanding(10000, 0, 2500))
        assertFalse(rows.any { it.entryType == CustomerLedgerType.CREDIT_SALE && it.amountPaise < 0 })
    }

    @Test
    fun receiptAcceptsOnlyUndamagedQuantity() {
        val qty = PurchaseMath.receiptQuantities(ReceiptQtyInput(10, 8, 1, 2))
        assertEquals(2, qty.shortQty)
        assertEquals(5, qty.acceptedQty)
        assertEquals(mapOf(4L to 5), PurchaseMath.stockDeltasForAccepted(mapOf(4L to qty.acceptedQty)))
        assertFails(FinanceError.RECEIPT_QTY_INVALID) {
            PurchaseMath.receiptQuantities(ReceiptQtyInput(10, 3, 2, 2))
        }
    }

    @Test
    fun quoteComparisonIncludesDeliveryInLandedTotal() {
        val quotes = listOf(
            QuoteSnapshot(1, "A", 500, 100, listOf(QuoteLineSnapshot("rice", "Rice", 2000, 2))),
            QuoteSnapshot(2, "B", 0, 0, listOf(QuoteLineSnapshot("rice", "Rice", 2100, 2)))
        )
        val rows = PurchaseMath.compareQuotes(quotes)
        assertEquals(2000L, rows.single().rateByQuoteId[1])
        assertEquals(2100L, rows.single().rateByQuoteId[2])
        val landedA = PurchaseMath.landedTotal(quotes[0], setOf("rice"))
        assertEquals(2000 * 2 + 500 - 100, landedA.landedPaise)
        assertTrue(landedA.coversAllItems)
    }

    @Test
    fun orderedReceivedInvoicedDifferences() {
        val rows = PurchaseMath.reconcile(
            ordered = mapOf("rice" to ("Rice" to 10)),
            accepted = mapOf("rice" to 8),
            invoiced = mapOf("rice" to 6)
        )
        assertEquals(2, rows.single().orderedVersusReceived)
        assertEquals(2, rows.single().receivedVersusInvoiced)
    }

    @Test
    fun ageingBucketsAndSalesResetKeepsOpeningBalances() {
        val now = 100L * 86_400_000L
        assertEquals(AgeBucket.CURRENT, LedgerMath.ageBucket(now - 10L * 86_400_000L, now))
        assertEquals(AgeBucket.OVER_90, LedgerMath.ageBucket(now - 100L * 86_400_000L, now))
        val opening = LedgerRow(1, 1, CustomerLedgerType.OPENING_BALANCE, 500, 1, referenceType = FinanceReference.OPENING)
        val credit = LedgerRow(2, 1, CustomerLedgerType.CREDIT_SALE, 900, 2, billId = 4, referenceType = FinanceReference.BILL)
        assertFalse(FinanceResetPolicy.isBillLinkedCustomerEntry(opening))
        assertTrue(FinanceResetPolicy.isBillLinkedCustomerEntry(credit))
        assertTrue(FinanceResetPolicy.featureTogglePreservesData())
    }

    @Test
    fun backupKeepsOldSchemasAndRemapsRelationships() {
        assertTrue(BackupSchema.SUPPORTED.containsAll(setOf(1, 2, 3)))
        assertEquals(3, BackupSchema.CURRENT)
        assertEquals(20L, IdRemapper.resolve(5, mapOf(5L to 20L), setOf(5L)))
        assertEquals(5L, IdRemapper.resolve(5, emptyMap(), setOf(5L)))
        assertNull(IdRemapper.resolve(5, emptyMap(), emptySet()))
        assertEquals(
            40L,
            FinanceRestoreLinks.customerReference(
                FinanceReference.CUSTOMER_RECEIPT, 7, emptyMap(), emptySet(), mapOf(7L to 40L), emptyMap()
            )
        )
        assertEquals(
            9L,
            FinanceRestoreLinks.customerReference(
                FinanceReference.BILL, 9, emptyMap(), setOf(9L), emptyMap(), emptyMap()
            )
        )
        assertNull(
            FinanceRestoreLinks.vendorReference(
                FinanceReference.PURCHASE_INVOICE, 3, emptyMap(), emptyMap()
            )
        )
        assertEquals(
            15L,
            FinanceRestoreLinks.vendorReference(
                FinanceReference.VENDOR_PAYMENT, 4, emptyMap(), mapOf(4L to 15L)
            )
        )
    }

    @Test
    fun invoiceTotalIncludesChargesAndDiscount() {
        assertEquals(1_150L, PurchaseMath.invoiceTotalPaise(listOf(1000L), 200, 50, 100))
    }

    private fun assertFails(code: FinanceError, block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected $code")
        } catch (error: FinanceException) {
            assertEquals(code, error.code)
        }
    }
}
