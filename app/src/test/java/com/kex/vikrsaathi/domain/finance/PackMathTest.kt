package com.kex.vikrsaathi.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackMathTest {

    @Test
    fun legacyFactorKeepsTheEnteredQuantityAsBase() {
        assertNull(PackMath.validateTransaction("", 1))
        assertEquals(10, PackMath.toBase(10, 1))
        assertEquals("10", PackMath.formatQuantity(10, "", 1))
        assertEquals(1_800L, PackMath.lineTotalPaise(10, 180))
    }

    @Test
    fun rejectsBlankBundleNameAndNonPositiveFactor() {
        assertEquals(FinanceError.INVALID_PACK, PackMath.validateTransaction("", 20))
        assertEquals(FinanceError.INVALID_PACK, PackMath.validateTransaction("Box", 0))
        assertEquals(FinanceError.INVALID_PACK, PackMath.validateItemPack("Box", 0))
        assertNull(PackMath.validateItemPack("", 1))
        assertNull(PackMath.validateItemPack("Box", 20))
        assertNull(PackMath.toBase(-1, 20))
    }

    @Test
    fun conversionOverflowDoesNotWrap() {
        assertNull(PackMath.toBase(Int.MAX_VALUE, 2))
        assertEquals(Int.MAX_VALUE.toLong() * 2, PackMath.toBaseLong(Int.MAX_VALUE, 2))
        assertNull(PackMath.lineTotalPaise(3, Long.MAX_VALUE / 2))
    }

    @Test
    fun boxOfPensNormalizesAndPricesTheEnteredUnit() {
        assertEquals(200, PackMath.toBase(10, 20))
        assertEquals("10 Box × 20 Piece = 200 Piece", PackMath.formatQuantity(10, "Box", 20, "Piece"))
        assertEquals(180_000L, PackMath.lineTotalPaise(10, 18_000L))
        assertEquals(900L, PackMath.perBasePaise(18_000L, 20))
        val coverage = PackMath.coverage(requestedBase = 200, availablePackages = 10, unitsPerBundle = 20)
        assertEquals(10, coverage?.packagesNeeded)
        assertEquals(0, coverage?.excessBase)
        assertTrue(coverage?.canCover == true)
    }

    @Test
    fun wholePackagesShowExcessWhenTheyPassTheRequest() {
        val coverage = PackMath.coverage(requestedBase = 15, availablePackages = 2, unitsPerBundle = 10)
        assertEquals(2, coverage?.packagesNeeded)
        assertEquals(5, coverage?.excessBase)
        assertTrue(coverage?.canCover == true)
        val short = PackMath.coverage(requestedBase = 15, availablePackages = 1, unitsPerBundle = 10)
        assertEquals(false, short?.canCover)
    }

    @Test
    fun differentPackSizesComparePerBaseUnit() {
        assertTrue(PackMath.comparePerBase(18_000L, 20, 10_000L, 10)!! < 0)
        assertTrue(PackMath.comparePerBase(10_000L, 10, 18_000L, 20)!! > 0)
    }

    @Test
    fun partialReceiptStockUsesAcceptedBaseQuantity() {
        val qty = PurchaseMath.receiptQuantities(
            ReceiptQtyInput(orderedQty = 10, receivedQty = 9, damagedQty = 1, rejectedQty = 0)
        )
        assertEquals(8, qty.acceptedQty)
        assertEquals(1, qty.shortQty)
        assertEquals(160, PackMath.toBase(qty.acceptedQty, 20))
        assertEquals(180, PackMath.toBase(qty.receivedQty, 20))
        assertEquals(200, PackMath.toBase(10, 20))
    }
}
