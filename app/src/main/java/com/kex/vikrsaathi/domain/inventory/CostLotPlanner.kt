package com.kex.vikrsaathi.domain.inventory

/**
 * Purchase-invoice cost lots. Already-sold units keep the cost snapshotted on the bill.
 * An edited invoice only replaces the quantity that is still on hand.
 */
object CostLotPlanner {

    data class ExistingLot(
        val sourceId: Long,
        val itemId: Long,
        val qtyReceived: Int,
        val qtyRemaining: Int
    )

    data class Incoming(
        val sourceId: Long,
        val itemId: Long,
        val baseQty: Int,
        val unitCostPaise: Long
    )

    data class LotInsert(
        val sourceId: Long,
        val itemId: Long,
        val qtyReceived: Int,
        val qtyRemaining: Int,
        val unitCostPaise: Long
    )

    data class Plan(
        val deleteSourceIds: List<Long>,
        val inserts: List<LotInsert>
    )

    fun replacePurchase(existing: List<ExistingLot>, incoming: List<Incoming>): Plan {
        val consumedLeft = existing
            .groupBy { it.itemId }
            .mapValues { (_, lots) ->
                lots.sumOf { (it.qtyReceived - it.qtyRemaining).coerceAtLeast(0) }
            }
            .toMutableMap()
        val inserts = incoming.filter { it.baseQty > 0 && it.itemId > 0L }.map { line ->
            val consumed = consumedLeft[line.itemId] ?: 0
            val applied = minOf(consumed, line.baseQty)
            consumedLeft[line.itemId] = consumed - applied
            LotInsert(
                sourceId = line.sourceId,
                itemId = line.itemId,
                qtyReceived = line.baseQty,
                qtyRemaining = line.baseQty - applied,
                unitCostPaise = line.unitCostPaise
            )
        }
        return Plan(
            deleteSourceIds = existing.map { it.sourceId }.distinct(),
            inserts = inserts
        )
    }

    fun baseQuantity(quantity: Int, unitsPerBundle: Int): Int =
        quantity.coerceAtLeast(0) * unitsPerBundle.coerceAtLeast(1)

    fun unitCostPerBase(ratePaise: Long, unitsPerBundle: Int): Long {
        val bundle = unitsPerBundle.coerceAtLeast(1)
        return ratePaise / bundle
    }
}
