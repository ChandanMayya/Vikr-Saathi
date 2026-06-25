package com.loctell.vikrsaathi.util

object PriceCalculator {

    fun priceAfterDiscount(mrp: Double, discountPercent: Double): Double {
        return mrp - (mrp * discountPercent / 100.0)
    }

    fun formatAmount(amount: Double, currencySymbol: String): String {
        return "$currencySymbol${String.format("%.2f", amount)}"
    }
}
