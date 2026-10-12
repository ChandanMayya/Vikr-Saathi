package com.kex.vikrsaathi.domain.finance

object QuoteAvailability {
    const val QUOTED = "QUOTED"
    const val UNAVAILABLE = "UNAVAILABLE"
}

object RequestStatus {
    const val DRAFT = "DRAFT"
    const val QUOTING = "QUOTING"
    const val PARTIALLY_AWARDED = "PARTIALLY_AWARDED"
    const val AWARDED = "AWARDED"
    const val CANCELLED = "CANCELLED"
}

data class RequestLineDraft(
    val itemName: String,
    val quantity: Int
)

data class RequestQuoteDraft(
    val requestLineId: Long,
    val availability: String,
    val ratePaise: Long,
    val availableQty: Int
)

data class AwardLineState(
    val requestLineId: Long,
    val requestedQty: Int,
    val awardedQty: Int
)

data class QuoteHeader(
    val quoteId: Long,
    val vendorId: Long,
    val vendorName: String,
    val deliveryChargesPaise: Long,
    val discountPaise: Long,
    val expectedDelivery: Long?,
    val notes: String
)

data class LineOffer(
    val quoteId: Long,
    val requestLineId: Long,
    val availability: String,
    val ratePaise: Long,
    val availableQty: Int,
    val itemId: Long?,
    val itemName: String
)

data class AwardSelection(
    val requestLineId: Long,
    val quoteId: Long,
    val quantity: Int
)

data class PlannedOrderLine(
    val requestLineId: Long,
    val itemId: Long?,
    val itemName: String,
    val quantity: Int,
    val ratePaise: Long
)

data class PlannedVendorOrder(
    val vendorId: Long,
    val quoteId: Long,
    val expectedDelivery: Long?,
    val notes: String,
    val lines: List<PlannedOrderLine>
)

sealed class AwardPlan {
    data class Accepted(
        val orders: List<PlannedVendorOrder>,
        val status: String
    ) : AwardPlan()

    data class Rejected(val error: FinanceError) : AwardPlan()
}

enum class OfferState {
    QUOTED,
    UNAVAILABLE,
    NOT_QUOTED
}

data class ComparisonCell(
    val quoteId: Long,
    val state: OfferState,
    val ratePaise: Long?,
    val availableQty: Int?,
    val lowest: Boolean
)

data class MaterialComparisonRow(
    val requestLineId: Long,
    val itemName: String,
    val requestedQty: Int,
    val notes: String,
    val cells: List<ComparisonCell>
)

data class VendorQuoteSummary(
    val quoteId: Long,
    val vendorName: String,
    val deliveryChargesPaise: Long,
    val discountPaise: Long,
    /** Goods for the full requested quantities. Null when this vendor cannot be compared fairly. */
    val goodsPaise: Long?
)

data class RequestComparison(
    val rows: List<MaterialComparisonRow>,
    val summaries: List<VendorQuoteSummary>
)

data class ComparedRequestLine(
    val requestLineId: Long,
    val itemName: String,
    val requestedQty: Int,
    val notes: String
)

object PurchaseRequestMath {

    fun remaining(requestedQty: Int, awardedQty: Int): Int =
        (requestedQty - awardedQty).coerceAtLeast(0)

    fun validateRequestLines(lines: List<RequestLineDraft>): FinanceError? {
        if (lines.isEmpty() || lines.any { it.itemName.isBlank() || it.quantity <= 0 }) {
            return FinanceError.REQUEST_LINE_REQUIRED
        }
        return null
    }

    fun validateQuote(
        deliveryChargesPaise: Long,
        discountPaise: Long,
        lines: List<RequestQuoteDraft>,
        requestLineIds: Set<Long>
    ): FinanceError? {
        if (deliveryChargesPaise < 0L || discountPaise < 0L) return FinanceError.INVALID_QUOTE
        if (lines.isEmpty()) return FinanceError.QUOTE_LINE_REQUIRED
        if (lines.map { it.requestLineId }.distinct().size != lines.size) return FinanceError.INVALID_QUOTE
        if (lines.any { it.requestLineId !in requestLineIds }) return FinanceError.INVALID_QUOTE
        lines.forEach { line ->
            when (line.availability) {
                QuoteAvailability.QUOTED -> {
                    if (line.ratePaise < 0L || line.availableQty <= 0) return FinanceError.INVALID_QUOTE
                }
                QuoteAvailability.UNAVAILABLE -> Unit
                else -> return FinanceError.INVALID_QUOTE
            }
        }
        return null
    }

    fun statusAfter(
        lines: List<AwardLineState>,
        awardedQtyByLine: Map<Long, Int>,
        hasQuotes: Boolean
    ): String {
        if (lines.isEmpty()) return RequestStatus.DRAFT
        var anyAwarded = false
        var anyRemaining = false
        lines.forEach { line ->
            val awarded = awardedQtyByLine[line.requestLineId] ?: 0
            if (awarded > 0) anyAwarded = true
            if (awarded < line.requestedQty) anyRemaining = true
        }
        return when {
            anyAwarded && !anyRemaining -> RequestStatus.AWARDED
            anyAwarded && anyRemaining -> RequestStatus.PARTIALLY_AWARDED
            hasQuotes -> RequestStatus.QUOTING
            else -> RequestStatus.DRAFT
        }
    }

