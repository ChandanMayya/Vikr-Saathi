package com.kex.vikrsaathi.data.repository

import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.CostSourceType
import com.kex.vikrsaathi.data.entity.ItemCostLot
import com.kex.vikrsaathi.data.entity.PurchaseInvoiceLine
import com.kex.vikrsaathi.domain.inventory.CostLotPlanner

/**
 * FIFO lots of known unit cost. A sale stores the resulting unit cost on the bill line.
 * Missing lots stay missing: the current item master price is never used as a substitute.
 */
class ItemCostRepository(
    private val database: AppDatabase
) {
    private val dao get() = database.itemCostDao()

    suspend fun replacePurchaseInvoice(
        oldLines: List<PurchaseInvoiceLine>,
        newLines: List<PurchaseInvoiceLine>,
        receivedAt: Long
    ) {
        val existing = oldLines.flatMap { line ->
            dao.lotsForSource(CostSourceType.PURCHASE_INVOICE, line.id).map { lot ->
                CostLotPlanner.ExistingLot(
                    sourceId = lot.sourceId,
                    itemId = lot.itemId,
                    qtyReceived = lot.qtyReceived,
                    qtyRemaining = lot.qtyRemaining
                )
            }
        }
        val incoming = newLines.mapNotNull { line ->
            val itemId = line.itemId ?: return@mapNotNull null
            CostLotPlanner.Incoming(
                sourceId = line.id,
                itemId = itemId,
                baseQty = CostLotPlanner.baseQuantity(line.quantity, line.unitsPerBundle),
                unitCostPaise = CostLotPlanner.unitCostPerBase(line.ratePaise, line.unitsPerBundle)
            )
        }
        val plan = CostLotPlanner.replacePurchase(existing, incoming)
        plan.deleteSourceIds.forEach { sourceId ->
            dao.deleteSource(CostSourceType.PURCHASE_INVOICE, sourceId)
        }
        if (plan.inserts.isNotEmpty()) {
            dao.insertAll(
                plan.inserts.map { lot ->
                    ItemCostLot(
                        itemId = lot.itemId,
                        sourceType = CostSourceType.PURCHASE_INVOICE,
                        sourceId = lot.sourceId,
                        qtyReceived = lot.qtyReceived,
                        qtyRemaining = lot.qtyRemaining,
                        unitCostPaise = lot.unitCostPaise,
                        receivedAt = receivedAt
                    )
                }
            )
        }
    }

    /**
     * Takes [qty] from the oldest lots. Returns the average unit cost when every unit is covered.
     * A partial lot is still consumed so it cannot be applied to a later sale, and the result is null.
     */
    suspend fun consume(itemId: Long, qty: Int): Long? {
        if (qty <= 0) return null
        val lots = dao.availableLots(itemId)
        val available = lots.sumOf { it.qtyRemaining }
        if (available < qty) {
            lots.forEach { dao.updateRemaining(it.id, 0) }
            return null
        }
        var need = qty
        var cost = 0L
        for (lot in lots) {
            if (need == 0) break
            val take = minOf(lot.qtyRemaining, need)
            dao.updateRemaining(lot.id, lot.qtyRemaining - take)
            cost += take * lot.unitCostPaise
            need -= take
        }
        return cost / qty
    }

    suspend fun restore(itemId: Long, qty: Int, unitCostPaise: Long, sourceType: String, sourceId: Long, receivedAt: Long) {
        if (qty <= 0) return
        dao.insert(
            ItemCostLot(
                itemId = itemId,
                sourceType = sourceType,
                sourceId = sourceId,
                qtyReceived = qty,
                qtyRemaining = qty,
                unitCostPaise = unitCostPaise,
                receivedAt = receivedAt
            )
        )
    }

    suspend fun importLot(lot: ItemCostLot) {
        dao.insert(lot.copy(id = 0))
    }

    suspend fun discardSource(sourceType: String, sourceId: Long) {
        dao.deleteSource(sourceType, sourceId)
    }
}
