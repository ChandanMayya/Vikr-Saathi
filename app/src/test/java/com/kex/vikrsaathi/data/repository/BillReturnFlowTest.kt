package com.kex.vikrsaathi.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.domain.finance.FinanceError
import com.kex.vikrsaathi.domain.finance.FinanceException
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.domain.finance.RefundMethod
import com.kex.vikrsaathi.util.InventoryMode
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
class BillReturnFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var bills: BillRepository
    private lateinit var finance: CustomerFinanceRepository

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
        settings.inventoryMode = InventoryMode.OFF
        settings.invoiceCounter = 1
        settings.invoicePrefix = "INV"
        settings.invoiceSeparator = "-"
        val inventory = InventoryRepository(db, db.itemDao(), db.stockMovementDao())
        bills = BillRepository(
            database = db,
            billDao = db.billDao(),
            billItemDao = db.billItemDao(),
            billPaymentDao = db.billPaymentDao(),
            customerLedgerDao = db.customerLedgerDao(),
            settingsRepository = settings,
            inventoryRepository = inventory
        )
        finance = CustomerFinanceRepository(
            database = db,
            ledgerDao = db.customerLedgerDao(),
            billDao = db.billDao(),
            billItemDao = db.billItemDao(),
            billPaymentDao = db.billPaymentDao(),
            inventoryRepository = inventory,
            billRepository = bills
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun partialReturnsStayInsideTheRemainingQuantity() = runBlocking {
        val tea = db.itemDao().insert(Item(name = "Tea", mrp = 20.0, stockQty = 6))
        val billId = bills.saveBill(
            customerId = null,
            lineItems = listOf(BillLineItem(itemId = tea, name = "Tea", mrp = 20.0, discount = 0.0, quantity = 6)),
            payment = com.kex.vikrsaathi.domain.finance.BillPaymentRequest.KeepLegacy
        )
        val lineId = db.billItemDao().getItemsForBill(billId).single().id
        val first = finance.postBillReturn(
            billId, listOf(BillReturnLineInput(lineId, 2, true)),
            RefundMethod.CASH, "", null, 10L, "Owner"
        )
        assertEquals(4000L, first.returnValuePaise)
        assertEquals(2, db.itemDao().getItemById(tea)!!.stockQty)
        val second = finance.postBillReturn(
            billId, listOf(BillReturnLineInput(lineId, 3, false)),
            RefundMethod.UPI, "", null, 11L, "Owner"
        )
        assertEquals(6000L, second.returnValuePaise)
        assertEquals(2, db.itemDao().getItemById(tea)!!.stockQty)
        assertFails(FinanceError.RETURN_QTY_EXCEEDS_SOLD) {
            runBlocking {
                finance.postBillReturn(
                    billId, listOf(BillReturnLineInput(lineId, 2, true)),
                    RefundMethod.CASH, "", null, 12L, "Owner"
                )
            }
        }
        val walkIn = db.customerLedgerDao().getAll()
        assertTrue(walkIn.isEmpty())
        val recorded = db.customerLedgerDao().getReturnById(first.returnId)!!
        assertNull(recorded.customerId)
        assertEquals(RefundMethod.CASH, recorded.refundMethod)
    }

    @Test
    fun repeatedLinesAndMissingItemsAreHandledSeparately() = runBlocking {
        val biscuit = db.itemDao().insert(Item(name = "Biscuit", mrp = 10.0, stockQty = 4))
        val billId = db.billDao().insertBill(Bill(billNumber = "A", invoiceCounter = 1, total = 50.0, date = 5L))
        val cheap = db.billItemDao().insert(
            BillItem(billId = billId, itemId = biscuit, itemName = "Biscuit", quantity = 2, mrp = 10.0, discount = 0.0, finalPrice = 10.0)
        )
        val dear = db.billItemDao().insert(
            BillItem(billId = billId, itemId = biscuit, itemName = "Biscuit", quantity = 2, mrp = 15.0, discount = 0.0, finalPrice = 15.0)
        )
        val posted = finance.postBillReturn(
            billId, listOf(BillReturnLineInput(dear, 1, true)),
            RefundMethod.CASH, "", null, 6L, "This device"
        )
        assertEquals(1500L, posted.returnValuePaise)
        val deletedBill = db.billDao().insertBill(Bill(billNumber = "B", invoiceCounter = 2, total = 10.0, date = 6L))
        val orphan = db.billItemDao().insert(
            BillItem(billId = deletedBill, itemId = null, itemName = "Old tea", quantity = 1, mrp = 10.0, discount = 0.0, finalPrice = 10.0)
        )
        assertFails(FinanceError.RESTOCK_NEEDS_ITEM) {
            runBlocking {
                finance.postBillReturn(
                    deletedBill, listOf(BillReturnLineInput(orphan, 1, true)),
                    RefundMethod.CASH, "", null, 7L, "This device"
                )
            }
        }
        finance.postBillReturn(
            deletedBill, listOf(BillReturnLineInput(orphan, 1, false)),
            RefundMethod.CASH, "", null, 8L, "This device"
        )
        assertEquals(5, db.itemDao().getItemById(biscuit)!!.stockQty)
        assertTrue(db.customerLedgerDao().getLinesForReturn(
            db.customerLedgerDao().getReturnsForBill(billId).single().id
        ).single().billItemId == dear)
        assertTrue(cheap > 0)
    }

    @Test
    fun storeCreditRequiresACustomerAndExchangeRollsBackTogether() = runBlocking {
        val tea = db.itemDao().insert(Item(name = "Tea", mrp = 20.0, stockQty = 2))
        val soap = db.itemDao().insert(Item(name = "Soap", mrp = 30.0, stockQty = 0))
        val billId = bills.saveBill(
            customerId = null,
            lineItems = listOf(BillLineItem(itemId = tea, name = "Tea", mrp = 20.0, discount = 0.0, quantity = 1)),
            payment = com.kex.vikrsaathi.domain.finance.BillPaymentRequest.KeepLegacy
        )
        val lineId = db.billItemDao().getItemsForBill(billId).single().id
        assertFails(FinanceError.CREDIT_REQUIRES_CUSTOMER) {
            runBlocking {
                finance.postBillReturn(
                    billId, listOf(BillReturnLineInput(lineId, 1, true)),
                    RefundMethod.STORE_CREDIT, "", null, 9L, "Owner"
                )
            }
        }
        assertTrue(db.customerLedgerDao().getAllReturns().isEmpty())
        settings.inventoryMode = InventoryMode.BLOCK
        try {
            finance.postExchange(
                billId = billId,
                lines = listOf(BillReturnLineInput(lineId, 1, true)),
                replacementLines = listOf(BillLineItem(itemId = soap, name = "Soap", mrp = 30.0, discount = 0.0, quantity = 1)),
                replacementRoundOff = 0.0,
                dueMethod = PaymentMethod.CASH,
                reason = "",
                note = null,
                occurredAt = 10L,
                actorLabel = "Owner"
            )
            throw AssertionError("Expected stock failure")
        } catch (error: InsufficientStockException) {
            assertTrue(db.customerLedgerDao().getAllReturns().isEmpty())
            assertEquals(1, db.itemDao().getItemById(tea)!!.stockQty)
            assertEquals(1, db.billDao().getAllBillsWithDetailsSync().size)
            assertEquals(billId, db.billDao().getAllBillsWithDetailsSync().single().bill.id)
        }
        settings.inventoryMode = InventoryMode.OFF
        val customerId = db.customerDao().insert(Customer(name = "Asha"))
        val posted = finance.postExchange(
            billId = billId,
            lines = listOf(BillReturnLineInput(lineId, 1, true)),
            replacementLines = listOf(BillLineItem(itemId = soap, name = "Soap", mrp = 15.0, discount = 0.0, quantity = 1)),
            replacementRoundOff = 0.0,
            dueMethod = RefundMethod.CASH,
            reason = "Swap",
            note = null,
            occurredAt = 11L,
            actorLabel = "Owner",
            walkInCustomerId = customerId
        )
        val record = db.customerLedgerDao().getReturnById(posted.returnId)!!
        assertEquals(500L, record.moneyRefundedPaise)
        assertEquals(customerId, record.customerId)
        val replacementPayments = db.billPaymentDao().getForBill(posted.replacementBillId!!)
        assertEquals(PaymentMethod.EXCHANGE, replacementPayments.single().method)
        assertEquals(1500L, replacementPayments.single().amountPaise)
        assertTrue(db.customerLedgerDao().getAll().isEmpty())
    }

    @Test
    fun exchangeKeepsApprovedDiscountAndANewInvoiceNumber() = runBlocking {
        val pen = db.itemDao().insert(Item(name = "Pen", mrp = 10.0, discount = 10.0, sellingPrice = 5.0, stockQty = 4))
        val billId = bills.saveBill(
            customerId = null,
            lineItems = listOf(BillLineItem(itemId = pen, name = "Pen", mrp = 10.0, discount = 0.0, quantity = 1)),
            payment = com.kex.vikrsaathi.domain.finance.BillPaymentRequest.KeepLegacy
        )
        val original = bills.getBillWithDetails(billId)!!
        val lineId = original.items.single().id
        val posted = finance.postExchange(
            billId = billId,
            lines = listOf(BillReturnLineInput(lineId, 1, true)),
            replacementLines = listOf(BillLineItem(itemId = pen, name = "Pen", mrp = 10.0, discount = 50.0, quantity = 2)),
            replacementRoundOff = 0.0,
            dueMethod = PaymentMethod.CASH,
            reason = "Wrong item",
            note = null,
            occurredAt = 12L,
            actorLabel = "Owner"
        )
        val unchanged = bills.getBillWithDetails(billId)!!
        assertEquals(original.bill.billNumber, unchanged.bill.billNumber)
        assertEquals(original.bill.total, unchanged.bill.total, 0.001)
        val replacement = bills.getBillWithDetails(posted.replacementBillId!!)!!
        assertTrue(replacement.bill.billNumber != original.bill.billNumber)
        assertEquals(10.0, replacement.items.single().mrp, 0.001)
        assertEquals(50.0, replacement.items.single().discount, 0.001)
        assertEquals(2, replacement.items.single().quantity)
        val record = db.customerLedgerDao().getReturnById(posted.returnId)!!
        assertEquals(posted.replacementBillId, record.exchangeBillId)
        assertEquals("Wrong item", record.reason)
        assertEquals(10.0, db.itemDao().getItemById(pen)!!.mrp, 0.001)
        assertEquals(10.0, db.itemDao().getItemById(pen)!!.discount, 0.001)
    }

    @Test
    fun olderReturnJsonWithoutNewFieldsStillParses() {
        val json = JSONObject(
            """
            {"salesReturns":[{"legacyId":3,"customerLegacyId":8,"reason":"Old","moneyRefundedPaise":100}],
             "salesReturnLines":[{"legacyId":4,"returnLegacyId":3,"itemName":"Tea","quantity":1}]}
            """.trimIndent()
        )
        val parsed = com.kex.vikrsaathi.data.backup.FinanceBackupCodec.salesReturns(json).single()
        assertEquals(8L, parsed.customerId)
        assertEquals(100L, parsed.moneyRefundedPaise)
        assertEquals("", parsed.refundMethod)
        val line = com.kex.vikrsaathi.data.backup.FinanceBackupCodec.salesReturnLines(json).single()
        assertNull(line.billItemId)
        assertTrue(line.restock)
    }

    private fun assertFails(code: FinanceError, block: () -> Unit) {
        try {
            block()
        } catch (error: FinanceException) {
            assertEquals(code, error.code)
            return
        }
        throw AssertionError("Expected $code")
    }
}
