package com.kex.vikrsaathi.domain.finance

import com.kex.vikrsaathi.domain.money.Money
import kotlin.math.abs

/**
 * Return value comes from the historical bill line, not the current catalog price.
 *
 * A line's paise total is its discounted amount plus that line's round-off.
 * Partial quantities split that total in whole paise. Earlier units take the even share.
 * The last units of the line absorb the remainder, so returning the whole line
 * adds back to the original line total exactly.
 *
 * Bill round-off is included only on the return that clears the last remaining unit
 * on the bill. Earlier partial returns leave it until then.
 */
object ReturnValuation {

    data class SourceLine(
        val billItemId: Long,
        val itemId: Long?,
        val itemName: String,
        val quantity: Int,
        val finalPrice: Double,
        val lineRoundOff: Double
    )

    data class PriorLine(
        val billItemId: Long?,
        val itemId: Long?,
        val itemName: String,
        val quantity: Int
    )

    data class Request(
        val billItemId: Long,
        val quantity: Int
    )

    data class ValuedLine(
        val billItemId: Long,
        val quantity: Int,
        val valuePaise: Long
    )

    data class Result(
        val lines: List<ValuedLine>,
        val billRoundOffPaise: Long,
        val totalPaise: Long
    )

    fun lineTotalPaise(quantity: Int, finalPrice: Double, lineRoundOff: Double): Long {
        return Money.rupeesToPaise(finalPrice * quantity + lineRoundOff)
    }

    /**
     * Paise for the first [taken] units. Remainder paise sit on the final units.
     */
    fun cumulativePaise(total: Long, sold: Int, taken: Int): Long {
        if (sold <= 0) throw FinanceException(FinanceError.RETURN_QTY_EXCEEDS_SOLD)
        if (taken <= 0) return 0L
        if (taken >= sold) return total
        val base = total / sold
        val remainder = (total % sold).toInt()
        val extraUnits = abs(remainder)
        val plainUnits = sold - extraUnits
        val takenPlain = minOf(taken, plainUnits)
        val takenExtra = taken - takenPlain
        val extraUnit = if (remainder >= 0) base + 1 else base - 1
        return base * takenPlain + extraUnit * takenExtra
    }

    fun alreadyByBillItem(sources: List<SourceLine>, prior: List<PriorLine>): Map<Long, Int> {
        val result = sources.associate { it.billItemId to 0 }.toMutableMap()
        prior.filter { it.billItemId != null && it.billItemId in result }.forEach { line ->
            val id = line.billItemId ?: return@forEach
            result[id] = result.getValue(id) + line.quantity
        }
        prior.filter { it.billItemId == null || it.billItemId !in result }.forEach { line ->
            var left = line.quantity
            val candidates = sources.filter { source ->
                if (line.itemId != null) source.itemId == line.itemId
                else source.itemId == null && source.itemName.equals(line.itemName, ignoreCase = true)
            }
            for (source in candidates) {
                if (left <= 0) break
                val room = source.quantity - result.getValue(source.billItemId)
                if (room <= 0) continue
                val take = minOf(room, left)
                result[source.billItemId] = result.getValue(source.billItemId) + take
                left -= take
            }
        }
        return result
    }

    fun value(
        sources: List<SourceLine>,
        already: Map<Long, Int>,
        requests: List<Request>,
        billRoundOff: Double
    ): Result {
        if (requests.isEmpty()) throw FinanceException(FinanceError.NOTHING_TO_RECORD)
        if (requests.any { it.quantity <= 0 }) throw FinanceException(FinanceError.RETURN_QTY_INVALID)
        val ids = requests.map { it.billItemId }
        if (ids.size != ids.toSet().size) throw FinanceException(FinanceError.RETURN_LINE_REPEATED)
        val byId = sources.associateBy { it.billItemId }
        val valued = requests.map { request ->
            val source = byId[request.billItemId] ?: throw FinanceException(FinanceError.RETURN_LINE_UNKNOWN)
            val priorQty = already[request.billItemId] ?: 0
            val remaining = source.quantity - priorQty
            if (request.quantity > remaining) throw FinanceException(FinanceError.RETURN_QTY_EXCEEDS_SOLD)
            val total = lineTotalPaise(source.quantity, source.finalPrice, source.lineRoundOff)
            val portion = cumulativePaise(total, source.quantity, priorQty + request.quantity) -
                cumulativePaise(total, source.quantity, priorQty)
            ValuedLine(request.billItemId, request.quantity, portion)
        }
        val soldUnits = sources.sumOf { it.quantity }
        val alreadyUnits = sources.sumOf { already[it.billItemId] ?: 0 }
        val requestUnits = requests.sumOf { it.quantity }
        val billRoundOffPaise = if (soldUnits > 0 && alreadyUnits + requestUnits == soldUnits) {
            Money.rupeesToPaise(billRoundOff)
        } else {
            0L
        }
        return Result(
            lines = valued,
            billRoundOffPaise = billRoundOffPaise,
            totalPaise = valued.sumOf { it.valuePaise } + billRoundOffPaise
        )
    }
}
