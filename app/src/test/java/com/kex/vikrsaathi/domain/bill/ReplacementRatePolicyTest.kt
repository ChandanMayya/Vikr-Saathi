package com.kex.vikrsaathi.domain.bill

import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.repository.BillTotalsCalculator
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.domain.finance.ReturnSettlement
import com.kex.vikrsaathi.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacementRatePolicyTest {
    @Test
    fun sellingPriceBecomesOneDiscountOnTheMasterRate() {
        val quote = ReplacementRatePolicy.masterQuote(mrp = 10.0, discountPercent = 20.0, sellingPrice = 5.0)
        assertEquals(10.0, quote.unitRate, 0.001)
        assertEquals(50.0, quote.discountPercent, 0.001)
        assertEquals(5.0, quote.net, 0.001)
    }

    @Test
    fun catalogDiscountAppliesOnceWhenThereIsNoSellingPrice() {
        val quote = ReplacementRatePolicy.masterQuote(mrp = 10.0, discountPercent = 10.0, sellingPrice = null)
        assertEquals(10.0, quote.unitRate, 0.001)
        assertEquals(10.0, quote.discountPercent, 0.001)
        assertEquals(9.0, quote.net, 0.001)
    }

    @Test
    fun rateAndDiscountRejectValuesOutsideTheirRange() {
        assertNull(ReplacementRatePolicy.parseRate("-1"))
        assertNull(ReplacementRatePolicy.parseRate("abc"))
        assertEquals(12.5, ReplacementRatePolicy.parseRate("₹12.50")!!, 0.001)
        assertNull(ReplacementRatePolicy.parseDiscount("101"))
        assertNull(ReplacementRatePolicy.parseDiscount("-1"))
        assertEquals(0.0, ReplacementRatePolicy.parseDiscount("0")!!, 0.001)
        assertEquals(100.0, ReplacementRatePolicy.parseDiscount("100%")!!, 0.001)
        assertEquals(7.5, ReplacementRatePolicy.lineTotal(10.0, 25.0, 1), 0.001)
    }

    @Test
    fun priceNotesMatchDifferAndExplainAMissingOriginal() {
        val master = ReplacementRatePolicy.masterQuote(10.0, 0.0, null)
        val match = ReplacementRatePolicy.compare(master, listOf(ReplacementRatePolicy.BilledPrice(10.0, 0.0, 10.0)))
        val differs = ReplacementRatePolicy.compare(master, listOf(ReplacementRatePolicy.BilledPrice(8.0, 0.0, 8.0)))
        val missing = ReplacementRatePolicy.compare(master, emptyList())
        assertEquals(ReplacementRatePolicy.PriceNote.MATCHES, match.note)
        assertEquals(0.0, match.originals.single().discountPercent, 0.001)
        assertEquals(ReplacementRatePolicy.PriceNote.DIFFERS, differs.note)
        assertEquals(ReplacementRatePolicy.PriceNote.NOT_ON_BILL, missing.note)
    }

    @Test
    fun severalOriginalRatesStayListed() {
        val master = ReplacementRatePolicy.PriceQuote(5.0, 0.0)
        val comparison = ReplacementRatePolicy.compare(
            master,
            listOf(
                ReplacementRatePolicy.BilledPrice(4.0, 0.0, 4.0),
                ReplacementRatePolicy.BilledPrice(6.0, 10.0, 5.4)
            )
        )
        assertEquals(ReplacementRatePolicy.PriceNote.DIFFERS, comparison.note)
        assertEquals(2, comparison.originals.size)
        assertEquals(10.0, comparison.originals[1].discountPercent, 0.001)
    }

    @Test
    fun sameItemMergesOnlyWhenRateAndDiscountBothMatch() {
        val first = ReplacementRatePolicy.merge(emptyList(), 7L, "Pen", 10.0, 10.0, 1, 1L)
        val same = ReplacementRatePolicy.merge(first.lines, 7L, "Pen", 10.0, 10.0, 2, first.nextKey)
        val different = ReplacementRatePolicy.merge(same.lines, 7L, "Pen", 10.0, 20.0, 1, same.nextKey)
        assertEquals(1, same.lines.size)
        assertEquals(3, same.lines.single().quantity)
        assertEquals(2, different.lines.size)
    }

    @Test
    fun savedLineKeepsTheApprovedDiscountInTheExchangeTotal() {
        val line = BillLineItem(itemId = 7L, name = "Pen", mrp = 10.0, discount = 50.0, quantity = 2)
        val total = BillTotalsCalculator.grandTotal(listOf(line), 0.0)
        val plan = ReturnSettlement.resolveExchange(800L, Money.rupeesToPaise(total), PaymentMethod.CASH, false)
        assertEquals(10.0, total, 0.001)
        assertEquals(10.0, line.mrp, 0.001)
        assertEquals(50.0, line.discount, 0.001)
        assertEquals(800L, plan.exchangeCreditPaise)
        assertEquals(200L, plan.customerPaysPaise)
        assertEquals(0L, plan.shopRefundsPaise)
        val original = BillItem(billId = 1L, itemId = 7L, itemName = "Pen", mrp = 10.0, quantity = 1, discount = 0.0, finalPrice = 10.0)
        assertEquals(0.0, original.discount, 0.001)
    }

    @Test
    fun historyLinksPointBothWaysAndIgnoreVoidedReturns() {
        val links = listOf(
            ExchangeHistory.ReturnLink(1L, 2L, voided = false, returnedQuantity = 2),
            ExchangeHistory.ReturnLink(3L, 4L, voided = true, returnedQuantity = 1)
        )
        val relations = ExchangeHistory.relations(links)
        assertEquals(ExchangeHistory.Role.ORIGINAL, relations.getValue(1L).single().role)
        assertEquals(2L, relations.getValue(1L).single().otherBillId)
        assertEquals(ExchangeHistory.Role.REPLACEMENT, relations.getValue(2L).single().role)
        assertEquals(1L, relations.getValue(2L).single().otherBillId)
        assertTrue(relations[3L] == null && relations[4L] == null)
    }

    @Test
    fun exchangeReasonRequiresAListedChoiceAndANoteForOther() {
        assertNull(ExchangeReason.problem(false, "", ""))
        assertEquals(ExchangeReason.Problem.MISSING, ExchangeReason.problem(true, "", ""))
        assertNull(ExchangeReason.problem(true, "Wrong item", ""))
        assertEquals(ExchangeReason.Problem.NOTE_REQUIRED, ExchangeReason.problem(true, "Other", " "))
        assertNull(ExchangeReason.problem(true, "Other", "Customer asked"))
    }
}
