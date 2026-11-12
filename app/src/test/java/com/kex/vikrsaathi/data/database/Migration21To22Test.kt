package com.kex.vikrsaathi.data.database

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Migration21To22Test {
    private lateinit var context: Context
    private val name = "migration-21-22.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun keepsPhase2ClaimsAndMarksOldEventsAsStatus() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(21) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE warranty_claims (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                reference TEXT NOT NULL,
                                sequence INTEGER NOT NULL,
                                billId INTEGER,
                                billItemId INTEGER,
                                billNumber TEXT NOT NULL,
                                saleDateMillis INTEGER NOT NULL,
                                customerName TEXT NOT NULL,
                                customerPhone TEXT NOT NULL,
                                itemName TEXT NOT NULL,
                                warrantyPeriod INTEGER,
                                warrantyUnit TEXT,
                                warrantyProvider TEXT,
                                warrantyProviderName TEXT,
                                warrantyTerms TEXT,
                                warrantyStartEpochDay INTEGER,
                                warrantyEndEpochDay INTEGER,
                                issue TEXT NOT NULL,
                                condition TEXT NOT NULL,
                                note TEXT NOT NULL,
                                status TEXT NOT NULL,
                                resolutionNote TEXT NOT NULL,
                                createdAt INTEGER NOT NULL,
                                updatedAt INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO warranty_claims (
                                reference, sequence, billNumber, saleDateMillis, customerName, customerPhone,
                                itemName, warrantyEndEpochDay, issue, condition, note, status, resolutionNote,
                                createdAt, updatedAt
                            ) VALUES (
                                'WC-26-00004', 4, 'INV-0042', 100, 'Kalyani', '',
                                'Pen', 20000, 'Leak', '', '', 'UNDER_INSPECTION', '',
                                10, 10
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE warranty_claim_events (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                claimId INTEGER NOT NULL,
                                status TEXT NOT NULL,
                                note TEXT NOT NULL,
                                createdAt INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO warranty_claim_events (claimId, status, note, createdAt) VALUES (1, 'RECEIVED', '', 10)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        MIGRATION_21_22.migrate(db)
        val claim = db.query(
            "SELECT status, issue, warrantyEndEpochDay, serviceCentre, providerOutcome, handoverEpochDay FROM warranty_claims"
        )
        claim.use {
            it.moveToFirst()
            assertEquals("UNDER_INSPECTION", it.getString(0))
            assertEquals("Leak", it.getString(1))
            assertEquals(20000, it.getInt(2))
            assertEquals("", it.getString(3))
            assertNull(it.getString(4))
            assertNull(it.getString(5))
        }
        val event = db.query("SELECT status, eventKind FROM warranty_claim_events")
        event.use {
            it.moveToFirst()
            assertEquals("RECEIVED", it.getString(0))
            assertEquals("STATUS", it.getString(1))
        }
        helper.close()
    }
}
