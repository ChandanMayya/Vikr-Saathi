package com.kex.vikrsaathi.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.domain.finance.BillPaymentRequest
import com.kex.vikrsaathi.domain.finance.FinanceError
import com.kex.vikrsaathi.domain.finance.FinanceException
import com.kex.vikrsaathi.domain.finance.RefundMethod
import com.kex.vikrsaathi.domain.inventory.BatchState
import com.kex.vikrsaathi.domain.inventory.CivilDate
import com.kex.vikrsaathi.util.InventoryMode
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BatchTrackingTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var batches: BatchRepository
    private lateinit var inventory: InventoryRepository
    private lateinit var bills: BillRepository
    private lateinit var vendors: VendorRepository
    private lateinit var finance: CustomerFinanceRepository
    private val today = CivilDate.of(2026, 6, 15)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("vikr_saathi_settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Executors.newSingleThreadExecutor())
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        settings = SettingsRepository(context)
        settings.inventoryMode = InventoryMode.BLOCK
        settings.batchTrackingEnabled = true
        settings.expiryAlertDays = 30
        settings.invoiceCounter = 1
        settings.invoicePrefix = "INV"
        settings.invoiceSeparator = "-"
        batches = BatchRepository(
            db,
            settings,
            clock = { today * 86_400_000L + 12 * 3_600_000L },
            zone = TimeZone.getTimeZone("UTC")
        )
        inventory = InventoryRepository(db, db.itemDao(), db.stockMovementDao(), batches)
        bills = BillRepository(
            database = db,
            billDao = db.billDao(),
            billItemDao = db.billItemDao(),
            billPaymentDao = db.billPaymentDao(),
            customerLedgerDao = db.customerLedgerDao(),
            settingsRepository = settings,
            inventoryRepository = inventory,
            batches = batches
        )
        vendors = VendorRepository(
            database = db,
            vendorDao = db.vendorDao(),
            purchaseDao = db.purchaseDao(),
            itemDao = db.itemDao(),
            inventoryRepository = inventory,
            settingsRepository = settings,
            batches = batches
        )
        finance = CustomerFinanceRepository(
            database = db,
            ledgerDao = db.customerLedgerDao(),
            billDao = db.billDao(),
            billItemDao = db.billItemDao(),
            billPaymentDao = db.billPaymentDao(),
            inventoryRepository = inventory,
            billRepository = bills,
            batches = batches
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun untrackedItemsKeepAggregateStock() = runBlocking {
        val rice = db.itemDao().insert(Item(name = "Rice", mrp = 10.0, stockQty = 8, trackBatches = false))
        sell(rice, 3)
        assertEquals(5, db.itemDao().getItemById(rice)!!.stockQty)
        assertTrue(batches.batchesFor(rice).isEmpty())
    }

    @Test
    fun fefoSkipsExpiredAndRestoresTheSameLots() = runBlocking {
        val milk = tracked("Milk")
        receive(milk, listOf(
            ReceiptLot(4, "OLD", today - 1, false),
            ReceiptLot(2, "SOON", today + 2, false),
            ReceiptLot(5, "LATER", today + 20, false)
        ))
        val billId = sell(milk, 3)
        val lots = batches.batchesFor(milk).associateBy { it.lotNumber }
        assertEquals(4, lots.getValue("OLD").quantityOnHand)
        assertEquals(0, lots.getValue("SOON").quantityOnHand)
        assertEquals(4, lots.getValue("LATER").quantityOnHand)
        assertEquals(8, db.itemDao().getItemById(milk)!!.stockQty)
        bills.deleteBill(db.billDao().getBillById(billId)!!)
        val restored = batches.batchesFor(milk).associateBy { it.lotNumber }
        assertEquals(2, restored.getValue("SOON").quantityOnHand)
        assertEquals(5, restored.getValue("LATER").quantityOnHand)
        assertEquals(11, db.itemDao().getItemById(milk)!!.stockQty)
    }

    @Test
    fun blockUsesEligibleQuantityAndReportsExpired() = runBlocking {
        val milk = tracked("Milk")
        receive(milk, listOf(
            ReceiptLot(4, "OLD", today - 2, false),
            ReceiptLot(1, "OK", today + 3, false)
        ))
        val error = runCatching { sell(milk, 2) }.exceptionOrNull() as InsufficientStockException
        assertEquals(1, error.shortfalls.single().available)
        assertEquals(4, error.shortfalls.single().expired)
        assertEquals(5, db.itemDao().getItemById(milk)!!.stockQty)
    }

    @Test
    fun warnCanLeaveTheShortageUnassigned() = runBlocking {
        settings.inventoryMode = InventoryMode.WARN
        val milk = tracked("Milk")
        receive(milk, listOf(ReceiptLot(1, "OK", today + 1, false)))
        sell(milk, 3, acknowledged = true)
        assertEquals(-2, db.itemDao().getItemById(milk)!!.stockQty)
        assertEquals(0, batches.batchesFor(milk).sumOf { it.quantityOnHand })
    }

    @Test
    fun chosenBatchIsTheOnlyLotReduced() = runBlocking {
        val milk = tracked("Milk")
        receive(milk, listOf(
            ReceiptLot(4, "SOON", today + 2, false),
            ReceiptLot(5, "LATER", today + 20, false)
        ))
        val later = batches.batchesFor(milk).single { it.lotNumber == "LATER" }
        val item = db.itemDao().getItemById(milk)!!
        val billId = bills.saveBill(
            customerId = null,
            lineItems = listOf(
                BillLineItem(
                    itemId = milk,
                    name = item.name,
                    mrp = 10.0,
                    discount = 0.0,
                    quantity = 2,
                    batchId = later.id
                )
            ),
            payment = BillPaymentRequest.KeepLegacy
        )
        val lots = batches.batchesFor(milk).associateBy { it.lotNumber }
        assertEquals(4, lots.getValue("SOON").quantityOnHand)
        assertEquals(3, lots.getValue("LATER").quantityOnHand)
        val trace = batches.tracesForBill(billId).values.single().single()
        assertEquals(later.id, trace.batchId)
        assertEquals(2, trace.quantity)
        assertEquals(today + 20, trace.expiryEpochDay)
        val saved = db.batchDao().allocationsForBill(billId).single()
        assertEquals("LATER", saved.lotNumber)
        assertEquals(today + 20, saved.expiryEpochDay)
        assertEquals(
            "LATER · ${CivilDate.format(today + 20)}",
            bills.batchNotesForPrint(billId, "Expiry unknown").values.single()
        )
    }

    @Test
    fun trackingOffLeavesChosenBatchUntouched() = runBlocking {
        val milk = tracked("Milk")
        receive(milk, listOf(ReceiptLot(4, "SOON", today + 2, false)))
        val lot = batches.batchesFor(milk).single()
        settings.batchTrackingEnabled = false
        sellChosen(milk, lot.id, 1)
        assertEquals(4, batches.batchesFor(milk).single().quantityOnHand)
        assertEquals(3, db.itemDao().getItemById(milk)!!.stockQty)
    }

    @Test
    fun editRestoresTheOriginalLotsBeforeAllocatingAgain() = runBlocking {
        val milk = tracked("Milk")
        receive(milk, listOf(
            ReceiptLot(2, "SOON", today + 1, false),
            ReceiptLot(4, "LATER", today + 15, false)
        ))
        val billId = sell(milk, 3)
        sellEdit(billId, milk, 1)
        val lots = batches.batchesFor(milk).associateBy { it.lotNumber }
        assertEquals(1, lots.getValue("SOON").quantityOnHand)
        assertEquals(4, lots.getValue("LATER").quantityOnHand)
        assertEquals(5, db.itemDao().getItemById(milk)!!.stockQty)
    }

    @Test
    fun receiptSplitMustMatchAcceptedQuantity() = runBlocking {
        val milk = tracked("Milk")
        val error = runCatching {
            receive(milk, listOf(ReceiptLot(2, "A", today + 5, false)), received = 4)
        }.exceptionOrNull() as FinanceException
        assertEquals(FinanceError.BATCH_QTY_MISMATCH, error.code)
        assertEquals(0, db.itemDao().getItemById(milk)!!.stockQty)
    }

    @Test
    fun adjustmentDecreaseNeedsALotAndIncreaseCanBeUnknown() = runBlocking {
        val milk = tracked("Milk")
        receive(milk, listOf(ReceiptLot(3, "A", today + 4, false)))
        val missing = inventory.adjustStock(milk, -1, expiryUnknown = false)
        assertTrue(missing.isFailure)
        val lot = batches.batchesFor(milk).single()
        inventory.adjustStock(milk, -1, batchId = lot.id).getOrThrow()
        inventory.adjustStock(milk, 2, expiryUnknown = true).getOrThrow()
        assertEquals(4, db.itemDao().getItemById(milk)!!.stockQty)
        assertEquals(4, batches.batchesFor(milk).sumOf { it.quantityOnHand })
        assertTrue(batches.batchesFor(milk).any { it.state == BatchState.UNKNOWN && it.lotNumber == null })
    }

    @Test
    fun returnIsQuarantinedAndVoidedOnce() = runBlocking {
        settings.inventoryMode = InventoryMode.OFF
        val milk = tracked("Milk")
        receive(milk, listOf(ReceiptLot(2, "A", today + 4, false)))
        val billId = sell(milk, 2)
        val lineId = db.billItemDao().getItemsForBill(billId).single().id
        val posted = finance.postBillReturn(
            billId,
            listOf(BillReturnLineInput(lineId, 1, true)),
            RefundMethod.CASH,
            "",
            null,
            20L,
            "Owner"
        )
        val held = batches.batchesFor(milk).single { it.state == BatchState.QUARANTINE }
        assertEquals(1, held.quantityOnHand)
        assertEquals(0, batches.eligibleAndExpired(milk).first)
        finance.voidSalesReturn(posted.returnId, "Damaged", 21L, "Owner")
        assertEquals(0, batches.batchesFor(milk).single { it.id == held.id }.quantityOnHand)
        assertEquals(0, db.itemDao().getItemById(milk)!!.stockQty)
        finance.reverseSalesReturnStock()
        assertEquals(0, db.itemDao().getItemById(milk)!!.stockQty)
    }

    @Test
    fun reconciliationDoesNotInventExpiry() = runBlocking {
        val milk = db.itemDao().insert(Item(name = "Milk", mrp = 10.0, stockQty = 6, trackBatches = true))
        val assigned = batches.reconcilePositiveGap(milk)
        assertEquals(6, assigned)
        val lot = batches.batchesFor(milk).single()
        assertNull(lot.lotNumber)
        assertNull(lot.expiryEpochDay)
        assertEquals(BatchState.UNKNOWN, lot.state)
        assertEquals(0, batches.gaps().size)
    }

    @Test
    fun olderBackupWithoutBatchesImportsAsEmpty() {
        val root = JSONObject().put("schemaVersion", 5).put("items", org.json.JSONArray())
        assertTrue(com.kex.vikrsaathi.data.backup.BackupJsonCodec.parseBatches(root).isEmpty())
        assertTrue(com.kex.vikrsaathi.data.backup.BackupSchema.SUPPORTED.contains(5))
    }

    private suspend fun tracked(name: String): Long =
        db.itemDao().insert(Item(name = name, mrp = 10.0, stockQty = 0, trackBatches = true))

    private suspend fun receive(itemId: Long, lots: List<ReceiptLot>, received: Int = lots.sumOf { it.quantity }) {
        val vendorId = db.vendorDao().insert(Vendor(name = "Vendor"))
        vendors.saveReceipt(
            vendorId = vendorId,
            orderId = null,
            notes = "",
            receivedAt = 1L,
            lines = listOf(
                ReceiptLineInput(
                    orderLineId = null,
                    itemId = itemId,
                    itemName = "Item",
                    orderedQty = 0,
                    receivedQty = received,
                    damagedQty = 0,
                    rejectedQty = 0,
                    lots = lots
                )
            )
        )
    }

    private suspend fun sellChosen(itemId: Long, batchId: Long, qty: Int): Long {
        val item = db.itemDao().getItemById(itemId)!!
        return bills.saveBill(
            customerId = null,
            lineItems = listOf(
                BillLineItem(
                    itemId = itemId,
                    name = item.name,
                    mrp = 10.0,
                    discount = 0.0,
                    quantity = qty,
                    batchId = batchId
                )
            ),
            payment = BillPaymentRequest.KeepLegacy
        )
    }

    private suspend fun sell(itemId: Long, qty: Int, acknowledged: Boolean = false): Long {
        val item = db.itemDao().getItemById(itemId)!!
        return bills.saveBill(
            customerId = null,
            lineItems = listOf(BillLineItem(itemId = itemId, name = item.name, mrp = 10.0, discount = 0.0, quantity = qty)),
            payment = BillPaymentRequest.KeepLegacy,
            shortageAcknowledged = acknowledged
        )
    }

    private suspend fun sellEdit(billId: Long, itemId: Long, qty: Int) {
        val item = db.itemDao().getItemById(itemId)!!
        bills.saveBill(
            customerId = null,
            lineItems = listOf(BillLineItem(itemId = itemId, name = item.name, mrp = 10.0, discount = 0.0, quantity = qty)),
            existingBillId = billId,
            payment = BillPaymentRequest.LeaveUnchanged
        )
    }
}
