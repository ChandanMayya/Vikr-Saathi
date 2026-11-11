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
    val required: Int,
    val expired: Int = 0
) {
    val shortage: Int
        get() = (required - available).coerceAtLeast(0)
}

class InventoryRepository(
    private val database: AppDatabase,
    private val itemDao: ItemDao,
    private val stockMovementDao: StockMovementDao,
    private val batches: BatchRepository? = null
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
            Result.success(
                applyMovementUnchecked(itemId, delta, type, referenceType, referenceId, note)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Applies a stock movement and throws on failure so an outer Room transaction can roll back.
     */
    suspend fun applyMovementUnchecked(
        itemId: Long,
        delta: Int,
        type: StockMovementType,
        referenceType: String? = null,
        referenceId: Long? = null,
        note: String? = null
    ): Item {
        if (delta == 0) {
            return itemDao.getItemById(itemId) ?: throw IllegalArgumentException("Item not found")
        }
        return database.withTransaction {
            val item = itemDao.getItemById(itemId) ?: throw IllegalArgumentException("Item not found")
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
            item.copy(stockQty = quantityAfter)
        }
    }

    suspend fun reconcileTrackedItem(itemId: Long): Int = batches?.reconcilePositiveGap(itemId) ?: 0

    suspend fun setOpeningStock(itemId: Long, openingQty: Int): Result<Item> {
        val qty = openingQty.coerceAtLeast(0)
        if (qty == 0) {
            val item = itemDao.getItemById(itemId)
                ?: return Result.failure(IllegalArgumentException("Item not found"))
            return Result.success(item)
        }
        return database.withTransaction {
            val result = applyMovement(
                itemId = itemId,
                delta = qty,
                type = StockMovementType.OPENING,
                referenceType = StockReferenceType.MANUAL,
                note = "Opening stock"
            )
            if (result.isSuccess) {
                batches?.addIncoming(
                    itemId = itemId,
                    quantity = qty,
                    sourceType = com.kex.vikrsaathi.domain.inventory.BatchSource.OPENING,
                    movementType = StockMovementType.OPENING,
                    referenceType = StockReferenceType.MANUAL,
                    referenceId = null,
                    note = "Opening stock · expiry unknown"
                )
            }
            result
        }
    }

    suspend fun adjustStock(
        itemId: Long,
        delta: Int,
        note: String? = null,
        batchId: Long? = null,
        lotNumber: String? = null,
        expiryEpochDay: Long? = null,
        expiryUnknown: Boolean = true
    ): Result<Item> = try {
        database.withTransaction {
        val result = applyMovement(
            itemId = itemId,
            delta = delta,
            type = StockMovementType.ADJUSTMENT,
            referenceType = StockReferenceType.MANUAL,
            note = note?.trim()?.ifEmpty { null }
        )
        if (result.isSuccess && delta != 0) {
            batches?.addIncoming(
                itemId = itemId,
                quantity = delta,
                sourceType = com.kex.vikrsaathi.domain.inventory.BatchSource.ADJUSTMENT,
                movementType = StockMovementType.ADJUSTMENT,
                referenceType = StockReferenceType.MANUAL,
                referenceId = null,
                note = note?.trim()?.ifEmpty { null } ?: "Stock adjustment",
                lotNumber = lotNumber,
                expiryEpochDay = expiryEpochDay,
                expiryUnknown = expiryUnknown,
                batchId = batchId
            )
        }
        result
        }
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun importStock(itemId: Long, delta: Int, note: String? = null): Result<Item> {
        if (delta <= 0) {
            val item = itemDao.getItemById(itemId)
                ?: return Result.failure(IllegalArgumentException("Item not found"))
            return Result.success(item)
        }
        return database.withTransaction {
            val result = applyMovement(
                itemId = itemId,
                delta = delta,
                type = StockMovementType.IMPORT,
                referenceType = StockReferenceType.MANUAL,
                note = note?.trim()?.ifEmpty { null } ?: "Excel import"
            )
            if (result.isSuccess) {
                batches?.addIncoming(
                    itemId = itemId,
                    quantity = delta,
                    sourceType = com.kex.vikrsaathi.domain.inventory.BatchSource.IMPORT,
                    movementType = StockMovementType.IMPORT,
                    referenceType = StockReferenceType.MANUAL,
                    referenceId = null,
                    note = "Excel import · expiry unknown"
                )
            }
            result
        }
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
            val tracked = batches?.tracks(itemId) == true
            val allocated = if (tracked && existingBillId != null) {
                batches?.allocatedFor(existingBillId, itemId) ?: 0
            } else {
                0
            }
            val counts = if (tracked) batches?.eligibleAndExpired(itemId) else null
            val available = when {
                counts == null -> item.stockQty + alreadyOnBill
                allocated > 0 -> counts.first + allocated
                else -> counts.first + alreadyOnBill
            }
            val expired = counts?.second ?: 0
            if (needed > available) {
                shortfalls.add(
                    StockShortfall(
                        itemId = itemId,
                        itemName = item.name,
                        available = available,
                        required = needed,
                        expired = expired
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
