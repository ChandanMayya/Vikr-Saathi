package com.loctell.vikrsaathi

import android.app.Application
import com.loctell.vikrsaathi.data.database.AppDatabase
import com.loctell.vikrsaathi.data.repository.BillRepository
import com.loctell.vikrsaathi.data.repository.CustomerRepository
import com.loctell.vikrsaathi.data.repository.ItemRepository
import com.loctell.vikrsaathi.data.repository.SettingsRepository

class VikrSaathiApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val customerRepository by lazy { CustomerRepository(database.customerDao()) }
    val itemRepository by lazy { ItemRepository(database.itemDao()) }
    val billRepository by lazy {
        BillRepository(database.billDao(), database.billItemDao())
    }
    val settingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        settingsRepository.copyAssetHeaderIfNeeded(this)
    }
}
