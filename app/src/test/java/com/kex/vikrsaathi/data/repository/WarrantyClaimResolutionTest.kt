package com.kex.vikrsaathi.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kex.vikrsaathi.data.backup.WarrantyClaimBackup
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.domain.bill.WarrantySlip
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
class WarrantyClaimResolutionTest {
    private lateinit var db: AppDatabase
    private lateinit var claims: WarrantyClaimRepository
    private val today = CivilDate.of(2026, 11, 12)
    private val now = today * 86_400_000L + 12 * 60 * 60 * 1000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Executors.newSingleThreadExecutor())
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        claims = WarrantyClaimRepository(db, db.warrantyClaimDao(), db.billDao(), clock = { now })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun providerOutcomesSetStatusAndKeepEarlierEvents() = runBlocking {
        val expected = mapOf(
            WarrantyClaimPolicy.REPAIRED to WarrantyClaimPolicy.RESOLVED,
            WarrantyClaimPolicy.REPLACEMENT to WarrantyClaimPolicy.RESOLVED,
            WarrantyClaimPolicy.NO_FAULT to WarrantyClaimPolicy.RESOLVED,
            WarrantyClaimPolicy.OTHER to WarrantyClaimPolicy.RESOLVED,
            WarrantyClaimPolicy.DECLINED to WarrantyClaimPolicy.REJECTED
        )
        expected.forEach { (outcome, status) ->
            val id = newClaim()
            val note = if (outcome == WarrantyClaimPolicy.OTHER) "Shop arranged a visit" else ""
            val saved = claims.saveProviderOutcome(id, outcome, note, today)
            assertTrue(saved is WarrantyClaimOutcome.Saved)
            val claim = claims.claim(id)!!
            assertEquals(status, claim.status)
            assertEquals(outcome, claim.providerOutcome)
            assertNull(claim.handoverEpochDay)
            val events = claims.events(id)
            assertEquals(WarrantyClaimPolicy.RECEIVED, events.first().status)
            assertEquals(WarrantyClaimPolicy.EVENT_OUTCOME, events.last().eventKind)
            assertTrue(events.last().note.contains(WarrantyClaimPolicy.outcomeLabel(outcome)))
        }
    }

    @Test
    fun manualStatusCannotContradictASavedProviderOutcome() = runBlocking {
        val declined = newClaim()
        val saved = claims.saveProviderOutcome(declined, WarrantyClaimPolicy.DECLINED, "Not covered", today)
        assertTrue(saved is WarrantyClaimOutcome.Saved)
        val before = claims.events(declined).size
        val contradicted = claims.updateStatus(declined, WarrantyClaimPolicy.RESOLVED, "Reopened")
        assertEquals(
            WarrantyClaimPolicy.STATUS_FOLLOWS_OUTCOME,
            (contradicted as WarrantyClaimOutcome.NotAllowed).message
        )
        val claim = claims.claim(declined)!!
        assertEquals(WarrantyClaimPolicy.REJECTED, claim.status)
        assertEquals(WarrantyClaimPolicy.DECLINED, claim.providerOutcome)
        assertEquals("Not covered", claim.resolutionNote)
        assertEquals(before, claims.events(declined).size)

        val sameStatus = claims.updateStatus(declined, WarrantyClaimPolicy.REJECTED, "A different note")
        assertTrue(sameStatus is WarrantyClaimOutcome.NotAllowed)
        assertEquals("Not covered", claims.claim(declined)!!.resolutionNote)

        val repaired = newClaim()
        claims.saveProviderOutcome(repaired, WarrantyClaimPolicy.REPAIRED, "", today)
        val rejected = claims.updateStatus(repaired, WarrantyClaimPolicy.REJECTED, "Changed by hand")
        assertTrue(rejected is WarrantyClaimOutcome.NotAllowed)
        assertEquals(WarrantyClaimPolicy.RESOLVED, claims.claim(repaired)!!.status)
        assertEquals(WarrantyClaimPolicy.REPAIRED, claims.claim(repaired)!!.providerOutcome)
    }

    @Test
    fun otherRequiresAnOutcomeNote() = runBlocking {
        val id = newClaim()
        val rejected = claims.saveProviderOutcome(id, WarrantyClaimPolicy.OTHER, " ", today)
        assertEquals(
            WarrantyClaimPolicy.OUTCOME_NOTE_REQUIRED,
            (rejected as WarrantyClaimOutcome.NotAllowed).message
        )
        assertEquals(1, claims.events(id).size)
        assertEquals(WarrantyClaimPolicy.RECEIVED, claims.claim(id)!!.status)
    }

    @Test
    fun serviceAndHandoverAppendEventsWithoutErasingHistory() = runBlocking {
        val id = newClaim()
        val sent = CivilDate.of(2026, 11, 13)
        val service = claims.recordServiceCentre(id, "City Service", "SC-78412", sent)
        assertTrue(service is WarrantyClaimOutcome.Saved)
        val afterService = claims.claim(id)!!
        assertEquals(WarrantyClaimPolicy.SENT_TO_SERVICE, afterService.status)
        assertEquals("City Service", afterService.serviceCentre)
        assertEquals("SC-78412", afterService.serviceReference)
        assertEquals(sent, afterService.serviceSentEpochDay)

        val outcome = claims.saveProviderOutcome(id, WarrantyClaimPolicy.REPAIRED, "Ink flow restored", today)
        assertTrue(outcome is WarrantyClaimOutcome.Saved)
        val resolved = claims.claim(id)!!
        assertEquals(WarrantyClaimPolicy.RESOLVED, resolved.status)
        assertNull(resolved.handoverEpochDay)
        val blocked = claims.saveHandover(newClaim(), today, "", "")
        assertTrue(blocked is WarrantyClaimOutcome.NotAllowed)

        val handover = claims.saveHandover(id, CivilDate.of(2026, 11, 14), "Kalyani", "Collected at the counter")
        assertTrue(handover is WarrantyClaimOutcome.Saved)
        val done = claims.claim(id)!!
        assertEquals(WarrantyClaimPolicy.RESOLVED, done.status)
        assertEquals(CivilDate.of(2026, 11, 14), done.handoverEpochDay)
        assertEquals("Kalyani", done.handoverRecipient)
        val kinds = claims.events(id).map { it.eventKind }
        assertEquals(
            listOf(
                WarrantyClaimPolicy.EVENT_STATUS,
                WarrantyClaimPolicy.EVENT_SERVICE,
                WarrantyClaimPolicy.EVENT_OUTCOME,
                WarrantyClaimPolicy.EVENT_HANDOVER
            ),
            kinds
        )

        val updated = claims.saveHandover(id, CivilDate.of(2026, 11, 15), "Kalyani", "Came back the next day")
        assertTrue(updated is WarrantyClaimOutcome.Saved)
        assertEquals(5, claims.events(id).size)
        assertEquals(CivilDate.of(2026, 11, 15), claims.claim(id)!!.handoverEpochDay)

        val rows = WarrantySlip.rows(claims.claim(id)!!, "VikrSaathi")
        assertTrue(rows.any { it.label == "Service centre" && it.value == "City Service" })
        assertTrue(rows.any { it.label == "Service-centre reference" && it.value == "SC-78412" })
        assertTrue(rows.any { it.label == "Provider outcome" && it.value == "Repaired" })
        assertTrue(rows.any { it.label == "Handed to" && it.value == "Kalyani" })
        assertEquals(WarrantySlip.FOOTER, "Keep this slip for claim reference.")

        val stored = claims.claim(id)!!
        val json = JSONObject().put("warrantyClaims", WarrantyClaimBackup.encode(listOf(stored), claims.events(id)))
        val decoded = WarrantyClaimBackup.decode(json).single()
        assertEquals("City Service", decoded.serviceCentre)
        assertEquals(WarrantyClaimPolicy.REPAIRED, decoded.providerOutcome)
        assertEquals(CivilDate.of(2026, 11, 15), decoded.handoverEpochDay)
        assertEquals(WarrantyClaimPolicy.EVENT_HANDOVER, decoded.events.last().eventKind)
        val old = JSONObject(
            """{"warrantyClaims":[{"legacyId":1,"reference":"WC-26-00008","sequence":8,"billNumber":"OLD","saleDateMillis":1,"itemName":"Pen","issue":"Leak","status":"RECEIVED","createdAt":1,"updatedAt":1,"events":[{"status":"RECEIVED","note":"","createdAt":1}]}]}"""
        )
        val legacy = WarrantyClaimBackup.decode(old).single()
        assertNull(legacy.providerOutcome)
        assertEquals("", legacy.serviceCentre)
        assertEquals(WarrantyClaimPolicy.EVENT_STATUS, legacy.events.single().eventKind)
    }

    private suspend fun newClaim(): Long {
        val billId = db.billDao().insertBill(Bill(billNumber = "INV-${System.nanoTime()}", total = 10.0, date = now))
        val lineId = db.billItemDao().insert(
            BillItem(
                billId = billId,
                itemName = "Pen",
                mrp = 10.0,
                discount = 0.0,
                finalPrice = 10.0,
                warrantyPeriod = 12,
                warrantyUnit = WarrantyPolicy.MONTHS,
                warrantyProvider = WarrantyPolicy.MANUFACTURER,
                warrantyStartEpochDay = today,
                warrantyEndEpochDay = CivilDate.of(2027, 11, 11)
            )
        )
        val saved = claims.createClaim(lineId, "Ink stops", "", "")
        return (saved as WarrantyClaimOutcome.Saved).claimId
    }
}
