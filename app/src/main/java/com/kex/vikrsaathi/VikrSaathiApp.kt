package com.kex.vikrsaathi

import android.app.Application
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.repository.BillDraftRepository
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.CustomerRepository
import com.kex.vikrsaathi.data.repository.InventoryRepository
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.kex.vikrsaathi.data.repository.ItemRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.data.backup.BackupManager
import com.kex.vikrsaathi.data.reset.ResetHistoryStore
import com.kex.vikrsaathi.data.reset.ResetManager
import com.kex.vikrsaathi.util.BillsHistoryPreferences
import com.kex.vikrsaathi.util.InvoiceBuilderPreferences
import com.kex.vikrsaathi.util.AppThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VikrSaathiApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getInstance(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val customerRepository by lazy { CustomerRepository(database.customerDao()) }
    val itemRepository by lazy { ItemRepository(database.itemDao()) }
    val inventoryRepository by lazy {
        InventoryRepository(
            database = database,
            itemDao = database.itemDao(),
            stockMovementDao = database.stockMovementDao()
        )
    }
    val billRepository by lazy {
        BillRepository(
            database.billDao(),
            database.billItemDao(),
            settingsRepository,
            inventoryRepository
        )
    }
    val billDraftRepository by lazy { BillDraftRepository(database.billDraftDao()) }
    val invoiceTemplateRepository by lazy {
        InvoiceTemplateRepository(
            database.invoiceTemplateDao(),
            database.invoiceTemplateVersionDao()
        )
    }
    val invoiceBuilderPreferences by lazy { InvoiceBuilderPreferences(this) }
    val billsHistoryPreferences by lazy { BillsHistoryPreferences(this) }
    val backupManager by lazy {
        BackupManager(
            context = this,
            database = database,
            settingsRepository = settingsRepository,
            customerRepository = customerRepository,
            itemRepository = itemRepository,
            billRepository = billRepository,
            invoiceTemplateRepository = invoiceTemplateRepository,
            inventoryRepository = inventoryRepository
        )
    }
    val resetHistoryStore by lazy { ResetHistoryStore(this) }
    val resetManager by lazy {
        ResetManager(
            context = this,
            database = database,
            backupManager = backupManager,
            settingsRepository = settingsRepository,
            invoiceTemplateRepository = invoiceTemplateRepository,
            invoiceBuilderPreferences = invoiceBuilderPreferences,
            historyStore = resetHistoryStore
        )
    }

    override fun onCreate() {
        super.onCreate()
        AppThemeManager.apply(settingsRepository.themeMode)
        settingsRepository.copyAssetHeaderIfNeeded(this)
        appScope.launch {
            invoiceTemplateRepository.ensureDefaultTemplateExists()
        }
    }
}
