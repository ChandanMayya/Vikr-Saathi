package com.loctell.vikrsaathi.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.loctell.vikrsaathi.data.dao.BillDao
import com.loctell.vikrsaathi.data.dao.BillItemDao
import com.loctell.vikrsaathi.data.dao.CustomerDao
import com.loctell.vikrsaathi.data.dao.InvoiceTemplateDao
import com.loctell.vikrsaathi.data.dao.InvoiceTemplateVersionDao
import com.loctell.vikrsaathi.data.dao.ItemDao
import com.loctell.vikrsaathi.data.entity.Bill
import com.loctell.vikrsaathi.data.entity.BillItem
import com.loctell.vikrsaathi.data.entity.Customer
import com.loctell.vikrsaathi.data.entity.InvoiceTemplateEntity
import com.loctell.vikrsaathi.data.entity.InvoiceTemplateVersionEntity
import com.loctell.vikrsaathi.data.entity.Item

@Database(
    entities = [
        Customer::class,
        Item::class,
        Bill::class,
        BillItem::class,
        InvoiceTemplateEntity::class,
        InvoiceTemplateVersionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao
    abstract fun itemDao(): ItemDao
    abstract fun billDao(): BillDao
    abstract fun billItemDao(): BillItemDao
    abstract fun invoiceTemplateDao(): InvoiceTemplateDao
    abstract fun invoiceTemplateVersionDao(): InvoiceTemplateVersionDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
