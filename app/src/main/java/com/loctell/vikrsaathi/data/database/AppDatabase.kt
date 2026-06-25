package com.loctell.vikrsaathi.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.loctell.vikrsaathi.data.dao.BillDao
import com.loctell.vikrsaathi.data.dao.BillItemDao
import com.loctell.vikrsaathi.data.dao.CustomerDao
import com.loctell.vikrsaathi.data.dao.ItemDao
import com.loctell.vikrsaathi.data.entity.Bill
import com.loctell.vikrsaathi.data.entity.BillItem
import com.loctell.vikrsaathi.data.entity.Customer
import com.loctell.vikrsaathi.data.entity.Item

@Database(
    entities = [Customer::class, Item::class, Bill::class, BillItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao
    abstract fun itemDao(): ItemDao
    abstract fun billDao(): BillDao
    abstract fun billItemDao(): BillItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vikr_saathi.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
