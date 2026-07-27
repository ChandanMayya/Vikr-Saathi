package com.kex.vikrsaathi.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.ui.analysis.AnalysisViewModel
import com.kex.vikrsaathi.ui.bill.BillPreviewViewModel
import com.kex.vikrsaathi.ui.bill.BillViewModel
import com.kex.vikrsaathi.ui.bill.HeldBillsViewModel
import com.kex.vikrsaathi.ui.bills.BillsHistoryViewModel
import com.kex.vikrsaathi.ui.bills.ExcelUploadViewModel
import com.kex.vikrsaathi.ui.customer.CustomerViewModel
import com.kex.vikrsaathi.ui.dashboard.DashboardViewModel
import com.kex.vikrsaathi.ui.item.ItemViewModel
import com.kex.vikrsaathi.ui.settings.SettingsViewModel
import com.kex.vikrsaathi.ui.settings.InvoiceTemplatesViewModel
import com.kex.vikrsaathi.ui.settings.backup.BackupViewModel
import com.kex.vikrsaathi.ui.settings.reset.ResetViewModel
import com.kex.vikrsaathi.ui.settings.invoicebuilder.InvoiceBuilderViewModel
import com.kex.vikrsaathi.ui.stock.StockViewModel

class ViewModelFactory(private val app: VikrSaathiApp) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(
                    app.settingsRepository,
                    app.billRepository,
                    app.inventoryRepository
                ) as T
            modelClass.isAssignableFrom(AnalysisViewModel::class.java) ->
                AnalysisViewModel(
                    app.analyticsRepository,
                    app.settingsRepository
                ) as T
            modelClass.isAssignableFrom(CustomerViewModel::class.java) ->
                CustomerViewModel(app.customerRepository) as T
            modelClass.isAssignableFrom(ItemViewModel::class.java) ->
                ItemViewModel(
                    app.itemRepository,
                    app.settingsRepository,
                    app.inventoryRepository
                ) as T
            modelClass.isAssignableFrom(StockViewModel::class.java) ->
                StockViewModel(
                    app.itemRepository,
                    app.inventoryRepository,
                    app.settingsRepository
                ) as T
            modelClass.isAssignableFrom(BillViewModel::class.java) ->
                BillViewModel(
                    app.customerRepository,
                    app.itemRepository,
                    app.billRepository,
                    app.billDraftRepository,
                    app.settingsRepository,
                    app.invoiceTemplateRepository,
                    app.inventoryRepository
                ) as T
            modelClass.isAssignableFrom(BillPreviewViewModel::class.java) ->
                BillPreviewViewModel(
                    app.billRepository,
                    app.settingsRepository,
                    app.invoiceTemplateRepository
                ) as T
            modelClass.isAssignableFrom(HeldBillsViewModel::class.java) ->
                HeldBillsViewModel(app.billDraftRepository) as T
            modelClass.isAssignableFrom(BillsHistoryViewModel::class.java) ->
                BillsHistoryViewModel(
                    app.billRepository,
                    app.settingsRepository,
                    app.customerRepository,
                    app.itemRepository
                ) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(app.settingsRepository, app.invoiceTemplateRepository) as T
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
            modelClass.isAssignableFrom(ResetViewModel::class.java) ->
                ResetViewModel(app.resetManager) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
