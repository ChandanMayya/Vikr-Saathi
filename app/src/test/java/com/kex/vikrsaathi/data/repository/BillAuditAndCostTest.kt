package com.kex.vikrsaathi.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.domain.bill.OperatorLabels
import com.kex.vikrsaathi.domain.dashboard.DashboardPeriod
import com.kex.vikrsaathi.domain.dashboard.GrossProfitCalculator
import com.kex.vikrsaathi.domain.finance.BillPaymentRequest
import com.kex.vikrsaathi.util.InventoryMode
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
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
class BillAuditAndCostTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var bills: BillRepository
    private lateinit var vendors: VendorRepository
    private lateinit var finance: CustomerFinanceRepository
    private var now = 1_763_000_000_000L

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
        settings.selectedOperatorId = 0L
        val inventory = InventoryRepository(db, db.itemDao(), db.stockMovementDao())
        bills = BillRepository(
            database = db,
            billDao = db.billDao(),
            billItemDao = db.billItemDao(),
            billPaymentDao = db.billPaymentDao(),
            customerLedgerDao = db.customerLedgerDao(),
            settingsRepository = settings,
            inventoryRepository = inventory,
            clock = { now }
        )
        vendors = VendorRepository(
            database = db,
            vendorDao = db.vendorDao(),
            purchaseDao = db.purchaseDao(),
            itemDao = db.itemDao(),
            inventoryRepository = inventory,
            settingsRepository = settings
        )
        finance = CustomerFinanceRepository(
            database = db,
            ledgerDao = db.customerLedgerDao(),
            billDao = db.billDao(),
            billItemDao = db.billItemDao(),
            billPaymentDao = db.billPaymentDao(),
            inventoryRepository = inventory
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun editStoresActorAndLeavesThePriorVersionImmutable() = runBlocking {
        OperatorRepository(db, settings).saveAndSelect("Owner")
        val tea = db.itemDao().insert(Item(name = "Tea", mrp = 20.0, stockQty = 10))
        val billId = bills.saveBill(
            customerId = null,
            lineItems = listOf(BillLineItem(itemId = tea, name = "Tea", mrp = 20.0, discount = 0.0, quantity = 1)),
            payment = BillPaymentRequest.KeepLegacy
        )
        val saleDate = db.billDao().getBillById(billId)!!.date
        now += 3_600_000L
        bills.saveBill(
            customerId = null,
            lineItems = listOf(BillLineItem(itemId = tea, name = "Tea", mrp = 20.0, discount = 0.0, quantity = 2)),
            existingBillId = billId,
            payment = BillPaymentRequest.KeepLegacy
        )
        val history = bills.getVersionHistory(billId)
        assertEquals(listOf(2, 1), history.map { it.version.versionNumber })
        assertEquals(OperatorLabels.THIS_DEVICE, history.first { it.version.versionNumber == 1 }.version.actorLabel)
        assertEquals("Owner", history.first { it.version.versionNumber == 2 }.version.actorLabel)
        assertEquals(saleDate, db.billDao().getBillById(billId)!!.date)
        assertEquals(1, history.first { it.version.versionNumber == 1 }.items.single().quantity)
        val frozen = history.first { it.version.versionNumber == 1 }
        now += 3_600_000L
        bills.saveBill(
            customerId = null,
            lineItems = listOf(BillLineItem(itemId = tea, name = "Tea", mrp = 20.0, discount = 0.0, quantity = 3)),
            existingBillId = billId,
            payment = BillPaymentRequest.KeepLegacy
        )
        val again = bills.getVersionHistory(billId).first { it.version.versionNumber == 1 }
        assertEquals(frozen.version, again.version)
        assertEquals(frozen.items, again.items)
    }

    @Test
    fun profitUsesPurchaseInvoiceCostAndStaysIncompleteWithoutIt() = runBlocking {
        val tea = db.itemDao().insert(Item(name = "Tea", mrp = 20.0, stockQty = 0))
        val soap = db.itemDao().insert(Item(name = "Soap", mrp = 10.0, stockQty = 5))
        val vendorId = db.vendorDao().insert(Vendor(name = "Mill"))
        vendors.saveInvoice(
            vendorId = vendorId,
            invoiceNumber = "PI-1",
            invoiceDate = now,
            orderId = null,
            receiptId = null,
            deliveryChargesPaise = 0,
            otherChargesPaise = 0,
            discountPaise = 0,
            notes = "",
            lines = listOf(InvoiceLineInput(tea, "Tea", 10, 500))
        )
        val billId = bills.saveBill(
            customerId = null,
            lineItems = listOf(BillLineItem(itemId = tea, name = "Tea", mrp = 20.0, discount = 0.0, quantity = 2)),
            payment = BillPaymentRequest.KeepLegacy
        )
        val line = db.billItemDao().getItemsForBill(billId).single()
        assertEquals(500L, line.unitCostPaise)
        val covered = bills.getDashboardPeriodStats(DashboardPeriod.TODAY, now)
        assertEquals(GrossProfitCalculator.Coverage.COMPLETE, covered.profit.coverage)
        assertEquals(3000L, covered.profit.profitPaise)

        bills.saveBill(
            customerId = null,
            lineItems = listOf(BillLineItem(itemId = soap, name = "Soap", mrp = 10.0, discount = 0.0, quantity = 1)),
            payment = BillPaymentRequest.KeepLegacy
        )
        val mixed = bills.getDashboardPeriodStats(DashboardPeriod.TODAY, now)
        assertEquals(GrossProfitCalculator.Coverage.INCOMPLETE, mixed.profit.coverage)
        assertNull(mixed.profit.profitPaise)

        val customerId = db.customerDao().insert(Customer(name = "Asha"))
        finance.addSalesReturn(
            customerId = customerId,
            billId = billId,
            reason = "Changed mind",
            moneyRefundedPaise = 0,
            creditAdjustmentPaise = 0,
            lines = listOf(ReturnLineInput(tea, "Tea", 1)),
            occurredAt = now
        )
        val returned = db.customerLedgerDao().getAllReturnLines().single()
        assertEquals(500L, returned.unitCostPaise)
    }
}
