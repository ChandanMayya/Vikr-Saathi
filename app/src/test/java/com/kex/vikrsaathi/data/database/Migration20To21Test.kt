package com.kex.vikrsaathi.data.database

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
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
class Migration20To21Test {
    private lateinit var context: Context
    private val name = "migration-20-21.db"

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
    fun addsClaimTablesWithoutChangingExistingSales() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE bills (id INTEGER PRIMARY KEY NOT NULL, billNumber TEXT NOT NULL)")
                        db.execSQL("INSERT INTO bills (id, billNumber) VALUES (4, 'INV-0004')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        MIGRATION_20_21.migrate(db)
        val bill = db.query("SELECT billNumber FROM bills")
        bill.use {
            it.moveToFirst()
            assertEquals("INV-0004", it.getString(0))
        }
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('warranty_claims', 'warranty_claim_events')"
        )
        val names = mutableListOf<String>()
        tables.use {
            while (it.moveToNext()) names += it.getString(0)
        }
        assertTrue(names.contains("warranty_claims"))
        assertTrue(names.contains("warranty_claim_events"))
        val claims = db.query("SELECT COUNT(*) FROM warranty_claims")
        claims.use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        helper.close()
    }
}
