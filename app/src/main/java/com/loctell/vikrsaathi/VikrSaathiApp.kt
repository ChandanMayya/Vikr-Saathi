package com.loctell.vikrsaathi

import android.app.Application
import com.loctell.vikrsaathi.data.database.AppDatabase
import com.loctell.vikrsaathi.data.repository.BillRepository
import com.loctell.vikrsaathi.data.repository.CustomerRepository
import com.loctell.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.loctell.vikrsaathi.data.repository.ItemRepository
import com.loctell.vikrsaathi.data.repository.SettingsRepository
import com.loctell.vikrsaathi.util.BillsHistoryPreferences
import com.loctell.vikrsaathi.util.InvoiceBuilderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VikrSaathiApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getInstance(this) }
    val customerRepository by lazy { CustomerRepository(database.customerDao()) }
    val itemRepository by lazy { ItemRepository(database.itemDao()) }
    val billRepository by lazy {
        BillRepository(
            database.billDao(),
            database.billItemDao(),
            settingsRepository
        )
    }
    val settingsRepository by lazy { SettingsRepository(this) }
    val invoiceTemplateRepository by lazy {
        InvoiceTemplateRepository(
            database.invoiceTemplateDao(),
            database.invoiceTemplateVersionDao()
        )
    }
    val invoiceBuilderPreferences by lazy { InvoiceBuilderPreferences(this) }
    val billsHistoryPreferences by lazy { BillsHistoryPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        settingsRepository.copyAssetHeaderIfNeeded(this)
        appScope.launch {
            invoiceTemplateRepository.ensureDefaultTemplateExists()
        }
    }
}
