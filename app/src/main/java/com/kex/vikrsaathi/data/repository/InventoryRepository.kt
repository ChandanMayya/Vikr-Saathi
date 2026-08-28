package com.kex.vikrsaathi.data.repository

import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.kex.vikrsaathi.data.dao.ItemDao
import com.kex.vikrsaathi.data.dao.StockMovementDao
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.StockMovement
import com.kex.vikrsaathi.data.entity.StockMovementType
import com.kex.vikrsaathi.data.entity.StockReferenceType
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.domain.inventory.StockDeltaCalculator

data class StockShortfall(
    val itemId: Long,
    val itemName: String,
    val available: Int,
    val required: Int
) {
    val shortage: Int
        get() = (required - available).coerceAtLeast(0)
}

class InventoryRepository(
    private val database: AppDatabase,
    private val itemDao: ItemDao,
    private val stockMovementDao: StockMovementDao
) {

    suspend fun applyMovement(
        itemId: Long,
        delta: Int,
        type: StockMovementType,
        referenceType: String? = null,
        referenceId: Long? = null,
        note: String? = null
    ): Result<Item> {
        if (delta == 0) {
            val item = itemDao.getItemById(itemId)
                ?: return Result.failure(IllegalArgumentException("Item not found"))
            return Result.success(item)
        }
        return try {
            database.withTransaction {
                val item = itemDao.getItemById(itemId)
                    ?: throw IllegalArgumentException("Item not found")
                val quantityAfter = item.stockQty + delta
                itemDao.updateStockQty(itemId, quantityAfter)
                stockMovementDao.insert(
                    StockMovement(
                        itemId = itemId,
                        delta = delta,
                        quantityAfter = quantityAfter,
                        type = type.name,
                        referenceType = referenceType,
                        referenceId = referenceId,
                        note = note
                    )
                )
                Result.success(item.copy(stockQty = quantityAfter))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setOpeningStock(itemId: Long, openingQty: Int): Result<Item> {
        val qty = openingQty.coerceAtLeast(0)
        if (qty == 0) {
            val item = itemDao.getItemById(itemId)
                ?: return Result.failure(IllegalArgumentException("Item not found"))
            return Result.success(item)
        }
        return applyMovement(
            itemId = itemId,
            delta = qty,
            type = StockMovementType.OPENING,
            referenceType = StockReferenceType.MANUAL,
            note = "Opening stock"
        )
    }

    suspend fun adjustStock(itemId: Long, delta: Int, note: String? = null): Result<Item> =
        applyMovement(
            itemId = itemId,
            delta = delta,
            type = StockMovementType.ADJUSTMENT,
            referenceType = StockReferenceType.MANUAL,
            note = note?.trim()?.ifEmpty { null }
        )

    suspend fun importStock(itemId: Long, delta: Int, note: String? = null): Result<Item> {
        if (delta <= 0) {
            val item = itemDao.getItemById(itemId)
                ?: return Result.failure(IllegalArgumentException("Item not found"))
            return Result.success(item)
        }
        return applyMovement(
            itemId = itemId,
            delta = delta,
            type = StockMovementType.IMPORT,
            referenceType = StockReferenceType.MANUAL,
            note = note?.trim()?.ifEmpty { null } ?: "Excel import"
        )
    }

    /**
     * Available stock for bill validation. When editing [existingBillId], quantities
     * already on that bill are treated as still available.
     */
    suspend fun findShortfalls(
        lineItems: List<BillLineItem>,
        existingBillId: Long?,
        oldBillQuantities: Map<Long, Int> = emptyMap()
    ): List<StockShortfall> {
        val required = StockDeltaCalculator.aggregateQuantities(
            lineItems.map { it.itemId to it.quantity }
        )
        if (required.isEmpty()) return emptyList()

        val shortfalls = mutableListOf<StockShortfall>()
        for ((itemId, needed) in required) {
            val item = itemDao.getItemById(itemId) ?: continue
            val alreadyOnBill = if (existingBillId != null) oldBillQuantities[itemId] ?: 0 else 0
            val available = item.stockQty + alreadyOnBill
            if (needed > available) {
                shortfalls.add(
                    StockShortfall(
                        itemId = itemId,
                        itemName = item.name,
                        available = available,
                        required = needed
                    )
                )
            }
        }
        return shortfalls
    }

    suspend fun applyBillStockChanges(
        billId: Long,
        oldQuantities: Map<Long, Int>,
        newLineItems: List<BillLineItem>
    ) {
        val newQuantities = StockDeltaCalculator.aggregateQuantities(
            newLineItems.map { it.itemId to it.quantity }
        )
        val deltas = StockDeltaCalculator.netStockDeltas(oldQuantities, newQuantities)
        if (deltas.isEmpty()) return

        database.withTransaction {
            for ((itemId, delta) in deltas) {
                val item = itemDao.getItemById(itemId) ?: continue
                val quantityAfter = item.stockQty + delta
                val type = when {
                    delta < 0 -> StockMovementType.SALE
                    else -> StockMovementType.SALE_REVERSAL
                }
                itemDao.updateStockQty(itemId, quantityAfter)
                stockMovementDao.insert(
                    StockMovement(
                        itemId = itemId,
                        delta = delta,
                        quantityAfter = quantityAfter,
                        type = type.name,
                        referenceType = StockReferenceType.BILL,
                        referenceId = billId
                    )
                )
            }
        }
    }

    suspend fun reverseBillStock(billId: Long, quantities: Map<Long, Int>) {
        val deltas = StockDeltaCalculator.reversalDeltas(quantities)
        if (deltas.isEmpty()) return
        database.withTransaction {
            for ((itemId, qty) in deltas) {
                val item = itemDao.getItemById(itemId) ?: continue
                val quantityAfter = item.stockQty + qty
                itemDao.updateStockQty(itemId, quantityAfter)
                stockMovementDao.insert(
                    StockMovement(
                        itemId = itemId,
                        delta = qty,
                        quantityAfter = quantityAfter,
                        type = StockMovementType.SALE_REVERSAL.name,
                        referenceType = StockReferenceType.BILL,
                        referenceId = billId
                    )
                )
            }
        }
    }

    suspend fun getMovementsForItem(itemId: Long, limit: Int = 50): List<StockMovement> =
        stockMovementDao.getForItem(itemId, limit)

    fun observeMovementsForItem(itemId: Long, limit: Int = 50): LiveData<List<StockMovement>> =
        stockMovementDao.observeForItem(itemId, limit)

    suspend fun getAllMovementsSync(): List<StockMovement> = stockMovementDao.getAllSync()

    suspend fun importMovement(movement: StockMovement) {
        stockMovementDao.insert(movement.copy(id = 0))
    }

    suspend fun getLowStockItems(threshold: Int): List<Item> =
        itemDao.getLowStockItems(threshold)

    suspend fun countLowStockItems(threshold: Int): Int =
        itemDao.countLowStockItems(threshold)
}
