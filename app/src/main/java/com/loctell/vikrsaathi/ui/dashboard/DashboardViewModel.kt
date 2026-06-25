package com.loctell.vikrsaathi.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.loctell.vikrsaathi.data.repository.SettingsRepository

class DashboardViewModel(settingsRepository: SettingsRepository) : ViewModel() {

    private val _shopName = MutableLiveData(settingsRepository.shopName)
    val shopName: LiveData<String> = _shopName

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun refresh(settingsRepository: SettingsRepository) {
        _loading.value = true
        _shopName.value = settingsRepository.shopName
        _loading.value = false
    }
}
