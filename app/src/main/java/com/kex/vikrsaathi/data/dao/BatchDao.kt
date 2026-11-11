package com.kex.vikrsaathi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kex.vikrsaathi.data.entity.BillBatchAllocation
import com.kex.vikrsaathi.data.entity.InventoryBatch
import com.kex.vikrsaathi.data.entity.InventoryBatchMovement

@Dao
interface BatchDao {
    @Insert
    suspend fun insertBatch(batch: InventoryBatch): Long

    @Insert
    suspend fun insertMovement(movement: InventoryBatchMovement): Long

    @Insert
    suspend fun insertAllocation(allocation: BillBatchAllocation): Long

    @Query("SELECT * FROM inventory_batches WHERE itemId = :itemId ORDER BY expiryEpochDay IS NULL, expiryEpochDay, createdAt, id")
    suspend fun batchesForItem(itemId: Long): List<InventoryBatch>

    @Query("SELECT * FROM inventory_batches")
    suspend fun allBatches(): List<InventoryBatch>

    @Query("SELECT * FROM inventory_batches WHERE id = :id")
    suspend fun batch(id: Long): InventoryBatch?

    @Query("SELECT COALESCE(SUM(quantityOnHand), 0) FROM inventory_batches WHERE itemId = :itemId")
    suspend fun onHand(itemId: Long): Int

    @Query("UPDATE inventory_batches SET quantityOnHand = quantityOnHand + :delta WHERE id = :id AND quantityOnHand + :delta >= 0")
    suspend fun addQuantity(id: Long, delta: Int): Int

    @Query("UPDATE inventory_batches SET internalRef = :ref WHERE id = :id")
    suspend fun setInternalRef(id: Long, ref: String)

    @Query("UPDATE inventory_batches SET state = :state, expiryEpochDay = :expiry, lotNumber = :lotNumber WHERE id = :id")
    suspend fun updateIdentity(id: Long, state: String, expiry: Long?, lotNumber: String?)

    @Query("SELECT * FROM inventory_batch_movements WHERE batchId = :batchId ORDER BY id DESC")
    suspend fun movements(batchId: Long): List<InventoryBatchMovement>

    @Query("SELECT * FROM inventory_batch_movements")
    suspend fun allMovements(): List<InventoryBatchMovement>

    @Query("SELECT * FROM bill_batch_allocations WHERE billId = :billId")
    suspend fun allocationsForBill(billId: Long): List<BillBatchAllocation>

    @Query("SELECT * FROM bill_batch_allocations")
    suspend fun allAllocations(): List<BillBatchAllocation>

    @Query("DELETE FROM bill_batch_allocations WHERE billId = :billId")
    suspend fun deleteAllocationsForBill(billId: Long)

    @Query("SELECT * FROM inventory_batches WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun batchesForSource(sourceType: String, sourceId: Long): List<InventoryBatch>

    @Query(
        """
        SELECT COALESCE(SUM(quantity), 0) FROM bill_batch_allocations
        WHERE billId = :billId
        AND batchId IN (SELECT id FROM inventory_batches WHERE itemId = :itemId)
        """
    )
    suspend fun allocatedForItem(billId: Long, itemId: Long): Int

    @Query("SELECT COUNT(*) FROM bill_batch_allocations WHERE billId = :billId")
    suspend fun allocationCount(billId: Long): Int
}
