package com.kex.vikrsaathi.data.repository

import com.kex.vikrsaathi.data.dao.BatchDao
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.BillBatchAllocation
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.InventoryBatch
import com.kex.vikrsaathi.data.entity.InventoryBatchMovement
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.StockMovementType
import com.kex.vikrsaathi.data.entity.StockReferenceType
import com.kex.vikrsaathi.domain.finance.FinanceError
import com.kex.vikrsaathi.domain.finance.FinanceException
import com.kex.vikrsaathi.domain.inventory.BatchSource
import com.kex.vikrsaathi.domain.inventory.BatchState
import com.kex.vikrsaathi.domain.inventory.CivilDate
import com.kex.vikrsaathi.domain.inventory.FefoAllocator
import com.kex.vikrsaathi.domain.inventory.SaleLot
import com.kex.vikrsaathi.domain.inventory.SavedLotUse
import java.util.TimeZone

data class ReceiptLot(
    val quantity: Int,
    val lotNumber: String? = null,
    val expiryEpochDay: Long? = null,
    val expiryUnknown: Boolean = false
)

data class ExpiryAlert(
    val batchId: Long,
    val itemId: Long,
    val itemName: String,
    val lotNumber: String?,
    val internalRef: String,
    val expiryEpochDay: Long,
    val quantity: Int,
    val unit: String,
    val expired: Boolean
)

/**
 * Lot quantities sit beside the item total. Tracking off leaves the lots untouched.
 * A tracked item's stock is the sum of its lots plus any quantity received while tracking was off.
 */
