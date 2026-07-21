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
import kotlinx.coroutines.launch

class StockViewModel(
    private val itemRepository: ItemRepository,
    private val inventoryRepository: InventoryRepository,
    private val settingsRepository: SettingsRepository
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

    fun adjustStock(itemId: Long, delta: Int, note: String?, onResult: (Result<Item>) -> Unit) {
        viewModelScope.launch {
            onResult(inventoryRepository.adjustStock(itemId, delta, note))
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
            matchesQuery && matchesStock
        }
    }
}
