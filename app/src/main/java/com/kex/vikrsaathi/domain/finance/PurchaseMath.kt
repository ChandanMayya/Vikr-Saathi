package com.kex.vikrsaathi.domain.finance

data class ReceiptQtyInput(
    val orderedQty: Int,
    val receivedQty: Int,
    val damagedQty: Int,
    val rejectedQty: Int
)

data class ReceiptQtyResult(
    val orderedQty: Int,
    val receivedQty: Int,
    val shortQty: Int,
    val damagedQty: Int,
    val rejectedQty: Int,
    val acceptedQty: Int
)

data class QuoteLineSnapshot(
    val itemKey: String,
    val itemName: String,
    val ratePaise: Long,
    val quantity: Int
)

data class QuoteSnapshot(
    val quoteId: Long,
    val vendorName: String,
    val deliveryChargesPaise: Long,
    val discountPaise: Long,
    val lines: List<QuoteLineSnapshot>
)

data class QuoteComparisonRow(
    val itemKey: String,
    val itemName: String,
    val rateByQuoteId: Map<Long, Long>
)

data class QuoteLandedTotal(
    val quoteId: Long,
    val vendorName: String,
    val goodsPaise: Long,
    val deliveryChargesPaise: Long,
    val discountPaise: Long,
    val landedPaise: Long,
    val coversAllItems: Boolean
)

data class QtyReconciliation(
    val itemKey: String,
    val itemName: String,
    val orderedQty: Int,
    val acceptedQty: Int,
    val invoicedQty: Int
) {
    val orderedVersusReceived: Int get() = orderedQty - acceptedQty
    val receivedVersusInvoiced: Int get() = acceptedQty - invoicedQty
}

object PurchaseMath {

    /** Stock increases only by accepted quantity: received minus damaged minus rejected. */
    fun receiptQuantities(input: ReceiptQtyInput): ReceiptQtyResult {
        if (input.orderedQty < 0 || input.receivedQty < 0 || input.damagedQty < 0 || input.rejectedQty < 0) {
            throw FinanceException(FinanceError.RECEIPT_QTY_INVALID)
        }
        if (input.damagedQty + input.rejectedQty > input.receivedQty) {
            throw FinanceException(FinanceError.RECEIPT_QTY_INVALID)
        }
        val short = (input.orderedQty - input.receivedQty).coerceAtLeast(0)
        val accepted = input.receivedQty - input.damagedQty - input.rejectedQty
        return ReceiptQtyResult(
            orderedQty = input.orderedQty,
            receivedQty = input.receivedQty,
            shortQty = short,
            damagedQty = input.damagedQty,
            rejectedQty = input.rejectedQty,
            acceptedQty = accepted
        )
    }

    fun stockDeltasForAccepted(acceptedByItem: Map<Long, Int>): Map<Long, Int> =
        acceptedByItem.filterValues { it != 0 }

    fun invoiceTotalPaise(
        lineTotalsPaise: List<Long>,
        deliveryChargesPaise: Long,
        otherChargesPaise: Long,
        discountPaise: Long
    ): Long {
        if (deliveryChargesPaise < 0 || otherChargesPaise < 0 || discountPaise < 0) {
            throw FinanceException(FinanceError.INVOICE_TOTAL_MISMATCH)
        }
        return lineTotalsPaise.sum() + deliveryChargesPaise + otherChargesPaise - discountPaise
    }

    fun compareQuotes(quotes: List<QuoteSnapshot>): List<QuoteComparisonRow> {
        val items = linkedMapOf<String, String>()
        quotes.forEach { quote ->
            quote.lines.forEach { line ->
                items.putIfAbsent(line.itemKey, line.itemName)
            }
        }
        return items.map { (key, name) ->
            val rates = quotes.mapNotNull { quote ->
                quote.lines.firstOrNull { it.itemKey == key }?.let { quote.quoteId to it.ratePaise }
            }.toMap()
            QuoteComparisonRow(key, name, rates)
        }
    }

    /**
     * Landed total uses each quoted line quantity (at least 1) times the rate,
     * plus delivery charges, minus the quote-level discount.
     */
    fun landedTotal(quote: QuoteSnapshot, requiredItemKeys: Set<String> = emptySet()): QuoteLandedTotal {
        var goods = 0L
        val present = quote.lines.map { it.itemKey }.toSet()
        for (line in quote.lines) {
            val qty = line.quantity.coerceAtLeast(1)
            goods += line.ratePaise * qty
        }
        val covers = requiredItemKeys.isEmpty() || requiredItemKeys.all { it in present }
        return QuoteLandedTotal(
            quoteId = quote.quoteId,
            vendorName = quote.vendorName,
            goodsPaise = goods,
            deliveryChargesPaise = quote.deliveryChargesPaise,
            discountPaise = quote.discountPaise,
            landedPaise = goods + quote.deliveryChargesPaise - quote.discountPaise,
            coversAllItems = covers
        )
    }

    fun reconcile(
        ordered: Map<String, Pair<String, Int>>,
        accepted: Map<String, Int>,
        invoiced: Map<String, Int>
    ): List<QtyReconciliation> {
        val keys = (ordered.keys + accepted.keys + invoiced.keys).distinct()
        return keys.map { key ->
            QtyReconciliation(
                itemKey = key,
                itemName = ordered[key]?.first ?: key,
                orderedQty = ordered[key]?.second ?: 0,
                acceptedQty = accepted[key] ?: 0,
                invoicedQty = invoiced[key] ?: 0
            )
        }
    }
}
