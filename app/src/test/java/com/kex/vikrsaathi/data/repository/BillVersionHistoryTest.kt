package com.kex.vikrsaathi.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.StockMovementType
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.domain.bill.BillVersionDiffer
import com.kex.vikrsaathi.domain.finance.BillPaymentRequest
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.domain.finance.PaymentPortion
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.util.InventoryMode
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BillVersionHistoryTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repository: BillRepository
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Executors.newSingleThreadExecutor())
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        settings = SettingsRepository(context)
        settings.inventoryMode = InventoryMode.OFF
        settings.invoiceCounter = 1
        settings.invoicePrefix = "INV"
        settings.invoiceSeparator = "-"
        val inventory = InventoryRepository(db, db.itemDao(), db.stockMovementDao())
        repository = BillRepository(
            database = db,
            billDao = db.billDao(),
            billItemDao = db.billItemDao(),
            billPaymentDao = db.billPaymentDao(),
            customerLedgerDao = db.customerLedgerDao(),
            settingsRepository = settings,
            inventoryRepository = inventory,
            clock = { now }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun editSnapshotsAreSequencedImmutableAndKeepTheSaleDate() = runBlocking {
        val customerId = db.customerDao().insert(
            Customer(name = "Asha", phone = "9999900000", address1 = "Market Road", city = "Pune")
        )
        val teaId = db.itemDao().insert(Item(name = "Tea", mrp = 20.0, stockQty = 100))
        val biscuitId = db.itemDao().insert(Item(name = "Biscuit", mrp = 10.0, stockQty = 100))
        val roundOff = 0.5
        val originalLines = lines(teaId, biscuitId, teaQty = 2)
        val originalTotal = BillTotalsCalculator.grandTotal(originalLines, roundOff)
        val originalPaise = Money.rupeesToPaise(originalTotal)

        val billId = repository.saveBill(
            customerId = customerId,
            lineItems = originalLines,
            billRoundOff = roundOff,
            payment = BillPaymentRequest.Record(
                listOf(
                    PaymentPortion(PaymentMethod.CASH, 2000),
                    PaymentPortion(PaymentMethod.UPI, originalPaise - 2000)
                )
            )
        )

        assertEquals(0, db.billVersionDao().countForBill(billId))
        assertEquals(1, repository.displayedVersionCount(billId))
        val synthetic = repository.getVersionHistory(billId).single()
        assertTrue(synthetic.isCurrent)
        assertEquals(BillVersionDiffer.ORIGINAL_SALE, synthetic.version.changeSummary)
        assertEquals("Asha", synthetic.version.customerName)
        assertEquals("9999900000", synthetic.version.customerPhone)
        assertEquals("Market Road, Pune", synthetic.version.customerAddress)
        assertEquals(2, synthetic.items.first { it.itemId == teaId }.quantity)
        assertEquals(18.0, synthetic.items.first { it.itemId == teaId }.finalPrice, 0.001)
        assertEquals(10.0, synthetic.items.first { it.itemId == teaId }.discount, 0.001)
        assertEquals(-1.0, synthetic.items.first { it.itemId == teaId }.roundOff, 0.001)
        assertEquals(setOf(PaymentMethod.CASH, PaymentMethod.UPI), synthetic.payments.map { it.method }.toSet())
        assertEquals(now, db.billDao().getBillById(billId)?.date)

        val saleDate = now
        now += 86_400_000L
        repository.preserveLegacyVersionIfNeeded(billId)
        db.customerDao().update(
            db.customerDao().getCustomerById(customerId)!!.copy(name = "Asha Devi")
        )
        val editedLines = lines(teaId, biscuitId, teaQty = 3)
        val editedPaise = Money.rupeesToPaise(BillTotalsCalculator.grandTotal(editedLines, roundOff))
        repository.saveBill(
            customerId = customerId,
            lineItems = editedLines,
            billRoundOff = roundOff,
            existingBillId = billId,
            payment = BillPaymentRequest.Record(
                listOf(PaymentPortion(PaymentMethod.CASH, editedPaise))
            )
        )

        val history = repository.getVersionHistory(billId)
        assertEquals(listOf(2, 1), history.map { it.version.versionNumber })
        val v1 = history.first { it.version.versionNumber == 1 }
        val v2 = history.first { it.version.versionNumber == 2 }
        assertEquals(BillVersionDiffer.ORIGINAL_SALE, v1.version.changeSummary)
        assertEquals(saleDate, v1.version.createdAt)
        assertEquals(saleDate, v1.version.saleDate)
        assertEquals(saleDate, v2.version.saleDate)
        assertEquals(now, v2.version.createdAt)
        assertEquals(saleDate, db.billDao().getBillById(billId)?.date)
        assertEquals("Asha", v1.version.customerName)
        assertEquals("Asha Devi", v2.version.customerName)
        assertEquals(2, v1.items.first { it.itemId == teaId }.quantity)
        assertEquals(18.0, v1.items.first { it.itemId == teaId }.finalPrice, 0.001)
        assertEquals(10.0, v1.items.first { it.itemId == teaId }.discount, 0.001)
        assertEquals(-1.0, v1.items.first { it.itemId == teaId }.roundOff, 0.001)
        assertEquals(2000L, v1.payments.first { it.method == PaymentMethod.CASH }.amountPaise)
        assertEquals(originalPaise - 2000, v1.payments.first { it.method == PaymentMethod.UPI }.amountPaise)
        assertEquals(3, v2.items.first { it.itemId == teaId }.quantity)
        assertEquals(editedPaise, v2.payments.single().amountPaise)
        assertTrue(v2.version.changeSummary.contains("Quantity changed: Tea 2 → 3"))
        assertTrue(v2.isCurrent)
        assertTrue(!v1.isCurrent)
        assertEquals(0, db.customerLedgerDao().getForBill(billId).size)

        db.itemDao().update(db.itemDao().getItemById(teaId)!!.copy(name = "Green tea"))
        val afterRename = repository.getVersionHistory(billId)
        assertEquals("Tea", afterRename.first { it.version.versionNumber == 1 }.items.first { it.itemId == teaId }.itemName)
        assertEquals("Biscuit", afterRename.first { it.version.versionNumber == 1 }.items.first { it.itemId == biscuitId }.itemName)

        val frozenV1 = afterRename.first { it.version.versionNumber == 1 }
        val frozenV2 = afterRename.first { it.version.versionNumber == 2 }
        now += 3_600_000L
        val thirdLines = lines(teaId, biscuitId, teaQty = 4)
        val thirdPaise = Money.rupeesToPaise(BillTotalsCalculator.grandTotal(thirdLines, roundOff))
        repository.saveBill(
            customerId = customerId,
            lineItems = thirdLines,
            billRoundOff = roundOff,
            existingBillId = billId,
            payment = BillPaymentRequest.Record(listOf(PaymentPortion(PaymentMethod.CASH, thirdPaise)))
        )
        val afterThird = repository.getVersionHistory(billId)
        val savedV1 = afterThird.first { it.version.versionNumber == 1 }
        val savedV2 = afterThird.first { it.version.versionNumber == 2 }
        assertEquals(frozenV1.version, savedV1.version)
        assertEquals(frozenV1.items, savedV1.items)
        assertEquals(frozenV1.payments, savedV1.payments)
        assertEquals(frozenV2.version, savedV2.version)
        assertEquals(frozenV2.items, savedV2.items)
        assertEquals(frozenV2.payments, savedV2.payments)
        assertTrue(savedV1.isCurrent.not())
        assertTrue(savedV2.isCurrent.not())
        assertTrue(afterThird.first { it.version.versionNumber == 3 }.isCurrent)
        assertEquals(3, afterThird.size)
        assertEquals(saleDate, db.billDao().getBillById(billId)?.date)

        val movements = db.stockMovementDao().getAllSync()
        assertEquals(4, movements.size)
        assertEquals(-4, movements.filter { it.itemId == teaId }.sumOf { it.delta })
        assertEquals(-1, movements.filter { it.itemId == biscuitId }.sumOf { it.delta })
        assertTrue(movements.all { it.type == StockMovementType.SALE.name })

        val stockBeforeDelete = db.itemDao().getItemById(teaId)!!.stockQty
        try {
            repository.deleteBill(db.billDao().getBillById(billId)!!)
            fail("Deleting a bill with version history must be refused")
        } catch (error: BillHistoryRetainedException) {
            assertEquals(billId, error.billId)
        }
        assertEquals(3, db.billVersionDao().countForBill(billId))
        assertEquals(stockBeforeDelete, db.itemDao().getItemById(teaId)!!.stockQty)
        assertEquals("INV-01", db.billDao().getBillById(billId)?.billNumber)
    }

    @Test
    fun restoreAppendsTheNextVersionAndKeepsEarlierSnapshots() = runBlocking {
        val customerId = db.customerDao().insert(Customer(name = "Asha", phone = "999", address1 = "Market"))
        val teaId = db.itemDao().insert(Item(name = "Tea", mrp = 20.0, stockQty = 50))
        val biscuitId = db.itemDao().insert(Item(name = "Biscuit", mrp = 10.0, stockQty = 50))
        val roundOff = 0.5
        val original = lines(teaId, biscuitId, teaQty = 2)
        val billId = repository.saveBill(
            customerId = customerId,
            lineItems = original,
            billRoundOff = roundOff,
            payment = BillPaymentRequest.Record(
                listOf(PaymentPortion(PaymentMethod.CREDIT, Money.rupeesToPaise(BillTotalsCalculator.grandTotal(original, roundOff))))
            )
        )
        val saleDate = now
        now += 60_000L
        repository.saveBill(
            customerId = customerId,
            lineItems = lines(teaId, biscuitId, teaQty = 5),
            billRoundOff = roundOff,
            existingBillId = billId,
            payment = BillPaymentRequest.Record(
                listOf(
                    PaymentPortion(
                        PaymentMethod.CASH,
                        Money.rupeesToPaise(BillTotalsCalculator.grandTotal(lines(teaId, biscuitId, teaQty = 5), roundOff))
                    )
                )
            )
        )
        db.customerDao().update(db.customerDao().getCustomerById(customerId)!!.copy(name = "Asha Devi"))
        val before = repository.getVersionHistory(billId)
        val v1 = before.first { it.version.versionNumber == 1 }
        val v2 = before.first { it.version.versionNumber == 2 }
        now += 60_000L
        repository.restoreVersion(billId, versionNumber = 1)

        val after = repository.getVersionHistory(billId)
        assertEquals(listOf(3, 2, 1), after.map { it.version.versionNumber })
        val restored = after.first { it.version.versionNumber == 3 }
        assertEquals(BillVersionDiffer.restoredFrom(1), restored.version.changeSummary)
        assertEquals(now, restored.version.createdAt)
        assertEquals(saleDate, restored.version.saleDate)
        assertEquals(saleDate, db.billDao().getBillById(billId)?.date)
        assertTrue(restored.isCurrent)
        assertEquals(2, restored.items.first { it.itemId == teaId }.quantity)
        assertEquals(v1.payments.map { it.method to it.amountPaise }, restored.payments.map { it.method to it.amountPaise })
        assertEquals(v1.version, after.first { it.version.versionNumber == 1 }.version)
        assertEquals(v1.items, after.first { it.version.versionNumber == 1 }.items)
        assertEquals(v1.payments, after.first { it.version.versionNumber == 1 }.payments)
        assertEquals(v2.version, after.first { it.version.versionNumber == 2 }.version)
        assertEquals(v2.items, after.first { it.version.versionNumber == 2 }.items)
        assertEquals(v2.payments, after.first { it.version.versionNumber == 2 }.payments)
        assertEquals(2, db.billItemDao().getItemsForBill(billId).first { it.itemId == teaId }.quantity)

        val printable = repository.printableBill(v1)
        assertEquals("Asha", printable.customer?.name)
        assertEquals("999", printable.customer?.phone)
        assertEquals("Market", printable.customer?.formattedAddress())
        assertEquals(saleDate, printable.bill.date)
        assertEquals(v1.version.total, printable.bill.total, 0.001)
        assertEquals(2, printable.items.first { it.itemId == teaId }.quantity)
        assertEquals(18.0, printable.items.first { it.itemId == teaId }.finalPrice, 0.001)
        assertEquals(v1.payments.map { it.method to it.amountPaise }, printable.payments.map { it.method to it.amountPaise })
        assertEquals("Asha", printable.customer?.name)
        assertNotEquals("Asha Devi", printable.customer?.name)

        val copyId = repository.duplicateVersion(billId, versionNumber = 2)!!
        assertNotEquals(billId, copyId)
        assertEquals(5, db.billItemDao().getItemsForBill(copyId).first { it.itemId == teaId }.quantity)
        assertEquals(2, db.billItemDao().getItemsForBill(billId).first { it.itemId == teaId }.quantity)
        assertEquals(PaymentMethod.CASH, db.billPaymentDao().getForBill(copyId).single().method)
    }

    @Test
    fun blockedRestoreDoesNotAppendAVersion() = runBlocking {
        settings.inventoryMode = InventoryMode.BLOCK
        val teaId = db.itemDao().insert(Item(name = "Tea", mrp = 10.0, stockQty = 5))
        val billId = saveQty(teaId, 2)
        settings.inventoryMode = InventoryMode.OFF
        saveQty(teaId, 1, billId)
        val before = repository.getVersionHistory(billId)
        settings.inventoryMode = InventoryMode.BLOCK
        db.itemDao().updateStockQty(teaId, 0)
        try {
            repository.restoreVersion(billId, versionNumber = 1)
            fail("Restore must honor the stock block")
        } catch (_: InsufficientStockException) {
            // expected
        }
        assertEquals(before, repository.getVersionHistory(billId))
        assertEquals(1, db.billItemDao().getItemsForBill(billId).single().quantity)
        assertEquals(0, db.itemDao().getItemById(teaId)!!.stockQty)
    }

    @Test
    fun failedEditDoesNotCreateAVersionOrChangeTheBill() = runBlocking {
        settings.inventoryMode = InventoryMode.BLOCK
        val teaId = db.itemDao().insert(Item(name = "Tea", mrp = 10.0, stockQty = 5))
        val billId = saveQty(teaId, 2)
        assertEquals(3, db.itemDao().getItemById(teaId)!!.stockQty)

        try {
            saveQty(teaId, 20, billId)
            fail("Block should refuse the edit")
        } catch (error: InsufficientStockException) {
            assertEquals("Tea", error.shortfalls.single().itemName)
            assertEquals(5, error.shortfalls.single().available)
            assertEquals(20, error.shortfalls.single().required)
            assertTrue(error.message!!.contains("available 5"))
            assertTrue(error.message!!.contains("needed 20"))
        }
        assertEquals(0, db.billVersionDao().countForBill(billId))
        assertEquals(2, db.billItemDao().getItemsForBill(billId).single().quantity)
        assertEquals(3, db.itemDao().getItemById(teaId)!!.stockQty)

        settings.inventoryMode = InventoryMode.OFF
        saveQty(teaId, 3, billId)
        val before = repository.getVersionHistory(billId)
        settings.inventoryMode = InventoryMode.BLOCK
        db.itemDao().updateStockQty(teaId, 0)
        try {
            saveQty(teaId, 50, billId)
            fail("A later blocked edit must not append a version")
        } catch (_: InsufficientStockException) {
            // expected
        }
        assertEquals(before, repository.getVersionHistory(billId))
        assertEquals(0, db.itemDao().getItemById(teaId)!!.stockQty)
    }

    @Test
    fun duplicateHonorsOffWarnAndBlockInsideTheSave() = runBlocking {
        settings.inventoryMode = InventoryMode.BLOCK
        val blocked = stockedSale(stock = 3, qty = 2)
        assertShortage {
            repository.duplicateBill(blocked.billId, shortageAcknowledged = true)
            Unit
        }
        assertEquals(1, blocked.stock())
        assertEquals(2, settings.invoiceCounter)
        assertEquals(1, db.billDao().getAllBillsWithDetailsSync().size)
        assertEquals(0, db.billVersionDao().countForBill(blocked.billId))

        settings.inventoryMode = InventoryMode.WARN
        val warned = stockedSale(stock = 3, qty = 2)
        assertShortage {
            repository.duplicateBill(warned.billId, shortageAcknowledged = false)
            Unit
        }
        assertEquals(1, warned.stock())
        val warnedCopy = repository.duplicateBill(warned.billId, shortageAcknowledged = true)!!
        assertNotEquals(warned.billId, warnedCopy)
        assertEquals(-1, warned.stock())
        val copy = db.billDao().getBillById(warnedCopy)
        assertNotEquals(db.billDao().getBillById(warned.billId)?.billNumber, copy?.billNumber)
        assertEquals(0, db.billVersionDao().countForBill(warned.billId))
        assertEquals(0, db.billVersionDao().countForBill(warnedCopy))
        assertTrue(
            db.stockMovementDao().getAllSync().any {
                it.referenceId == warnedCopy && it.type == StockMovementType.SALE.name && it.delta == -2
            }
        )

        settings.inventoryMode = InventoryMode.OFF
        val tracked = stockedSale(stock = 3, qty = 2)
        val trackedCopy = repository.duplicateBill(tracked.billId, shortageAcknowledged = false)
        assertNotEquals(null, trackedCopy)
        assertEquals(-1, tracked.stock())
        assertTrue(
            db.stockMovementDao().getAllSync().any {
                it.referenceId == trackedCopy && it.type == StockMovementType.SALE.name && it.delta == -2
            }
        )
    }

    private suspend fun lines(teaId: Long, biscuitId: Long, teaQty: Int) = listOf(
        BillLineItem(
            itemId = teaId,
            name = "Tea",
            mrp = 20.0,
            discount = 10.0,
            quantity = teaQty,
            roundOff = -1.0
        ),
        BillLineItem(
            itemId = biscuitId,
            name = "Biscuit",
            mrp = 10.0,
            discount = 0.0,
            quantity = 1
        )
    )

    private suspend fun saveQty(itemId: Long, qty: Int, existingBillId: Long? = null): Long {
        return repository.saveBill(
            customerId = null,
            lineItems = listOf(
                BillLineItem(itemId = itemId, name = "Tea", mrp = 10.0, discount = 0.0, quantity = qty)
            ),
            existingBillId = existingBillId,
            payment = BillPaymentRequest.KeepLegacy
        )
    }

    private suspend fun stockedSale(stock: Int, qty: Int): StockedSale {
        val itemId = db.itemDao().insert(Item(name = "Tea", mrp = 10.0, stockQty = stock))
        val billId = saveQty(itemId, qty)
        return StockedSale(itemId, billId)
    }

    private suspend fun assertShortage(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected a stock shortage")
        } catch (error: InsufficientStockException) {
            val shortfall = error.shortfalls.single()
            assertEquals("Tea", shortfall.itemName)
            assertEquals(1, shortfall.available)
            assertEquals(2, shortfall.required)
        }
    }

    private inner class StockedSale(val itemId: Long, val billId: Long) {
        suspend fun stock(): Int = db.itemDao().getItemById(itemId)!!.stockQty
    }
}
