package com.kex.vikrsaathi.ui.orders

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.repository.VendorRepository
import com.kex.vikrsaathi.domain.finance.HubCounts
import com.kex.vikrsaathi.domain.finance.HubStatusFilter
import com.kex.vikrsaathi.domain.finance.HubTypeFilter
import com.kex.vikrsaathi.domain.finance.OrderHubMath
import com.kex.vikrsaathi.domain.finance.OrderHubRow
import kotlinx.coroutines.launch

class OrdersViewModel(
    private val vendorRepository: VendorRepository
) : ViewModel() {

    private var snapshot: List<OrderHubRow> = emptyList()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _failed = MutableLiveData(false)
    val failed: LiveData<Boolean> = _failed

    private val _rows = MutableLiveData<List<OrderHubRow>>(emptyList())
    val rows: LiveData<List<OrderHubRow>> = _rows

    private val _counts = MutableLiveData(HubCounts(0, 0, 0, 0))
    val counts: LiveData<HubCounts> = _counts

    private val _type = MutableLiveData(HubTypeFilter.ALL)
    val type: LiveData<HubTypeFilter> = _type

    private val _status = MutableLiveData(HubStatusFilter.ALL)
    val status: LiveData<HubStatusFilter> = _status

    private val _from = MutableLiveData<Long?>(null)
    val from: LiveData<Long?> = _from

    private val _to = MutableLiveData<Long?>(null)
    val to: LiveData<Long?> = _to

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _failed.value = false
            runCatching { vendorRepository.orderHubRows() }
                .onSuccess {
                    snapshot = it
                    publish()
                }
                .onFailure {
                    snapshot = emptyList()
                    _failed.value = true
                    publish()
                }
            _loading.value = false
        }
    }

    fun setType(type: HubTypeFilter) {
        if (_type.value == type) return
        _type.value = type
        publish()
    }

    fun setStatus(status: HubStatusFilter) {
        if (_status.value == status) return
        _status.value = status
        publish()
    }

    fun setDates(fromInclusive: Long?, toInclusive: Long?) {
        _from.value = fromInclusive
        _to.value = toInclusive
        publish()
    }

    private fun publish() {
        val type = _type.value ?: HubTypeFilter.ALL
        val status = _status.value ?: HubStatusFilter.ALL
        val from = _from.value
        val to = _to.value
        _counts.value = OrderHubMath.counts(snapshot, type, from, to)
        _rows.value = OrderHubMath.filter(snapshot, type, status, from, to)
    }
}
