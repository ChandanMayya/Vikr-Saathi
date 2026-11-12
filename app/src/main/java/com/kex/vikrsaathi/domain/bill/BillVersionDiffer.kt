package com.kex.vikrsaathi.domain.bill

import java.util.Locale
import kotlin.math.abs

/**
 * Builds a short, immutable description of what changed between two bill snapshots.
 * Summaries are stored with the version and are not recomputed from live masters.
 */
object BillVersionDiffer {

    const val ORIGINAL_SALE = "Original sale"
    const val WALK_IN = "Walk-in customer"

    fun restoredFrom(versionNumber: Int): String = "Restored from version $versionNumber"
    private const val MAX_PARTS = 3

    data class LineSnap(
        val itemId: Long?,
        val name: String,
        val quantity: Int,
        val mrp: Double,
        val discount: Double,
        val finalPrice: Double,
        val roundOff: Double
    )

    data class PaymentSnap(
        val method: String,
        val amountPaise: Long
    )

    data class State(
        val customerName: String,
        val customerPhone: String = "",
        val customerAddress: String = "",
        val roundOff: Double = 0.0,
        val lines: List<LineSnap> = emptyList(),
        val payments: List<PaymentSnap> = emptyList()
    )

    fun summarize(before: State, after: State, currencySymbol: String): String {
        val changes = mutableListOf<String>()
        if (before.customerName != after.customerName) {
            changes += "Customer changed: ${before.customerName} → ${after.customerName}"
        } else if (
            before.customerPhone != after.customerPhone ||
            before.customerAddress != after.customerAddress
        ) {
            changes += "Customer details changed"
        }

        val previous = before.lines.toMutableList()
        val consumed = BooleanArray(previous.size)
        for (line in after.lines) {
            val index = previous.indexOfFirstIndexed { i, candidate ->
                !consumed[i] && sameLine(candidate, line)
            }
            if (index < 0) {
                changes += "Added: ${line.name}"
                continue
            }
            consumed[index] = true
            val old = previous[index]
            if (old.quantity != line.quantity) {
                changes += "Quantity changed: ${line.name} ${old.quantity} → ${line.quantity}"
            }
            if (!sameMoney(old.discount, line.discount)) {
                changes += "Discount changed: ${line.name} ${percent(old.discount)} → ${percent(line.discount)}"
            }
            if (!sameMoney(old.mrp, line.mrp)) {
                changes += "Price changed: ${line.name} ${money(currencySymbol, old.mrp)} → ${money(currencySymbol, line.mrp)}"
            } else if (
                sameMoney(old.discount, line.discount) &&
                !sameMoney(old.finalPrice, line.finalPrice)
            ) {
                changes += "Price changed: ${line.name} ${money(currencySymbol, old.finalPrice)} → ${money(currencySymbol, line.finalPrice)}"
            }
            if (!sameMoney(old.roundOff, line.roundOff)) {
                changes += "Line round-off changed: ${line.name} ${money(currencySymbol, old.roundOff)} → ${money(currencySymbol, line.roundOff)}"
            }
        }
        previous.forEachIndexed { index, line ->
            if (!consumed[index]) changes += "Removed: ${line.name}"
        }

        if (!sameMoney(before.roundOff, after.roundOff)) {
            changes += "Bill round-off changed: ${money(currencySymbol, before.roundOff)} → ${money(currencySymbol, after.roundOff)}"
        }
        val beforePay = paymentLabel(before.payments, currencySymbol)
        val afterPay = paymentLabel(after.payments, currencySymbol)
        if (beforePay != afterPay) {
            changes += "Payment changed: $beforePay → $afterPay"
        }

        if (changes.isEmpty()) return "Details updated"
        val shown = changes.take(MAX_PARTS).joinToString("; ")
        val extra = changes.size - MAX_PARTS
        return if (extra > 0) "$shown; +$extra more" else shown
    }

    private fun sameLine(left: LineSnap, right: LineSnap): Boolean {
        if (left.itemId != null && right.itemId != null) return left.itemId == right.itemId
        return left.name.equals(right.name, ignoreCase = true)
    }

    private fun paymentLabel(payments: List<PaymentSnap>, currencySymbol: String): String {
        if (payments.isEmpty()) return "Legacy"
        return payments.sortedBy { it.method }.joinToString(" + ") { portion ->
            "${methodName(portion.method)} ${money(currencySymbol, portion.amountPaise / 100.0)}"
        }
    }

    private fun methodName(method: String): String = when (method) {
        "CASH" -> "Cash"
        "UPI" -> "UPI"
        "CREDIT" -> "Credit"
        else -> method
    }

    private fun percent(value: Double): String {
        val nearest = value.toInt()
        val text = if (abs(value - nearest) < 0.001) {
            nearest.toString()
        } else {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
        return "$text%"
    }

    private fun money(symbol: String, amount: Double): String =
        symbol + String.format(Locale.US, "%.2f", amount)

    private fun sameMoney(left: Double, right: Double): Boolean = abs(left - right) < 0.0005

    private inline fun <T> List<T>.indexOfFirstIndexed(predicate: (Int, T) -> Boolean): Int {
        for (index in indices) {
            if (predicate(index, this[index])) return index
        }
        return -1
    }
}
