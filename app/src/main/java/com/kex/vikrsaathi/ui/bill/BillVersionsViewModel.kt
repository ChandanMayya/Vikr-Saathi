package com.kex.vikrsaathi.ui.bill

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.model.BillVersionDetails
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import kotlinx.coroutines.launch

class BillVersionsViewModel(
    private val repository: BillRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    data class HistoryState(
        val billNumber: String = "",
        val versions: List<BillVersionDetails> = emptyList()
    )

    private val _history = MutableLiveData(HistoryState())
    val history: LiveData<HistoryState> = _history

    val currencySymbol: String
        get() = settingsRepository.currencySymbol

    fun load(billId: Long) {
        viewModelScope.launch {
            val versions = repository.getVersionHistory(billId)
            val number = versions.firstOrNull()?.version?.billNumber
                ?: repository.getBillWithDetails(billId)?.bill?.billNumber.orEmpty()
            _history.value = HistoryState(billNumber = number, versions = versions)
        }
    }
}
