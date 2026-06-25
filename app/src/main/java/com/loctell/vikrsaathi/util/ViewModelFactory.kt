package com.loctell.vikrsaathi.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.loctell.vikrsaathi.VikrSaathiApp
import com.loctell.vikrsaathi.ui.bill.BillViewModel
import com.loctell.vikrsaathi.ui.bills.BillsHistoryViewModel
import com.loctell.vikrsaathi.ui.customer.CustomerViewModel
import com.loctell.vikrsaathi.ui.dashboard.DashboardViewModel
import com.loctell.vikrsaathi.ui.item.ItemViewModel
import com.loctell.vikrsaathi.ui.settings.SettingsViewModel

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
                    app.settingsRepository
                ) as T
            modelClass.isAssignableFrom(BillsHistoryViewModel::class.java) ->
                BillsHistoryViewModel(app.billRepository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(app.settingsRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