class BatchRepository(
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zone: TimeZone = TimeZone.getDefault()
) {
    private val dao: BatchDao get() = database.batchDao()

    fun trackingEnabled(): Boolean = settings.batchTrackingEnabled

    suspend fun tracks(itemId: Long?): Boolean {
        if (itemId == null || !settings.batchTrackingEnabled) return false
        return database.itemDao().getItemById(itemId)?.trackBatches == true
    }

    suspend fun batchesFor(itemId: Long): List<InventoryBatch> = dao.batchesForItem(itemId)

    /**
     * Lots that can be chosen on a bill. Quantity already on [editingBillId] is counted
     * as still available, because saving that bill puts it back before allocating again.
     */
    suspend fun saleLots(itemId: Long, editingBillId: Long?): List<SaleLot> {
        if (!tracks(itemId)) return emptyList()
        val today = CivilDate.today(clock(), zone)
        val held = heldQuantities(editingBillId)
        return dao.batchesForItem(itemId).mapNotNull { batch ->
            val quantity = batch.quantityOnHand + (held[batch.id] ?: 0)
            if (quantity <= 0 || batch.state == BatchState.QUARANTINE) return@mapNotNull null
            val lot = batch.copy(quantityOnHand = quantity).toLot()
            if (!FefoAllocator.isEligible(lot, today)) return@mapNotNull null
            SaleLot(
                id = batch.id,
                lotNumber = batch.lotNumber,
                internalRef = batch.internalRef,
                expiryEpochDay = batch.expiryEpochDay,
                quantityOnHand = quantity
            )
        }
    }

    suspend fun tracesForBill(billId: Long): Map<Long, List<SavedLotUse>> {
        if (billId <= 0L) return emptyMap()
        return groupTraces(dao.allocationsForBill(billId))
    }

    /** Every sale line that already recorded a lot, including after batch tracking is turned off. */
    suspend fun saleTraces(): Map<Long, List<SavedLotUse>> = groupTraces(dao.allAllocations())

    private suspend fun groupTraces(rows: List<BillBatchAllocation>): Map<Long, List<SavedLotUse>> {
        val grouped = linkedMapOf<Long, MutableList<SavedLotUse>>()
        rows.forEach { row ->
            val use = traceOf(row) ?: return@forEach
            grouped.getOrPut(use.first) { mutableListOf() } += use.second
        }
        return grouped
    }

    private suspend fun traceOf(row: BillBatchAllocation): Pair<Long, SavedLotUse>? {
        val billItemId = row.billItemId ?: return null
        val snapshotted = !row.internalRef.isNullOrBlank()
        val batch = if (snapshotted) null else dao.batch(row.batchId)
        val ref = row.internalRef?.takeIf { it.isNotBlank() } ?: batch?.internalRef ?: return null
        return billItemId to SavedLotUse(
            batchId = row.batchId,
            lotNumber = if (snapshotted) row.lotNumber else batch?.lotNumber,
            internalRef = ref,
            expiryEpochDay = if (snapshotted) row.expiryEpochDay else batch?.expiryEpochDay,
            quantity = row.quantity
        )
    }

    private suspend fun heldQuantities(billId: Long?): Map<Long, Int> {
        if (billId == null || billId <= 0L) return emptyMap()
        return dao.allocationsForBill(billId)
            .groupBy { it.batchId }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
    }

    suspend fun movements(batchId: Long): List<InventoryBatchMovement> = dao.movements(batchId)

    suspend fun unassigned(item: Item): Int = item.stockQty - dao.onHand(item.id)

    /** Positive gap becomes one opening lot with no invented batch number or expiry. */
    suspend fun reconcilePositiveGap(itemId: Long): Int {
        val item = database.itemDao().getItemById(itemId) ?: return 0
        val gap = item.stockQty - dao.onHand(itemId)
        if (gap <= 0) return 0
        createBatch(
            itemId = itemId,
            quantity = gap,
            lotNumber = null,
            expiryEpochDay = null,
            state = BatchState.UNKNOWN,
            sourceType = BatchSource.OPENING,
            sourceId = null,
            type = StockMovementType.OPENING,
            referenceType = StockReferenceType.MANUAL,
            referenceId = null,
            note = "Opening stock · expiry unknown"
        )
        return gap
    }

    suspend fun receive(
        itemId: Long,
        acceptedBase: Int,
        lots: List<ReceiptLot>,
        receiptId: Long
    ) {
        if (!tracks(itemId) || acceptedBase <= 0) return
        if (lots.isEmpty()) throw FinanceException(FinanceError.BATCH_REQUIRED)
        if (lots.any { it.quantity <= 0 }) throw FinanceException(FinanceError.BATCH_QTY_MISMATCH)
        if (lots.sumOf { it.quantity } != acceptedBase) throw FinanceException(FinanceError.BATCH_QTY_MISMATCH)
        lots.forEach { lot ->
            if (!lot.expiryUnknown && lot.expiryEpochDay == null) {
                throw FinanceException(FinanceError.BATCH_EXPIRY_REQUIRED)
            }
            createBatch(
                itemId = itemId,
                quantity = lot.quantity,
                lotNumber = lot.lotNumber?.trim()?.ifEmpty { null },
                expiryEpochDay = if (lot.expiryUnknown) null else lot.expiryEpochDay,
                state = if (lot.expiryUnknown) BatchState.UNKNOWN else BatchState.ACTIVE,
                sourceType = BatchSource.GOODS_RECEIPT,
                sourceId = receiptId,
                type = StockMovementType.PURCHASE_RECEIPT,
                referenceType = StockReferenceType.GOODS_RECEIPT,
                referenceId = receiptId,
                note = "Goods receipt"
            )
        }
    }

    suspend fun addIncoming(
        itemId: Long,
        quantity: Int,
        sourceType: String,
        movementType: StockMovementType,
        referenceType: String?,
        referenceId: Long?,
        note: String,
        lotNumber: String? = null,
        expiryEpochDay: Long? = null,
        expiryUnknown: Boolean = true,
        batchId: Long? = null
    ) {
        if (!tracks(itemId) || quantity == 0) return
        if (quantity < 0) {
            val target = batchId ?: throw FinanceException(FinanceError.BATCH_REQUIRED)
            change(target, quantity, movementType, referenceType, referenceId, note)
            return
        }
        if (batchId != null) {
            change(batchId, quantity, movementType, referenceType, referenceId, note)
            return
        }
        if (!expiryUnknown && expiryEpochDay == null) throw FinanceException(FinanceError.BATCH_EXPIRY_REQUIRED)
        createBatch(
            itemId = itemId,
            quantity = quantity,
            lotNumber = lotNumber?.trim()?.ifEmpty { null },
            expiryEpochDay = if (expiryUnknown) null else expiryEpochDay,
            state = if (expiryUnknown) BatchState.UNKNOWN else BatchState.ACTIVE,
            sourceType = sourceType,
            sourceId = referenceId,
            type = movementType,
            referenceType = referenceType,
            referenceId = referenceId,
            note = note
        )
    }

    /** Puts a return on a chosen lot, or holds it for inspection. Returns the lot id, or 0 when tracking is off. */
    suspend fun placeReturn(itemId: Long, quantity: Int, returnId: Long, batchId: Long?): Long {
        if (!tracks(itemId) || quantity <= 0) return 0L
        if (batchId != null) {
            val batch = dao.batch(batchId) ?: throw FinanceException(FinanceError.BATCH_REQUIRED)
            if (batch.itemId != itemId) throw FinanceException(FinanceError.BATCH_REQUIRED)
            change(batchId, quantity, StockMovementType.SALES_RETURN, StockReferenceType.SALES_RETURN, returnId, "Return to batch")
            return batchId
        }
        return createBatch(
            itemId = itemId,
            quantity = quantity,
            lotNumber = null,
            expiryEpochDay = null,
            state = BatchState.QUARANTINE,
            sourceType = BatchSource.SALES_RETURN,
            sourceId = returnId,
            type = StockMovementType.SALES_RETURN,
            referenceType = StockReferenceType.SALES_RETURN,
            referenceId = returnId,
            note = "Return held for inspection"
        )
    }

    suspend fun reverseRestock(batchId: Long?, quantity: Int, returnId: Long, itemId: Long) {
        if (quantity <= 0 || !tracks(itemId)) return
        var left = quantity
        val candidates = buildList {
            if (batchId != null) dao.batch(batchId)?.let { add(it) }
            addAll(
                dao.batchesForSource(BatchSource.SALES_RETURN, returnId)
                    .filter { it.itemId == itemId && it.id != batchId }
            )
        }
        candidates.forEach { batch ->
            if (left == 0) return@forEach
            val take = minOf(batch.quantityOnHand, left)
            if (take > 0) {
                change(
                    batch.id,
                    -take,
                    StockMovementType.ADJUSTMENT,
                    StockReferenceType.SALES_RETURN,
                    returnId,
                    "Reversed sales return"
                )
                left -= take
            }
        }
        if (candidates.isEmpty()) return
        if (left > 0) throw FinanceException(FinanceError.BATCH_SHORT)
    }

    suspend fun reverseReturn(returnId: Long) {
        dao.batchesForSource(BatchSource.SALES_RETURN, returnId).forEach { batch ->
            if (batch.quantityOnHand == 0) return@forEach
            change(
                batch.id,
                -batch.quantityOnHand,
                StockMovementType.ADJUSTMENT,
                StockReferenceType.SALES_RETURN,
                returnId,
                "Reversed sales return"
            )
        }
    }

    suspend fun reverseReceipt(receiptId: Long) {
        dao.batchesForSource(BatchSource.GOODS_RECEIPT, receiptId).forEach { batch ->
            if (batch.quantityOnHand == 0) return@forEach
            change(
                batch.id,
                -batch.quantityOnHand,
                StockMovementType.PURCHASE_REVERSAL,
                StockReferenceType.GOODS_RECEIPT,
                receiptId,
                "Reversed goods receipt"
            )
        }
    }

    suspend fun eligibleAndExpired(itemId: Long, extraEligible: Int = 0): Pair<Int, Int> {
        val today = CivilDate.today(clock(), zone)
        val lots = dao.batchesForItem(itemId).map { it.toLot() }
        val plan = FefoAllocator.plan(lots, 0, today)
        return plan.eligible + extraEligible to plan.expired
    }

    suspend fun syncBill(
        billId: Long,
        isEdit: Boolean,
        oldQuantities: Map<Long, Int>,
        chosenBatchIds: List<Long?> = emptyList()
    ) {
        if (!settings.batchTrackingEnabled) return
        val lines = database.billItemDao().getItemsForBill(billId)
        val previous = dao.allocationsForBill(billId)
        if (previous.isNotEmpty()) {
            restore(previous, billId)
            dao.deleteAllocationsForBill(billId)
            allocate(billId, lines, chosenBatchIds)
            return
        }
        if (!isEdit) {
            allocate(billId, lines, chosenBatchIds)
            return
        }
        val newQty = lines.filter { it.itemId != null }.groupBy { it.itemId!! }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
        val itemIds = (newQty.keys + oldQuantities.keys).distinct()
        itemIds.forEach { itemId ->
            if (!tracks(itemId)) return@forEach
            val extra = (newQty[itemId] ?: 0) - (oldQuantities[itemId] ?: 0)
            if (extra > 0) {
                val choice = lines.indexOfFirst { it.itemId == itemId }
                    .takeIf { it >= 0 }
                    ?.let { chosenBatchIds.getOrNull(it) }
                allocateQuantity(billId, null, itemId, extra, choice)
            }
        }
    }

    suspend fun restoreBill(billId: Long) {
        val previous = dao.allocationsForBill(billId)
        if (previous.isEmpty()) return
        restore(previous, billId)
        dao.deleteAllocationsForBill(billId)
    }

    suspend fun alerts(): List<ExpiryAlert> {
        if (!settings.batchTrackingEnabled) return emptyList()
        val today = CivilDate.today(clock(), zone)
        val window = settings.expiryAlertDays
        val items = database.itemDao().getAllItemsSync().associateBy { it.id }
        return dao.allBatches().mapNotNull { batch ->
            val expiry = batch.expiryEpochDay ?: return@mapNotNull null
            if (batch.quantityOnHand <= 0 || batch.state == BatchState.QUARANTINE) return@mapNotNull null
            val expired = expiry < today || batch.state == BatchState.EXPIRED
            val soon = CivilDate.inAlertWindow(expiry, today, window)
            if (!expired && !soon) return@mapNotNull null
            val item = items[batch.itemId]
            ExpiryAlert(
                batchId = batch.id,
                itemId = batch.itemId,
                itemName = item?.name ?: "Item",
                lotNumber = batch.lotNumber,
                internalRef = batch.internalRef,
                expiryEpochDay = expiry,
                quantity = batch.quantityOnHand,
                unit = item?.stockUnit().orEmpty(),
                expired = expired
            )
        }.sortedBy { it.expiryEpochDay }
    }

    suspend fun releaseQuarantine(batchId: Long, expiryEpochDay: Long?, expiryUnknown: Boolean, lotNumber: String?) {
        val batch = dao.batch(batchId) ?: throw FinanceException(FinanceError.BATCH_REQUIRED)
        if (batch.state != BatchState.QUARANTINE || batch.quantityOnHand <= 0) {
            throw FinanceException(FinanceError.BATCH_REQUIRED)
        }
        if (!expiryUnknown && expiryEpochDay == null) throw FinanceException(FinanceError.BATCH_EXPIRY_REQUIRED)
        val qty = batch.quantityOnHand
        change(batchId, -qty, StockMovementType.ADJUSTMENT, StockReferenceType.MANUAL, null, "Released from inspection")
        createBatch(
            itemId = batch.itemId,
            quantity = qty,
            lotNumber = lotNumber?.trim()?.ifEmpty { null },
            expiryEpochDay = if (expiryUnknown) null else expiryEpochDay,
            state = if (expiryUnknown) BatchState.UNKNOWN else BatchState.ACTIVE,
            sourceType = batch.sourceType,
            sourceId = batch.sourceId,
            type = StockMovementType.ADJUSTMENT,
            referenceType = StockReferenceType.MANUAL,
            referenceId = null,
            note = "Released from inspection"
        )
    }

    private suspend fun allocate(billId: Long, lines: List<BillItem>, chosenBatchIds: List<Long?>) {
        lines.forEachIndexed { index, line ->
            val itemId = line.itemId ?: return@forEachIndexed
            if (!tracks(itemId) || line.quantity <= 0) return@forEachIndexed
            allocateQuantity(billId, line.id, itemId, line.quantity, chosenBatchIds.getOrNull(index))
        }
    }

    private suspend fun allocateQuantity(
        billId: Long,
        billItemId: Long?,
        itemId: Long,
        quantity: Int,
        preferredBatchId: Long? = null
    ) {
        if (preferredBatchId != null && takePreferred(billId, billItemId, itemId, quantity, preferredBatchId)) {
            return
        }
        val today = CivilDate.today(clock(), zone)
        val lots = dao.batchesForItem(itemId).map { it.toLot() }
        val plan = FefoAllocator.plan(lots, quantity, today)
        plan.taken.forEach { take ->
            recordSale(billId, billItemId, take.lotId, take.quantity)
        }
    }

    /** Takes only the chosen lot. Other lots stay untouched so the expiry on the bill is that lot. */
    private suspend fun takePreferred(
        billId: Long,
        billItemId: Long?,
        itemId: Long,
        quantity: Int,
        preferredBatchId: Long
    ): Boolean {
        val batch = dao.batch(preferredBatchId) ?: return false
        if (batch.itemId != itemId) return false
        val today = CivilDate.today(clock(), zone)
        if (!FefoAllocator.isEligible(batch.toLot(), today)) return false
        val use = minOf(batch.quantityOnHand, quantity)
        if (use > 0) recordSale(billId, billItemId, preferredBatchId, use)
        return true
    }

    private suspend fun recordSale(billId: Long, billItemId: Long?, batchId: Long, quantity: Int) {
        val batch = dao.batch(batchId)
        change(batchId, -quantity, StockMovementType.SALE, StockReferenceType.BILL, billId, "Sale")
        dao.insertAllocation(
            BillBatchAllocation(
                billId = billId,
                billItemId = billItemId,
                batchId = batchId,
                quantity = quantity,
                lotNumber = batch?.lotNumber,
                internalRef = batch?.internalRef,
                expiryEpochDay = batch?.expiryEpochDay
            )
        )
    }

    private suspend fun restore(previous: List<BillBatchAllocation>, billId: Long) {
        previous.forEach { row ->
            if (row.quantity <= 0) return@forEach
            change(
                row.batchId,
                row.quantity,
                StockMovementType.SALE_REVERSAL,
                StockReferenceType.BILL,
                billId,
                "Sale reversal"
            )
        }
    }

    suspend fun allocatedFor(billId: Long, itemId: Long): Int =
        if (billId == 0L) 0 else dao.allocatedForItem(billId, itemId)

    suspend fun hasAllocations(billId: Long): Boolean = dao.allocationCount(billId) > 0

    suspend fun importBatch(batch: InventoryBatch): Long = dao.insertBatch(batch)

    suspend fun importMovement(movement: InventoryBatchMovement) {
        dao.insertMovement(movement)
    }

    suspend fun importAllocation(allocation: BillBatchAllocation) {
        dao.insertAllocation(allocation)
    }

    data class GapRow(val itemId: Long, val name: String, val stockQty: Int, val batchQty: Int) {
        val gap: Int get() = stockQty - batchQty
    }

    suspend fun gaps(): List<GapRow> {
        return database.itemDao().getAllItemsSync()
            .filter { it.trackBatches }
            .map { item -> GapRow(item.id, item.name, item.stockQty, dao.onHand(item.id)) }
            .filter { it.gap != 0 }
    }

    suspend fun itemIdsForFilter(filter: String): Set<Long> {
        val today = CivilDate.today(clock(), zone)
        val window = settings.expiryAlertDays
        return dao.allBatches().filter { batch ->
            if (batch.quantityOnHand <= 0) return@filter false
            val expired = FefoAllocator.isExpired(
                FefoAllocator.Lot(batch.id, batch.expiryEpochDay, batch.quantityOnHand, batch.state, batch.createdAt),
                today
            )
            when (filter) {
                "expired" -> expired
                "soon" -> !expired && batch.expiryEpochDay != null &&
                    batch.state != BatchState.QUARANTINE &&
                    CivilDate.inAlertWindow(batch.expiryEpochDay, today, window)
                "unknown" -> batch.state == BatchState.UNKNOWN
                else -> true
            }
        }.map { it.itemId }.toSet()
    }

    private suspend fun createBatch(
        itemId: Long,
        quantity: Int,
        lotNumber: String?,
        expiryEpochDay: Long?,
        state: String,
        sourceType: String,
        sourceId: Long?,
        type: StockMovementType,
        referenceType: String?,
        referenceId: Long?,
        note: String
    ): Long {
        val now = clock()
        val id = dao.insertBatch(
            InventoryBatch(
                itemId = itemId,
                lotNumber = lotNumber,
                internalRef = "PENDING",
                expiryEpochDay = expiryEpochDay,
                quantityOnHand = quantity,
                createdAt = now,
                sourceType = sourceType,
                sourceId = sourceId,
                state = state
            )
        )
        dao.setInternalRef(id, "L-$id")
        dao.insertMovement(
            InventoryBatchMovement(
                batchId = id,
                delta = quantity,
                quantityAfter = quantity,
                type = type.name,
                referenceType = referenceType,
                referenceId = referenceId,
                note = note,
                createdAt = now
            )
        )
        return id
    }

    private suspend fun change(
        batchId: Long,
        delta: Int,
        type: StockMovementType,
        referenceType: String?,
        referenceId: Long?,
        note: String
    ) {
        if (delta == 0) return
        val updated = dao.addQuantity(batchId, delta)
        if (updated == 0) throw FinanceException(FinanceError.BATCH_SHORT)
        val after = dao.batch(batchId)?.quantityOnHand ?: throw FinanceException(FinanceError.BATCH_SHORT)
        dao.insertMovement(
            InventoryBatchMovement(
                batchId = batchId,
                delta = delta,
                quantityAfter = after,
                type = type.name,
                referenceType = referenceType,
                referenceId = referenceId,
                note = note,
                createdAt = clock()
            )
        )
    }

    private fun InventoryBatch.toLot() = FefoAllocator.Lot(
        id = id,
        expiryEpochDay = expiryEpochDay,
        quantity = quantityOnHand,
        state = state,
        createdAt = createdAt
    )
}
