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
class Migration19To20Test {
    private lateinit var context: Context
    private val name = "migration-19-20.db"

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
    fun addsWarrantyColumnsWithoutFillingOldSales() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(19) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE items (
                                id INTEGER PRIMARY KEY NOT NULL,
                                name TEXT NOT NULL,
                                mrp REAL NOT NULL,
                                discount REAL NOT NULL,
                                stockQty INTEGER NOT NULL,
                                baseUnit TEXT NOT NULL,
                                purchaseUnit TEXT NOT NULL,
                                unitsPerPurchase INTEGER NOT NULL,
                                trackBatches INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL("INSERT INTO items VALUES (1, 'Pen', 10.0, 0.0, 2, '', '', 1, 0)")
                        db.execSQL(
                            """
                            CREATE TABLE bill_items (
                                id INTEGER PRIMARY KEY NOT NULL,
                                billId INTEGER NOT NULL,
                                itemName TEXT NOT NULL,
                                quantity INTEGER NOT NULL,
                                mrp REAL NOT NULL,
                                discount REAL NOT NULL,
                                finalPrice REAL NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL("INSERT INTO bill_items VALUES (7, 3, 'Pen', 1, 10.0, 0.0, 10.0)")
                        db.execSQL(
                            """
                            CREATE TABLE bill_version_items (
                                id INTEGER PRIMARY KEY NOT NULL,
                                versionId INTEGER NOT NULL,
                                itemName TEXT NOT NULL,
                                quantity INTEGER NOT NULL,
                                mrp REAL NOT NULL,
                                discount REAL NOT NULL,
                                finalPrice REAL NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL("INSERT INTO bill_version_items VALUES (8, 4, 'Pen', 1, 10.0, 0.0, 10.0)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        MIGRATION_19_20.migrate(db)
        val item = db.query("SELECT name, warrantyApplies, warrantyPeriod FROM items")
        item.use {
            it.moveToFirst()
            assertEquals("Pen", it.getString(0))
            assertEquals(0, it.getInt(1))
            assertEquals(0, it.getInt(2))
        }
        val sold = db.query("SELECT warrantyPeriod, warrantyEndEpochDay FROM bill_items")
        sold.use {
            it.moveToFirst()
            assertNull(it.getString(0))
            assertNull(it.getString(1))
        }
        val version = db.query("SELECT itemName, warrantyUnit FROM bill_version_items")
        version.use {
            it.moveToFirst()
            assertEquals("Pen", it.getString(0))
            assertNull(it.getString(1))
        }
        helper.close()
    }
}
