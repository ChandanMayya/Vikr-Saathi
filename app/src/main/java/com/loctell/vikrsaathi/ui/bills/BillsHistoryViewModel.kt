package com.loctell.vikrsaathi.ui.bills

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loctell.vikrsaathi.data.entity.Bill
import com.loctell.vikrsaathi.data.model.BillWithDetails
import com.loctell.vikrsaathi.data.repository.BillRepository
import kotlinx.coroutines.launch

class BillsHistoryViewModel(private val repository: BillRepository) : ViewModel() {

    val allBills: LiveData<List<BillWithDetails>> = repository.allBillsWithDetails

    private val _searchQuery = MutableLiveData("")
    val searchResults: LiveData<List<Bill>> = MutableLiveData()

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch { repository.deleteBill(bill) }
    }

    fun duplicateBill(billId: Long, onDuplicated: (Long) -> Unit) {
        viewModelScope.launch {
            val newId = repository.duplicateBill(billId)
            if (newId != null) onDuplicated(newId)
        }
    }

    fun getBillDetails(billId: Long, callback: (BillWithDetails?) -> Unit) {
        viewModelScope.launch { callback(repository.getBillWithDetails(billId)) }
    }
}
