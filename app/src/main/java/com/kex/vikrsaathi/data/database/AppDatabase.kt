package com.kex.vikrsaathi.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kex.vikrsaathi.data.dao.BillDao
import com.kex.vikrsaathi.data.dao.BillDraftDao
import com.kex.vikrsaathi.data.dao.BillItemDao
import com.kex.vikrsaathi.data.dao.CustomerDao
import com.kex.vikrsaathi.data.dao.InvoiceTemplateDao
import com.kex.vikrsaathi.data.dao.InvoiceTemplateVersionDao
import com.kex.vikrsaathi.data.dao.ItemDao
import com.kex.vikrsaathi.data.dao.StockMovementDao
import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.entity.BillDraftEntity
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.entity.InvoiceTemplateEntity
import com.kex.vikrsaathi.data.entity.InvoiceTemplateVersionEntity
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.StockMovement

@Database(
    entities = [
        Customer::class,
        Item::class,
        Bill::class,
        BillItem::class,
        InvoiceTemplateEntity::class,
        InvoiceTemplateVersionEntity::class,
        BillDraftEntity::class,
        StockMovement::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao
    abstract fun itemDao(): ItemDao
    abstract fun billDao(): BillDao
    abstract fun billDraftDao(): BillDraftDao
    abstract fun billItemDao(): BillItemDao
    abstract fun invoiceTemplateDao(): InvoiceTemplateDao
    abstract fun invoiceTemplateVersionDao(): InvoiceTemplateVersionDao
    abstract fun stockMovementDao(): StockMovementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vikr_saathi.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
