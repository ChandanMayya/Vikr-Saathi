package com.kex.vikrsaathi.domain.inventory

/**
 * Computes stock quantity changes for bill lifecycle events.
 * Positive delta = stock in; negative = stock out.
 */
object StockDeltaCalculator {

    fun aggregateQuantities(lines: List<Pair<Long?, Int>>): Map<Long, Int> {
        val result = mutableMapOf<Long, Int>()
        for ((itemId, qty) in lines) {
            if (itemId == null || itemId <= 0L || qty == 0) continue
            result[itemId] = (result[itemId] ?: 0) + qty
        }
        return result
    }

    /**
     * Net stock deltas when replacing [oldQuantities] sold qty with [newQuantities].
     * Selling more reduces stock (negative); selling less restores stock (positive).
     */
    fun netStockDeltas(
        oldQuantities: Map<Long, Int>,
        newQuantities: Map<Long, Int>
    ): Map<Long, Int> {
        val itemIds = oldQuantities.keys + newQuantities.keys
        val deltas = mutableMapOf<Long, Int>()
        for (itemId in itemIds) {
            val oldQty = oldQuantities[itemId] ?: 0
            val newQty = newQuantities[itemId] ?: 0
            val delta = oldQty - newQty
            if (delta != 0) deltas[itemId] = delta
        }
        return deltas
    }

    fun saleDeltas(quantities: Map<Long, Int>): Map<Long, Int> =
        quantities.mapValues { -it.value }.filterValues { it != 0 }

    fun reversalDeltas(quantities: Map<Long, Int>): Map<Long, Int> =
        quantities.filterValues { it != 0 }
}
