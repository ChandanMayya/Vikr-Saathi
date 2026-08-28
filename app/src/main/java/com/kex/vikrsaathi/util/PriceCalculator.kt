package com.kex.vikrsaathi.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object PriceCalculator {

    private val indianAmountFormat = DecimalFormat(
        "#,##,##0.00",
        DecimalFormatSymbols(Locale("en", "IN"))
    )

    fun priceAfterDiscount(mrp: Double, discountPercent: Double): Double {
        return mrp - (mrp * discountPercent / 100.0)
    }

    fun discountAmount(mrp: Double, discountPercent: Double, quantity: Int = 1): Double {
        return mrp * discountPercent / 100.0 * quantity
    }

    fun formatIndianNumber(amount: Double): String = indianAmountFormat.format(amount)

    fun formatAmount(amount: Double, currencySymbol: String): String {
        return "$currencySymbol${formatIndianNumber(amount)}"
    }

    fun formatSignedAmount(amount: Double, currencySymbol: String): String {
        if (kotlin.math.abs(amount) < 0.005) {
            return formatAmount(0.0, currencySymbol)
        }
        val sign = if (amount > 0) "+" else "-"
        return "$sign$currencySymbol${formatIndianNumber(kotlin.math.abs(amount))}"
    }
}
