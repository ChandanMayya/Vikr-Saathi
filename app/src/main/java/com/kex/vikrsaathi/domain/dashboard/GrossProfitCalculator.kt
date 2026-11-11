package com.kex.vikrsaathi.domain.dashboard

/**
 * Gross profit from amounts captured on the sale and return, not from an item's current price.
 * Revenue is the amount after line discounts. Bill round-off is included once.
 * Cost is the purchase-invoice (or restored) unit cost stored when the unit was sold.
 * Any unit without that cost makes the whole result incomplete, and no profit total is returned.
 */
object GrossProfitCalculator {

    enum class Coverage { COMPLETE, INCOMPLETE }

    data class SaleSlice(
        val revenuePaise: Long,
        val quantity: Int,
        val unitCostPaise: Long?,
        /** False when the unit was not put back into stock, so its cost stays in this result. */
        val recoverCost: Boolean = true
    )

    data class ReturnSlice(
        val refundPaise: Long,
        val lines: List<SaleSlice> = emptyList()
    )

    data class Result(
        val coverage: Coverage,
        val revenuePaise: Long,
        val costPaise: Long?,
        val profitPaise: Long?,
        val marginPercent: Double?,
        val uncoveredUnits: Int
    )

    fun calculate(
        sales: List<SaleSlice>,
        billRoundOffPaise: Long,
        returns: List<ReturnSlice> = emptyList()
    ): Result {
        val soldUnits = sales.filter { it.quantity > 0 }
        val returnLines = returns.flatMap { slice -> slice.lines.filter { it.quantity > 0 } }
        val uncoveredSales = soldUnits.filter { it.unitCostPaise == null }.sumOf { it.quantity }
        val uncoveredReturns = returnLines.filter { it.unitCostPaise == null }.sumOf { it.quantity }
        val refundWithoutLines = returns.any { it.refundPaise != 0L && it.lines.none { line -> line.quantity > 0 } }
        val uncovered = uncoveredSales + uncoveredReturns + if (refundWithoutLines) 1 else 0
        val revenue = soldUnits.sumOf { it.revenuePaise } + billRoundOffPaise - returns.sumOf { it.refundPaise }
        if (uncovered > 0) {
            return Result(
                coverage = Coverage.INCOMPLETE,
                revenuePaise = revenue,
                costPaise = null,
                profitPaise = null,
                marginPercent = null,
                uncoveredUnits = uncovered
            )
        }
        val cost = soldUnits.sumOf { it.quantity * (it.unitCostPaise ?: 0L) } -
            returnLines.sumOf { line ->
                if (!line.recoverCost) 0L else line.quantity * (line.unitCostPaise ?: 0L)
            }
        val profit = revenue - cost
        val margin = if (revenue == 0L) null else (profit.toDouble() / revenue.toDouble()) * 100.0
        return Result(
            coverage = Coverage.COMPLETE,
            revenuePaise = revenue,
            costPaise = cost,
            profitPaise = profit,
            marginPercent = margin,
            uncoveredUnits = 0
        )
    }
}
