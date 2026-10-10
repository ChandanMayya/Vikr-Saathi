package com.kex.vikrsaathi.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.model.DashboardTodayStats
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.CustomerFinanceRepository
import com.kex.vikrsaathi.data.repository.InventoryRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.data.repository.VendorRepository
import kotlinx.coroutines.launch

data class LowStockRow(
    val name: String,
    val stockQty: Int
)

data class LowStockSummary(
    val count: Int,
    val items: List<LowStockRow>
)

class DashboardViewModel(
    private val settingsRepository: SettingsRepository,
    private val billRepository: BillRepository,
    private val inventoryRepository: InventoryRepository,
    private val customerFinanceRepository: CustomerFinanceRepository,
    private val vendorRepository: VendorRepository
) : ViewModel() {

    private val _shopName = MutableLiveData<String>()
    val shopName: LiveData<String> = _shopName

    private val _currencySymbol = MutableLiveData("₹")
    val currencySymbol: LiveData<String> = _currencySymbol

    private val _todayStats = MutableLiveData(DashboardTodayStats.EMPTY)
    val todayStats: LiveData<DashboardTodayStats> = _todayStats

    private val _lowStock = MutableLiveData(LowStockSummary(0, emptyList()))
    val lowStock: LiveData<LowStockSummary> = _lowStock

    private val _customerDuesPaise = MutableLiveData(0L)
    val customerDuesPaise: LiveData<Long> = _customerDuesPaise

    private val _vendorPayablesPaise = MutableLiveData(0L)
    val vendorPayablesPaise: LiveData<Long> = _vendorPayablesPaise

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _shopName.value = settingsRepository.shopName
            _currencySymbol.value = settingsRepository.currencySymbol
            _todayStats.value = billRepository.getDashboardTodayStats()
            val threshold = settingsRepository.lowStockThreshold
            val lowItems: List<Item> = inventoryRepository.getLowStockItems(threshold)
            _lowStock.value = LowStockSummary(
                count = lowItems.size,
                items = lowItems.take(5).map { LowStockRow(it.name, it.stockQty) }
            )
            _customerDuesPaise.value = if (settingsRepository.customerBalanceEnabled) {
                customerFinanceRepository.totalReceivablePaise()
            } else 0L
            _vendorPayablesPaise.value = if (settingsRepository.vendorManagementEnabled) {
                vendorRepository.totalPayablePaise()
            } else 0L
            _loading.value = false
        }
    }
}
