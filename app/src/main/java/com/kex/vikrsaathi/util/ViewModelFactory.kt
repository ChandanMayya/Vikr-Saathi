package com.kex.vikrsaathi.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.ui.bill.BillViewModel
import com.kex.vikrsaathi.ui.bills.BillsHistoryViewModel
import com.kex.vikrsaathi.ui.bills.ExcelUploadViewModel
import com.kex.vikrsaathi.ui.customer.CustomerViewModel
import com.kex.vikrsaathi.ui.dashboard.DashboardViewModel
import com.kex.vikrsaathi.ui.item.ItemViewModel
import com.kex.vikrsaathi.ui.settings.SettingsViewModel
import com.kex.vikrsaathi.ui.settings.InvoiceTemplatesViewModel
import com.kex.vikrsaathi.ui.settings.backup.BackupViewModel
import com.kex.vikrsaathi.ui.settings.invoicebuilder.InvoiceBuilderViewModel

class ViewModelFactory(private val app: VikrSaathiApp) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(app.settingsRepository) as T
            modelClass.isAssignableFrom(CustomerViewModel::class.java) ->
                CustomerViewModel(app.customerRepository) as T
            modelClass.isAssignableFrom(ItemViewModel::class.java) ->
                ItemViewModel(app.itemRepository, app.settingsRepository) as T
            modelClass.isAssignableFrom(BillViewModel::class.java) ->
                BillViewModel(
                    app.customerRepository,
                    app.itemRepository,
                    app.billRepository,
                    app.settingsRepository,
                    app.invoiceTemplateRepository
                ) as T
            modelClass.isAssignableFrom(BillsHistoryViewModel::class.java) ->
                BillsHistoryViewModel(
                    app.billRepository,
                    app.settingsRepository,
                    app.customerRepository,
                    app.itemRepository
                ) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(app.settingsRepository) as T
            modelClass.isAssignableFrom(InvoiceTemplatesViewModel::class.java) ->
                InvoiceTemplatesViewModel(app.invoiceTemplateRepository) as T
            modelClass.isAssignableFrom(InvoiceBuilderViewModel::class.java) ->
                InvoiceBuilderViewModel(
                    app.invoiceTemplateRepository,
                    app.settingsRepository,
                    app.invoiceBuilderPreferences
                ) as T
            modelClass.isAssignableFrom(ExcelUploadViewModel::class.java) ->
                ExcelUploadViewModel(
                    app.billRepository,
                    app.customerRepository,
                    app.itemRepository
                ) as T
            modelClass.isAssignableFrom(BackupViewModel::class.java) ->
                BackupViewModel(app.backupManager) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
