package com.kex.vikrsaathi

import android.app.Application
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.CustomerRepository
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.kex.vikrsaathi.data.repository.ItemRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.util.BillsHistoryPreferences
import com.kex.vikrsaathi.util.InvoiceBuilderPreferences
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
