package com.kex.vikrsaathi.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.model.DashboardTodayStats
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val settingsRepository: SettingsRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    private val _shopName = MutableLiveData<String>()
    val shopName: LiveData<String> = _shopName

    private val _currencySymbol = MutableLiveData("₹")
    val currencySymbol: LiveData<String> = _currencySymbol

    private val _todayStats = MutableLiveData(DashboardTodayStats.EMPTY)
    val todayStats: LiveData<DashboardTodayStats> = _todayStats

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _shopName.value = settingsRepository.shopName
            _currencySymbol.value = settingsRepository.currencySymbol
            _todayStats.value = billRepository.getDashboardTodayStats()
            _loading.value = false
        }
    }
}
