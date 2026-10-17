package com.kex.vikrsaathi.ui.finance

import com.kex.vikrsaathi.domain.finance.PackMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteCompareFormatTest {

    @Test
    fun packagePriceTimesPackagesAndLandedChargesStaySeparate() {
        val coverage = PackMath.coverage(500, 10, 100)
        assertTrue(coverage != null && coverage.canCover)
        assertEquals(5, coverage!!.packagesNeeded)
        assertEquals(0, coverage.excessBase)
        assertEquals(150_000L, QuoteCompareFormat.materialGoodsPaise(30_000L, coverage.packagesNeeded, true))
        assertEquals(300L, PackMath.perBasePaise(30_000L, 100))
        assertEquals(159_500L, QuoteCompareFormat.landedPaise(150_000L, 10_000L, 500L))

        val otherGoods = QuoteCompareFormat.materialGoodsPaise(25_000L, 5, true)
        assertEquals(125_000L, otherGoods)
        val otherLanded = QuoteCompareFormat.landedPaise(125_000L, 10_000L, 500L)
        assertEquals(134_500L, otherLanded)
        assertEquals(setOf(1), QuoteCompareFormat.lowestIndexes(listOf(159_500L, otherLanded)))

        assertEquals("₹300.00 / box", QuoteCompareFormat.perUnit("₹300.00", "box"))
        assertEquals("100 piece / box", QuoteCompareFormat.packageSize(100, "piece", "box"))
        assertEquals("5 box · 0 extra", QuoteCompareFormat.packages(5, "box", QuoteCompareFormat.extra(0, "piece")))
        assertEquals(
            "Goods ₹1,500.00 · Delivery ₹100.00 · Discount −₹5.00 · Total ₹1,595.00",
            QuoteCompareFormat.charges("₹1,500.00", "₹100.00", "₹5.00", "₹1,595.00")
        )
    }

    @Test
    fun missingOrUncoveredOfferIsNotTreatedAsZero() {
        assertNull(QuoteCompareFormat.materialGoodsPaise(0L, 5, false))
        assertEquals(emptySet<Int>(), QuoteCompareFormat.lowestIndexes(listOf(100L, null)))
        assertEquals(emptySet<Int>(), QuoteCompareFormat.lowestIndexes(listOf(100L)))
        assertEquals(setOf(0, 1), QuoteCompareFormat.lowestIndexes(listOf(80L, 80L, null)))
        assertEquals("20 piece extra", QuoteCompareFormat.extra(20, "piece"))
    }
}
