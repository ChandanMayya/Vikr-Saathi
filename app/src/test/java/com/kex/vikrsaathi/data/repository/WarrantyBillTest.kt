package com.kex.vikrsaathi.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kex.vikrsaathi.data.backup.BackupExportOptions
import com.kex.vikrsaathi.data.backup.BackupJsonCodec
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.draft.BillDraftCodec
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.domain.bill.warrantySnapshot
import com.kex.vikrsaathi.domain.finance.BillPaymentRequest
import com.kex.vikrsaathi.domain.inventory.CivilDate
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
class WarrantyBillTest {
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var bills: BillRepository

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
        settings.warrantyEnabled = true
        settings.inventoryMode = com.kex.vikrsaathi.util.InventoryMode.OFF
        val saleMillis = CivilDate.of(2026, 11, 12) * 86_400_000L + 12 * 60 * 60 * 1000L
        bills = BillRepository(
            database = db,
            billDao = db.billDao(),
            billItemDao = db.billItemDao(),
            billPaymentDao = db.billPaymentDao(),
            customerLedgerDao = db.customerLedgerDao(),
            settingsRepository = settings,
            inventoryRepository = InventoryRepository(db, db.itemDao(), db.stockMovementDao()),
            clock = { saleMillis }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun settingSurvivesANewRepositoryAndBackup() {
        settings.warrantyEnabled = true
        val again = SettingsRepository(ApplicationProvider.getApplicationContext())
        assertTrue(again.warrantyEnabled)
        val json = BackupJsonCodec.buildBackupJson(
            options = BackupExportOptions(includeSales = false, includeItems = false, includeCustomers = false, includeTemplates = false),
            settings = settings,
            customers = emptyList(),
            items = emptyList(),
            bills = emptyList(),
            templates = emptyList(),
            templateVersions = emptyList(),
            templateImages = emptyList()
        )
        val parsed = BackupJsonCodec.parseSettings(JSONObject(json))
        assertEquals(true, parsed?.warrantyEnabled)
        again.warrantyEnabled = false
        assertEquals(false, SettingsRepository(ApplicationProvider.getApplicationContext()).warrantyEnabled)
    }

    @Test
    fun saleCapturesWarrantyAndKeepsItAfterTheItemChanges() = runBlocking {
        val itemId = db.itemDao().insert(warrantedItem(period = 12))
        val billId = bills.saveBill(
            customerId = null,
            lineItems = listOf(line(itemId, 12)),
            payment = BillPaymentRequest.KeepLegacy
        )
        val sold = bills.getBillWithDetails(billId)!!.items.single()
        assertEquals(12, sold.warrantyPeriod)
        assertEquals(CivilDate.of(2026, 11, 12), sold.warrantyStartEpochDay)
        assertEquals(CivilDate.of(2027, 11, 11), sold.warrantyEndEpochDay)
        assertEquals("Manufacturer terms", sold.warrantyTerms)

        db.itemDao().update(warrantedItem(period = 24).copy(id = itemId))
        val retained = BillLineItem(
            itemId = itemId,
            name = "Mixer",
            mrp = 4500.0,
            discount = 0.0,
            quantity = 1,
            warranty = sold.warrantySnapshot()
        )
        val added = line(itemId, 24)
        bills.saveBill(
            customerId = null,
            lineItems = listOf(retained, added),
            existingBillId = billId,
            payment = BillPaymentRequest.LeaveUnchanged
        )
        val lines = bills.getBillWithDetails(billId)!!.items
        assertEquals(12, lines[0].warrantyPeriod)
        assertEquals(CivilDate.of(2027, 11, 11), lines[0].warrantyEndEpochDay)
        assertEquals(24, lines[1].warrantyPeriod)
        assertEquals(CivilDate.of(2028, 11, 11), lines[1].warrantyEndEpochDay)

        val history = bills.getVersionHistory(billId)
        val original = history.last().items.single()
        assertEquals(12, original.warrantyPeriod)
        assertEquals(CivilDate.of(2027, 11, 11), original.warrantyEndEpochDay)

        val plainId = db.itemDao().insert(Item(name = "Pen", mrp = 10.0))
        val plainBill = bills.saveBill(
            customerId = null,
            lineItems = listOf(BillLineItem(itemId = plainId, name = "Pen", mrp = 10.0, discount = 0.0)),
            payment = BillPaymentRequest.KeepLegacy
        )
        val plain = bills.getBillWithDetails(plainBill)!!.items.single()
        assertNull(plain.warrantyPeriod)
        assertNull(plain.warrantySnapshot())
    }

    @Test
    fun draftAndBackupKeepTheSnapshotWithoutInventingOne() = runBlocking {
        val pending = WarrantyPolicy.Snapshot(12, WarrantyPolicy.MONTHS, WarrantyPolicy.MANUFACTURER, terms = "Keep dry")
        val encoded = BillDraftCodec.encodeLineItems(
            listOf(BillLineItem(itemId = 4L, name = "Mixer", mrp = 100.0, discount = 0.0, warranty = pending))
        )
        val decoded = BillDraftCodec.decodeLineItems(encoded).single()
        assertEquals("Keep dry", decoded.warranty?.terms)
        assertNull(decoded.warranty?.endEpochDay)
        assertNull(BillDraftCodec.decodeLineItems(
            """[{"name":"Pen","mrp":10,"discount":0,"quantity":1}]"""
        ).single().warranty)

        val itemId = db.itemDao().insert(warrantedItem(12))
        val billId = bills.saveBill(
            customerId = null,
            lineItems = listOf(line(itemId, 12)),
            payment = BillPaymentRequest.KeepLegacy
        )
        val details = bills.getBillWithDetails(billId)!!
        val json = BackupJsonCodec.buildBackupJson(
            options = BackupExportOptions(includeSettings = false, includeTemplates = false, includeCustomers = false),
            settings = settings,
            customers = emptyList(),
            items = listOf(db.itemDao().getItemById(itemId)!!),
            bills = listOf(details),
            templates = emptyList(),
            templateVersions = emptyList(),
            templateImages = emptyList(),
            versionsByBillId = mapOf(billId to bills.getVersionHistory(billId))
        )
        val root = JSONObject(json)
        val item = BackupJsonCodec.parseItems(root).single()
        assertTrue(item.warrantyApplies)
        assertEquals(12, item.warrantyPeriod)
        val bill = BackupJsonCodec.parseBills(root).single()
        assertEquals(12, bill.items.single().warranty?.period)
        assertEquals(CivilDate.of(2027, 11, 11), bill.items.single().warranty?.endEpochDay)
        val old = JSONObject("""{"schemaVersion":6,"bills":[{"legacyId":1,"billNumber":"OLD","date":1,"total":10,"items":[{"itemName":"Pen","quantity":1,"mrp":10,"discount":0,"finalPrice":10}]}]}""")
        assertNull(BackupJsonCodec.parseBills(old).single().items.single().warranty)
    }

    private fun warrantedItem(period: Int) = Item(
        name = "Mixer",
        mrp = 4500.0,
        warrantyApplies = true,
        warrantyPeriod = period,
        warrantyUnit = WarrantyPolicy.MONTHS,
        warrantyProvider = WarrantyPolicy.MANUFACTURER,
        warrantyTerms = "Manufacturer terms"
    )

    private fun line(itemId: Long, period: Int) = BillLineItem(
        itemId = itemId,
        name = "Mixer",
        mrp = 4500.0,
        discount = 0.0,
        warranty = WarrantyPolicy.Snapshot(period, WarrantyPolicy.MONTHS, WarrantyPolicy.MANUFACTURER, terms = "Manufacturer terms")
    )
}
