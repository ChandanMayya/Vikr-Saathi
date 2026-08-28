package com.kex.vikrsaathi.data.repository

import com.kex.vikrsaathi.data.model.BillLineItem

object BillTotalsCalculator {

    fun lineItemsSubtotal(lineItems: List<BillLineItem>): Double =
        lineItems.sumOf { it.lineTotal }

    fun lineItemsRoundOffTotal(lineItems: List<BillLineItem>): Double =
        lineItems.sumOf { it.roundOff }

    fun grandTotal(lineItems: List<BillLineItem>, billRoundOff: Double = 0.0): Double =
        lineItemsSubtotal(lineItems) + billRoundOff
}
