package com.kex.vikrsaathi.data.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kex.vikrsaathi.data.database.MIGRATION_15_16
import com.kex.vikrsaathi.data.database.MIGRATION_16_17
import com.kex.vikrsaathi.data.database.MIGRATION_17_18
import com.kex.vikrsaathi.data.entity.Bill
import kotlinx.coroutines.runBlocking
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
class Migration14To15Test {

    private lateinit var context: Context
    private val name = "migration-14-15.db"

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
    fun migratesExistingBillsWithoutInventingVersionHistory() = runBlocking {
        val created = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        val billId = created.billDao().insertBill(
            Bill(
                billNumber = "LEGACY-1",
                invoiceCounter = 4,
                total = 15.0,
                roundOff = 0.5,
                date = 1_600_000_000_000,
                paymentStatus = "LEGACY",
                paymentLabel = "LEGACY"
            )
        )
        created.close()

        val downgraded = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        db.execSQL("DROP TABLE IF EXISTS bill_version_payments")
                        db.execSQL("DROP TABLE IF EXISTS bill_version_items")
                        db.execSQL("DROP TABLE IF EXISTS bill_versions")
                        db.execSQL("DROP TABLE IF EXISTS item_cost_lots")
                        db.execSQL("DROP TABLE IF EXISTS operators")
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS bill_items_v15 (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                billId INTEGER NOT NULL,
                                itemId INTEGER,
                                itemName TEXT NOT NULL,
                                quantity INTEGER NOT NULL,
                                mrp REAL NOT NULL,
                                discount REAL NOT NULL,
                                finalPrice REAL NOT NULL,
                                roundOff REAL NOT NULL,
                                FOREIGN KEY(billId) REFERENCES bills(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE SET NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO bill_items_v15 (id, billId, itemId, itemName, quantity, mrp, discount, finalPrice, roundOff)
                            SELECT id, billId, itemId, itemName, quantity, mrp, discount, finalPrice, roundOff FROM bill_items
                            """.trimIndent()
                        )
                        db.execSQL("DROP TABLE bill_items")
                        db.execSQL("ALTER TABLE bill_items_v15 RENAME TO bill_items")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_items_billId ON bill_items(billId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_items_itemId ON bill_items(itemId)")
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS sales_return_lines_v15 (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                returnId INTEGER NOT NULL,
                                itemId INTEGER,
                                itemName TEXT NOT NULL,
                                quantity INTEGER NOT NULL,
                                FOREIGN KEY(returnId) REFERENCES sales_returns(id) ON UPDATE NO ACTION ON DELETE CASCADE
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO sales_return_lines_v15 (id, returnId, itemId, itemName, quantity)
                            SELECT id, returnId, itemId, itemName, quantity FROM sales_return_lines
                            """.trimIndent()
                        )
                        db.execSQL("DROP TABLE sales_return_lines")
                        db.execSQL("ALTER TABLE sales_return_lines_v15 RENAME TO sales_return_lines")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_return_lines_returnId ON sales_return_lines(returnId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_return_lines_itemId ON sales_return_lines(itemId)")
                    }
                })
                .build()
        )
        downgraded.writableDatabase.close()
        downgraded.close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
            .allowMainThreadQueries()
            .build()
        val readable = migrated.openHelper.writableDatabase
        assertEquals(18, readable.version)
        val table = readable.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'bill_versions'"
        )
        assertTrue(table.moveToFirst())
        table.close()

        val bill = migrated.billDao().getBillById(billId)
        assertEquals("LEGACY-1", bill?.billNumber)
        assertEquals(15.0, bill?.total)
        assertEquals(0.5, bill?.roundOff)
        assertEquals(1_600_000_000_000, bill?.date)
        assertEquals(0, migrated.billVersionDao().countForBill(billId))
        migrated.close()
    }
}
