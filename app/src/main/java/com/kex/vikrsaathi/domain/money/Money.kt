package com.kex.vikrsaathi.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Monetary policy for customer and vendor balances.
 *
 * Existing bills, items, and invoice totals stay REAL rupee amounts (Double). Rewriting
 * those columns to integers would change historical invoice math, so they are left as-is.
 *
 * Ledgers, payment splits, purchase rates, and balance math use integer paise
 * (minor units, 1 rupee = 100 paise). A rupee Double is converted with HALF_UP to
 * 2 decimal places via [BigDecimal] of [Double.toString], which matches values that
 * round-trip through decimal text. Split totals must match the bill total in paise
 * exactly. Balances are sums of ledger entries, never a separately edited running total.
 */
object Money {

    const val SCALE = 2
    private val HUNDRED = BigDecimal(100)

    fun rupeesToPaise(rupees: Double): Long {
        require(rupees.isFinite()) { "Amount is not a finite number" }
        return BigDecimal(rupees.toString())
            .setScale(SCALE, RoundingMode.HALF_UP)
            .movePointRight(SCALE)
            .longValueExact()
    }

    fun paiseToRupees(paise: Long): Double {
        return BigDecimal.valueOf(paise).divide(HUNDRED).toDouble()
    }

    fun isExactSum(totalPaise: Long, portions: List<Long>): Boolean {
        return portions.sum() == totalPaise
    }
}
