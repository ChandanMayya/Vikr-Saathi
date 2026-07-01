package com.kex.vikrsaathi.util

object PriceCalculator {

    fun priceAfterDiscount(mrp: Double, discountPercent: Double): Double {
        return mrp - (mrp * discountPercent / 100.0)
    }

    fun discountAmount(mrp: Double, discountPercent: Double, quantity: Int = 1): Double {
        return mrp * discountPercent / 100.0 * quantity
    }

    fun formatAmount(amount: Double, currencySymbol: String): String {
        return "$currencySymbol${String.format("%.2f", amount)}"
    }
}
