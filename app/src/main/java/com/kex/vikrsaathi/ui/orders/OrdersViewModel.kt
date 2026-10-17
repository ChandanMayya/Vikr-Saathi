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

/**
 * One filter record for the Orders list and its options panel.
 * A switch can turn a filter off without discarding the selection underneath it.
 */
data class OrdersFilterState(
    val dateEnabled: Boolean = false,
    val from: Long? = null,
    val to: Long? = null,
    val typeEnabled: Boolean = false,
    val type: HubTypeFilter = HubTypeFilter.ALL,
    val statusEnabled: Boolean = false,
    val status: HubStatusFilter = HubStatusFilter.ALL
) {
    val appliedType: HubTypeFilter
        get() = if (typeEnabled) type else HubTypeFilter.ALL

    val appliedStatus: HubStatusFilter
        get() = if (statusEnabled) status else HubStatusFilter.ALL

    val appliedFrom: Long?
        get() = if (dateEnabled) from else null

    val appliedTo: Long?
        get() = if (dateEnabled) to else null

    val narrowsResults: Boolean
        get() = appliedType != HubTypeFilter.ALL ||
            appliedStatus != HubStatusFilter.ALL ||
            (appliedFrom != null && appliedTo != null)
}

class OrdersViewModel(
    private val vendorRepository: VendorRepository
) : ViewModel() {

    private var snapshot: List<OrderHubRow> = emptyList()
    private var filter = OrdersFilterState()

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private val _failed = MutableLiveData(false)
    val failed: LiveData<Boolean> = _failed

    private val _rows = MutableLiveData<List<OrderHubRow>>(emptyList())
    val rows: LiveData<List<OrderHubRow>> = _rows

    private val _counts = MutableLiveData(HubCounts(0, 0, 0, 0))
    val counts: LiveData<HubCounts> = _counts

    private val _filterState = MutableLiveData(filter)
    val filterState: LiveData<OrdersFilterState> = _filterState

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

    fun setDateEnabled(enabled: Boolean) = update { it.copy(dateEnabled = enabled) }

    fun setDates(fromInclusive: Long?, toInclusive: Long?) = update {
        it.copy(from = fromInclusive, to = toInclusive)
    }

    fun setTypeEnabled(enabled: Boolean) = update { it.copy(typeEnabled = enabled) }

    fun setType(type: HubTypeFilter) = update { it.copy(type = type) }

    /** Used when another screen opens Orders already limited to one record type. */
    fun selectType(type: HubTypeFilter) = update { it.copy(type = type, typeEnabled = true) }

    fun setStatusEnabled(enabled: Boolean) = update { it.copy(statusEnabled = enabled) }

    fun setStatus(status: HubStatusFilter) = update { it.copy(status = status) }

    fun clearFilters() = update { OrdersFilterState() }

    private fun update(block: (OrdersFilterState) -> OrdersFilterState) {
        val next = block(filter)
        if (next == filter) return
        filter = next
        _filterState.value = next
        publish()
    }

    private fun publish() {
        val type = filter.appliedType
        val status = filter.appliedStatus
        val from = filter.appliedFrom
        val to = filter.appliedTo
        _counts.value = OrderHubMath.counts(snapshot, type, from, to)
        _rows.value = OrderHubMath.filter(snapshot, type, status, from, to)
    }
}