    fun planAward(
        requestStatus: String,
        lines: List<AwardLineState>,
        quotes: List<QuoteHeader>,
        offers: List<LineOffer>,
        selections: List<AwardSelection>
    ): AwardPlan {
        if (requestStatus == RequestStatus.CANCELLED) {
            return AwardPlan.Rejected(FinanceError.REQUEST_NOT_EDITABLE)
        }
        val active = selections.filter { it.quantity > 0 }
        if (active.isEmpty()) return AwardPlan.Rejected(FinanceError.NOTHING_TO_RECORD)
        val lineById = lines.associateBy { it.requestLineId }
        val quoteById = quotes.associateBy { it.quoteId }
        val added = mutableMapOf<Long, Int>()
        val chosen = mutableListOf<Triple<QuoteHeader, LineOffer, Int>>()
        active.forEach { selection ->
            val line = lineById[selection.requestLineId]
                ?: return AwardPlan.Rejected(FinanceError.AWARD_EXCEEDS)
            val quote = quoteById[selection.quoteId]
                ?: return AwardPlan.Rejected(FinanceError.INVALID_QUOTE)
            val offer = offers.find {
                it.quoteId == selection.quoteId && it.requestLineId == selection.requestLineId
            } ?: return AwardPlan.Rejected(FinanceError.INVALID_QUOTE)
            if (offer.availability != QuoteAvailability.QUOTED) {
                return AwardPlan.Rejected(FinanceError.INVALID_QUOTE)
            }
            val open = remaining(line.requestedQty, line.awardedQty + (added[line.requestLineId] ?: 0))
            if (selection.quantity > open || selection.quantity > offer.availableQty) {
                return AwardPlan.Rejected(FinanceError.AWARD_EXCEEDS)
            }
            added[line.requestLineId] = (added[line.requestLineId] ?: 0) + selection.quantity
            chosen += Triple(quote, offer, selection.quantity)
        }
        val orders = chosen.groupBy { it.first.vendorId }.map { (_, rows) ->
            val header = rows.first().first
            if (rows.any { it.first.quoteId != header.quoteId }) {
                return AwardPlan.Rejected(FinanceError.INVALID_QUOTE)
            }
            PlannedVendorOrder(
                vendorId = header.vendorId,
                quoteId = header.quoteId,
                expectedDelivery = header.expectedDelivery,
                notes = header.notes,
                lines = rows.map { (_, offer, quantity) ->
                    PlannedOrderLine(
                        requestLineId = offer.requestLineId,
                        itemId = offer.itemId,
                        itemName = offer.itemName,
                        quantity = quantity,
                        ratePaise = offer.ratePaise
                    )
                }
            )
        }
        val awarded = lines.associate { line ->
            line.requestLineId to (line.awardedQty + (added[line.requestLineId] ?: 0))
        }
        return AwardPlan.Accepted(
            orders = orders,
            status = statusAfter(lines, awarded, hasQuotes = quotes.isNotEmpty())
        )
    }

    fun compare(
        lines: List<ComparedRequestLine>,
        quotes: List<QuoteHeader>,
        offers: List<LineOffer>
    ): RequestComparison {
        val rows = lines.map { line ->
            val cells = quotes.map { quote ->
                val matches = offers.filter { it.quoteId == quote.quoteId && it.requestLineId == line.requestLineId }
                val offer = matches.firstOrNull { it.availability == QuoteAvailability.QUOTED } ?: matches.firstOrNull()
                when (offer?.availability) {
                    QuoteAvailability.QUOTED -> ComparisonCell(
                        quoteId = quote.quoteId,
                        state = OfferState.QUOTED,
                        ratePaise = offer.ratePaise,
                        availableQty = offer.availableQty,
                        lowest = false
                    )
                    QuoteAvailability.UNAVAILABLE -> ComparisonCell(
                        quoteId = quote.quoteId,
                        state = OfferState.UNAVAILABLE,
                        ratePaise = null,
                        availableQty = null,
                        lowest = false
                    )
                    else -> ComparisonCell(
                        quoteId = quote.quoteId,
                        state = OfferState.NOT_QUOTED,
                        ratePaise = null,
                        availableQty = null,
                        lowest = false
                    )
                }
            }
            val comparable = cells.mapNotNull { cell ->
                val rate = cell.ratePaise
                val available = cell.availableQty
                if (cell.state == OfferState.QUOTED && rate != null && available != null && available > 0) rate else null
            }
            val best = comparable.minOrNull()
            MaterialComparisonRow(
                requestLineId = line.requestLineId,
                itemName = line.itemName,
                requestedQty = line.requestedQty,
                notes = line.notes,
                cells = cells.map { cell ->
                    val rate = cell.ratePaise
                    cell.copy(
                        lowest = best != null &&
                            cell.state == OfferState.QUOTED &&
                            rate == best &&
                            (cell.availableQty ?: 0) > 0
                    )
                }
            )
        }
        val summaries = quotes.map { quote ->
            val goods = lines.fold(0L) { sum, line ->
                val cell = rows.first { it.requestLineId == line.requestLineId }
                    .cells.first { it.quoteId == quote.quoteId }
                val available = cell.availableQty ?: 0
                val rate = cell.ratePaise
                if (cell.state != OfferState.QUOTED || rate == null || available < line.requestedQty) {
                    return@map VendorQuoteSummary(
                        quoteId = quote.quoteId,
                        vendorName = quote.vendorName,
                        deliveryChargesPaise = quote.deliveryChargesPaise,
                        discountPaise = quote.discountPaise,
                        goodsPaise = null
                    )
                }
                sum + rate * line.requestedQty
            }
            VendorQuoteSummary(
                quoteId = quote.quoteId,
                vendorName = quote.vendorName,
                deliveryChargesPaise = quote.deliveryChargesPaise,
                discountPaise = quote.discountPaise,
                goodsPaise = goods
            )
        }
        return RequestComparison(rows, summaries)
    }
}
