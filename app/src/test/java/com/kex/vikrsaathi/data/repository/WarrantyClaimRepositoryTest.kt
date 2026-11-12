package com.kex.vikrsaathi.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kex.vikrsaathi.data.backup.WarrantyClaimBackup
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.domain.bill.WarrantySlip
import com.kex.vikrsaathi.domain.inventory.CivilDate
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WarrantyClaimRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var claims: WarrantyClaimRepository
    private val saleMillis = CivilDate.of(2026, 11, 12) * 86_400_000L + 12 * 60 * 60 * 1000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Executors.newSingleThreadExecutor())
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        claims = WarrantyClaimRepository(
            database = db,
            claims = db.warrantyClaimDao(),
            bills = db.billDao(),
            clock = { saleMillis }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createsAClaimOnlyForAnActiveSaleSnapshot() = runBlocking {
        val customerId = db.customerDao().insert(Customer(name = "Kalyani", phone = "9000000000"))
        val billId = db.billDao().insertBill(
            Bill(billNumber = "INV-0042", customerId = customerId, total = 4500.0, date = saleMillis)
        )
        val activeId = db.billItemDao().insert(line(billId, "Mixer", end = CivilDate.of(2027, 11, 11)))
        val expiredId = db.billItemDao().insert(line(billId, "Old fan", end = CivilDate.of(2026, 11, 1)))
        val plainId = db.billItemDao().insert(
            BillItem(billId = billId, itemName = "Pen", mrp = 10.0, discount = 0.0, finalPrice = 10.0)
        )

        val expired = claims.createClaim(expiredId, "Dead motor", "", "")
        val plain = claims.createClaim(plainId, "Leak", "", "")
        val blank = claims.createClaim(activeId, "  ", "", "")
        assertTrue(expired is WarrantyClaimOutcome.NotAllowed)
        assertEquals(WarrantyClaimPolicy.BLOCKED_EXPIRED, (expired as WarrantyClaimOutcome.NotAllowed).message)
        assertEquals(WarrantyClaimPolicy.BLOCKED_NONE, (plain as WarrantyClaimOutcome.NotAllowed).message)
        assertEquals(WarrantyClaimPolicy.ISSUE_REQUIRED, (blank as WarrantyClaimOutcome.NotAllowed).message)
        assertTrue(claims.history().isEmpty())

        val saved = claims.createClaim(activeId, "Ink flow stops", "Used", "Brought in box")
        assertTrue(saved is WarrantyClaimOutcome.Saved)
        val claim = claims.claim((saved as WarrantyClaimOutcome.Saved).claimId)!!
        assertEquals("WC-26-00001", claim.reference)
        assertEquals(billId, claim.billId)
        assertEquals(activeId, claim.billItemId)
        assertEquals("INV-0042", claim.billNumber)
        assertEquals("Kalyani", claim.customerName)
        assertEquals("Mixer", claim.itemName)
        assertEquals(12, claim.warrantyPeriod)
        assertEquals(CivilDate.of(2027, 11, 11), claim.warrantyEndEpochDay)
        assertEquals(WarrantyClaimPolicy.RECEIVED, claim.status)
        assertEquals("Ink flow stops", claim.issue)

        val second = claims.createClaim(activeId, "Still failing", "", "")
        val again = claims.claim((second as WarrantyClaimOutcome.Saved).claimId)!!
        assertEquals("WC-26-00002", again.reference)

        db.openHelper.writableDatabase.execSQL(
            "UPDATE bill_items SET warrantyPeriod = 1, warrantyEndEpochDay = 1 WHERE id = $activeId"
        )
        val stored = claims.claim(claim.id)!!
        assertEquals(12, stored.warrantyPeriod)
        assertEquals(CivilDate.of(2027, 11, 11), stored.warrantyEndEpochDay)
        assertEquals("INV-0042", stored.billNumber)
        val blockedAfterEdit = claims.createClaim(activeId, "Later visit", "", "")
        assertEquals(
            WarrantyClaimPolicy.BLOCKED_EXPIRED,
            (blockedAfterEdit as WarrantyClaimOutcome.NotAllowed).message
        )

        val missingNote = claims.updateStatus(claim.id, WarrantyClaimPolicy.RESOLVED, " ")
        assertEquals(
            WarrantyClaimPolicy.RESOLUTION_REQUIRED,
            (missingNote as WarrantyClaimOutcome.NotAllowed).message
        )
        val resolved = claims.updateStatus(claim.id, WarrantyClaimPolicy.RESOLVED, "Replaced the nib")
        assertTrue(resolved is WarrantyClaimOutcome.Saved)
        val after = claims.claim(claim.id)!!
        assertEquals(WarrantyClaimPolicy.RESOLVED, after.status)
        assertEquals("Replaced the nib", after.resolutionNote)
        assertEquals(2, claims.events(claim.id).size)
        assertEquals("INV-0042", after.billNumber)

        val slip = WarrantySlip.rows(after, "VikrSaathi")
        assertEquals(WarrantySlip.TITLE, "WARRANTY SERVICE SLIP")
        assertEquals(WarrantySlip.FOOTER, "Keep this slip for claim reference.")
        assertTrue(slip.any { it.label == "Issue" && it.value == "Ink flow stops" })
        assertTrue(slip.any { it.label == "Bill" && it.value == "INV-0042" })
        assertTrue(slip.any { it.label == "Outcome" && it.value == "Replaced the nib" })

        val json = JSONObject().put(
            "warrantyClaims",
            WarrantyClaimBackup.encode(listOf(after), claims.events(claim.id))
        )
        val decoded = WarrantyClaimBackup.decode(json).single()
        assertEquals("WC-26-00001", decoded.reference)
        assertEquals(billId, decoded.billLegacyId)
        assertEquals(CivilDate.of(2027, 11, 11), decoded.warrantyEndEpochDay)
        assertEquals(2, decoded.events.size)
        assertTrue(WarrantyClaimBackup.decode(JSONObject()).isEmpty())
        val matched = WarrantyClaimBackup.matchingBillItemId(
            listOf(
                BillItem(
                    id = 8, billId = 1, itemName = "Mixer", mrp = 1.0, discount = 0.0, finalPrice = 1.0,
                    warrantyEndEpochDay = 5
                ),
                BillItem(
                    id = 9, billId = 1, itemName = "Mixer", mrp = 1.0, discount = 0.0, finalPrice = 1.0,
                    warrantyEndEpochDay = CivilDate.of(2027, 11, 11)
                )
            ),
            "Mixer",
            CivilDate.of(2027, 11, 11)
        )
        assertEquals(9L, matched)
    }

    private fun line(billId: Long, name: String, end: Long) = BillItem(
        billId = billId,
        itemName = name,
        mrp = 100.0,
        discount = 0.0,
        finalPrice = 100.0,
        warrantyPeriod = 12,
        warrantyUnit = WarrantyPolicy.MONTHS,
        warrantyProvider = WarrantyPolicy.MANUFACTURER,
        warrantyStartEpochDay = CivilDate.of(2026, 11, 12),
        warrantyEndEpochDay = end
    )
}
