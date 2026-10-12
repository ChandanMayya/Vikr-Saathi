package com.kex.vikrsaathi.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseRequestMathTest {

    @Test
    fun requestNeedsAPositiveLine() {
        assertEquals(FinanceError.REQUEST_LINE_REQUIRED, PurchaseRequestMath.validateRequestLines(emptyList()))
        assertEquals(
            FinanceError.REQUEST_LINE_REQUIRED,
            PurchaseRequestMath.validateRequestLines(listOf(RequestLineDraft("Cement", 0)))
        )
        assertNull(PurchaseRequestMath.validateRequestLines(listOf(RequestLineDraft("Cement", 10))))
    }

    @Test
    fun missingQuoteIsNotAFreePrice() {
        val comparison = PurchaseRequestMath.compare(
            lines = listOf(line(1, "Cement", 10), line(2, "Sand", 4)),
            quotes = listOf(header(10, "Shree", delivery = 500, discount = 100), header(11, "Metro")),
            offers = listOf(
                offer(10, 1, QuoteAvailability.QUOTED, rate = 1200, available = 10),
                offer(10, 2, QuoteAvailability.UNAVAILABLE, rate = 0, available = 0),
                offer(11, 1, QuoteAvailability.QUOTED, rate = 0, available = 10)
            )
        )
        val cement = comparison.rows.first { it.requestLineId == 1L }
        val shreeCement = cement.cells.first { it.quoteId == 10L }
        val metroCement = cement.cells.first { it.quoteId == 11L }
        assertEquals(OfferState.QUOTED, shreeCement.state)
        assertEquals(1200L, shreeCement.ratePaise)
        assertEquals(OfferState.QUOTED, metroCement.state)
        assertEquals(0L, metroCement.ratePaise)
        assertTrue(metroCement.lowest)
        val sand = comparison.rows.first { it.requestLineId == 2L }
        assertEquals(OfferState.UNAVAILABLE, sand.cells.first { it.quoteId == 10L }.state)
        assertNull(sand.cells.first { it.quoteId == 10L }.ratePaise)
        assertEquals(OfferState.NOT_QUOTED, sand.cells.first { it.quoteId == 11L }.state)
        assertNull(sand.cells.first { it.quoteId == 11L }.ratePaise)
        assertTrue(comparison.summaries.all { it.goodsPaise == null })
        assertEquals(500L, comparison.summaries.first { it.quoteId == 10L }.deliveryChargesPaise)
        assertEquals(100L, comparison.summaries.first { it.quoteId == 10L }.discountPaise)
    }

    @Test
    fun goodsTotalUsesRequestedQtyAndLeavesChargesSeparate() {
        val comparison = PurchaseRequestMath.compare(
            lines = listOf(line(1, "Cement", 10)),
            quotes = listOf(header(10, "Shree", delivery = 500, discount = 100)),
            offers = listOf(offer(10, 1, QuoteAvailability.QUOTED, rate = 1200, available = 12))
        )
        assertEquals(12_000L, comparison.summaries.single().goodsPaise)
    }

    @Test
    fun goodsTotalOmittedWhenAvailableQtyIsShort() {
        val comparison = PurchaseRequestMath.compare(
            lines = listOf(line(1, "Cement", 10)),
            quotes = listOf(header(10, "Shree")),
            offers = listOf(offer(10, 1, QuoteAvailability.QUOTED, rate = 1200, available = 9))
        )
        assertNull(comparison.summaries.single().goodsPaise)
    }

    @Test
    fun awardSplitsLinesAcrossVendorsAndStopsAtTheRequest() {
        val lines = listOf(AwardLineState(1, 10, 0), AwardLineState(2, 4, 0))
        val quotes = listOf(header(10, "Shree"), header(11, "Metro"))
        val offers = listOf(
            offer(10, 1, QuoteAvailability.QUOTED, 100, 10),
            offer(11, 1, QuoteAvailability.QUOTED, 90, 6),
            offer(11, 2, QuoteAvailability.QUOTED, 50, 4)
        )
        val split = PurchaseRequestMath.planAward(
            RequestStatus.QUOTING,
            lines,
            quotes,
            offers,
            listOf(
                AwardSelection(1, 10, 4),
                AwardSelection(1, 11, 6),
                AwardSelection(2, 11, 4)
            )
        ) as AwardPlan.Accepted
        assertEquals(RequestStatus.AWARDED, split.status)
        assertEquals(setOf(10L, 11L), split.orders.map { it.vendorId }.toSet())
        assertEquals(listOf(4), split.orders.first { it.vendorId == 10L }.lines.map { it.quantity })
        assertEquals(listOf(6, 4), split.orders.first { it.vendorId == 11L }.lines.map { it.quantity })

        val over = PurchaseRequestMath.planAward(
            RequestStatus.QUOTING,
            lines,
            quotes,
            offers,
            listOf(AwardSelection(1, 10, 5), AwardSelection(1, 11, 6))
        )
        assertEquals(FinanceError.AWARD_EXCEEDS, (over as AwardPlan.Rejected).error)
    }

    @Test
    fun partialAwardStaysOpenAndARepeatCannotExceedTheRemainder() {
        val first = PurchaseRequestMath.planAward(
            RequestStatus.QUOTING,
            listOf(AwardLineState(1, 10, 0)),
            listOf(header(10, "Shree")),
            listOf(offer(10, 1, QuoteAvailability.QUOTED, 100, 10)),
            listOf(AwardSelection(1, 10, 4))
        ) as AwardPlan.Accepted
        assertEquals(RequestStatus.PARTIALLY_AWARDED, first.status)

        val repeat = PurchaseRequestMath.planAward(
            RequestStatus.PARTIALLY_AWARDED,
            listOf(AwardLineState(1, 10, 4)),
            listOf(header(10, "Shree")),
            listOf(offer(10, 1, QuoteAvailability.QUOTED, 100, 10)),
            listOf(AwardSelection(1, 10, 4))
        ) as AwardPlan.Accepted
        assertEquals(4, repeat.orders.single().lines.single().quantity)
        assertEquals(50L, repeat.orders.single().expectedDelivery)
        assertEquals("note", repeat.orders.single().notes)
        assertEquals(RequestStatus.PARTIALLY_AWARDED, repeat.status)

        val tooMuch = PurchaseRequestMath.planAward(
            RequestStatus.PARTIALLY_AWARDED,
            listOf(AwardLineState(1, 10, 4)),
            listOf(header(10, "Shree")),
            listOf(offer(10, 1, QuoteAvailability.QUOTED, 100, 10)),
            listOf(AwardSelection(1, 10, 7))
        )
        assertEquals(FinanceError.AWARD_EXCEEDS, (tooMuch as AwardPlan.Rejected).error)
    }

    @Test
    fun unavailableAndCancelledAwardsAreRejected() {
        val unavailable = PurchaseRequestMath.planAward(
            RequestStatus.QUOTING,
            listOf(AwardLineState(1, 10, 0)),
            listOf(header(10, "Shree")),
            listOf(offer(10, 1, QuoteAvailability.UNAVAILABLE, 0, 0)),
            listOf(AwardSelection(1, 10, 1))
        )
        assertEquals(FinanceError.INVALID_QUOTE, (unavailable as AwardPlan.Rejected).error)

        val cancelled = PurchaseRequestMath.planAward(
            RequestStatus.CANCELLED,
            listOf(AwardLineState(1, 10, 0)),
            listOf(header(10, "Shree")),
            listOf(offer(10, 1, QuoteAvailability.QUOTED, 100, 10)),
            listOf(AwardSelection(1, 10, 1))
        )
        assertEquals(FinanceError.REQUEST_NOT_EDITABLE, (cancelled as AwardPlan.Rejected).error)
    }

    @Test
    fun fullAwardRequiresEveryLine() {
        assertEquals(
            RequestStatus.PARTIALLY_AWARDED,
            PurchaseRequestMath.statusAfter(
                listOf(AwardLineState(1, 10, 0), AwardLineState(2, 4, 0)),
                mapOf(1L to 10, 2L to 0),
                hasQuotes = true
            )
        )
        assertEquals(
            RequestStatus.AWARDED,
            PurchaseRequestMath.statusAfter(
                listOf(AwardLineState(1, 10, 0), AwardLineState(2, 4, 0)),
                mapOf(1L to 10, 2L to 4),
                hasQuotes = true
            )
        )
    }

    private fun line(id: Long, name: String, qty: Int) = ComparedRequestLine(id, name, qty, "")

    private fun header(id: Long, name: String, delivery: Long = 0, discount: Long = 0) = QuoteHeader(
        quoteId = id,
        vendorId = id,
        vendorName = name,
        deliveryChargesPaise = delivery,
        discountPaise = discount,
        expectedDelivery = 50L,
        notes = "note"
    )

    private fun offer(quoteId: Long, lineId: Long, availability: String, rate: Long, available: Int) = LineOffer(
        quoteId = quoteId,
        requestLineId = lineId,
        availability = availability,
        ratePaise = rate,
        availableQty = available,
        itemId = lineId,
        itemName = "Item"
    )
}
