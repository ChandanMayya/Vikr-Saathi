package com.kex.vikrsaathi.data.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ManualBackupStoreTest {

    @Test
    fun recordsABackupAndReportsWhenTheFileIsGone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ManualBackupStore(context)
        store.clear()
        assertTrue(ManualBackupFiles.status(context, store) is ManualBackupStatus.None)

        val file = File(context.filesDir, "backup.json")
        file.writeText("{}")
        store.save(
            ManualBackupRecord(
                exportedAt = 1_700_000_000_000L,
                fileName = file.name,
                displayPath = "Documents / Vikr Saathi / ${file.name}",
                categories = listOf("sales", "items"),
                sizeBytes = 12,
                contentUri = null,
                legacyPath = file.absolutePath
            )
        )
        val available = ManualBackupFiles.status(context, store)
        assertTrue(available is ManualBackupStatus.Available)
        assertEquals(listOf("sales", "items"), (available as ManualBackupStatus.Available).record.categories)

        file.delete()
        assertTrue(ManualBackupFiles.status(context, store) is ManualBackupStatus.Missing)
        store.clear()
    }

    @Test
    fun olderBackupWithoutVersionsStillParses() {
        val json = """
            {
              "schemaVersion": 3,
              "exportedAt": 10,
              "includes": ["sales"],
              "bills": [{
                "legacyId": 1,
                "billNumber": "A-1",
                "invoiceCounter": 1,
                "total": 20,
                "date": 5,
                "items": [{
                  "itemName": "Tea",
                  "quantity": 1,
                  "mrp": 20,
                  "discount": 0,
                  "finalPrice": 20
                }]
              }]
            }
        """.trimIndent()
        assertTrue(BackupSchema.SUPPORTED.contains(3))
        val bills = BackupJsonCodec.parseBills(JSONObject(json))
        assertEquals(1, bills.size)
        assertTrue(bills[0].versions.isEmpty())
        assertNull(bills[0].items[0].unitCostPaise)
        assertTrue(BackupJsonCodec.parseOperators(JSONObject(json)).isEmpty())
        assertTrue(BackupJsonCodec.parseCostLots(JSONObject(json)).isEmpty())
    }
}
