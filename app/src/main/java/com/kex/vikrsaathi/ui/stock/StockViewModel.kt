package com.kex.vikrsaathi.ui.stock

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.StockMovement
import com.kex.vikrsaathi.data.repository.InventoryRepository
import com.kex.vikrsaathi.data.repository.ItemRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.data.repository.VendorRepository
import kotlinx.coroutines.launch

class StockViewModel(
    private val itemRepository: ItemRepository,
    private val inventoryRepository: InventoryRepository,
    private val settingsRepository: SettingsRepository,
    private val vendorRepository: VendorRepository
) : ViewModel() {

    val items: LiveData<List<Item>> = itemRepository.allItems

    private val _filteredItems = MutableLiveData<List<Item>>(emptyList())
    val filteredItems: LiveData<List<Item>> = _filteredItems

    private val _movements = MutableLiveData<List<StockMovement>>(emptyList())
    val movements: LiveData<List<StockMovement>> = _movements

    private var allItemsCache: List<Item> = emptyList()
    private var query: String = ""
    private var lowStockOnly: Boolean = false

    val lowStockThreshold: Int
        get() = settingsRepository.lowStockThreshold

    private val _vendorNames = MutableLiveData<Map<Long, String>>(emptyMap())
    val vendorNames: LiveData<Map<Long, String>> = _vendorNames

    init {
        refreshVendorNames()
    }

    fun refreshVendorNames() {
        if (!settingsRepository.vendorManagementEnabled) {
            _vendorNames.value = emptyMap()
            return
        }
        viewModelScope.launch {
            _vendorNames.value = vendorRepository.getAll().associate { it.id to it.name }
        }
    }

    fun onItemsChanged(items: List<Item>) {
        allItemsCache = items
        applyFilter()
    }

    fun setQuery(value: String) {
        query = value.trim()
        applyFilter()
    }

    fun setLowStockOnly(enabled: Boolean) {
        lowStockOnly = enabled
        applyFilter()
    }

    private var allowedIds: Set<Long>? = null

    fun setAllowedItemIds(ids: Set<Long>?) {
        allowedIds = ids
        applyFilter()
    }

    fun adjustStock(itemId: Long, request: BatchAdjustRequest, onResult: (Result<Item>) -> Unit) {
        viewModelScope.launch {
            onResult(
                inventoryRepository.adjustStock(
                    itemId = itemId,
                    delta = request.delta,
                    note = request.note,
                    batchId = request.batchId,
                    lotNumber = request.lotNumber,
                    expiryEpochDay = request.expiryEpochDay,
                    expiryUnknown = request.expiryUnknown
                )
            )
        }
    }

    fun loadMovements(itemId: Long) {
        viewModelScope.launch {
            _movements.value = inventoryRepository.getMovementsForItem(itemId)
        }
    }

    private fun applyFilter() {
        val threshold = lowStockThreshold
        _filteredItems.value = allItemsCache.filter { item ->
            val matchesQuery = query.isEmpty() ||
                item.name.contains(query, ignoreCase = true) ||
                item.barcode?.contains(query, ignoreCase = true) == true
            val matchesStock = !lowStockOnly || item.stockQty <= threshold
            val matchesBatch = allowedIds?.contains(item.id) != false
            matchesQuery && matchesStock && matchesBatch
        }
    }
}
