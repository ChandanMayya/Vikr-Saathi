package com.kex.vikrsaathi.ui.bill

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.draft.HeldDraftSummary
import com.kex.vikrsaathi.data.repository.BillDraftRepository
import kotlinx.coroutines.launch

class HeldBillsViewModel(
    private val billDraftRepository: BillDraftRepository
) : ViewModel() {

    private val _heldBills = MutableLiveData<List<HeldDraftSummary>>(emptyList())
    val heldBills: LiveData<List<HeldDraftSummary>> = _heldBills

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _heldBills.value = billDraftRepository.getAllHeldSummaries()
            _loading.value = false
        }
    }

    fun deleteHeldBill(draftId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            billDraftRepository.deleteHeldBill(draftId)
            _heldBills.value = billDraftRepository.getAllHeldSummaries()
            onDeleted()
        }
    }
}
