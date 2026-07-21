package com.kex.vikrsaathi.ui.item

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.repository.InventoryRepository
import com.kex.vikrsaathi.data.repository.ItemRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import kotlinx.coroutines.launch

class ItemViewModel(
    private val repository: ItemRepository,
    private val settingsRepository: SettingsRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    val items: LiveData<List<Item>> = repository.allItems

    val defaultDiscount: Double
        get() = settingsRepository.defaultDiscount

    val lowStockThreshold: Int
        get() = settingsRepository.lowStockThreshold

    fun saveItem(
        item: Item,
        openingStock: Int = 0,
        onResult: (Result<Long>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val barcode = item.barcode?.trim().orEmpty()
                if (barcode.isNotEmpty() && !repository.isBarcodeUnique(barcode, item.id)) {
                    onResult(Result.failure(IllegalStateException("Barcode already exists")))
                    return@launch
                }
                val sanitized = item.copy(barcode = barcode.ifEmpty { null })
                val id = if (sanitized.id == 0L) {
                    val newId = repository.insert(sanitized)
                    if (openingStock > 0) {
                        inventoryRepository.setOpeningStock(newId, openingStock)
                    }
                    newId
                } else {
                    val existing = repository.getById(sanitized.id)
                    repository.update(
                        sanitized.copy(stockQty = existing?.stockQty ?: sanitized.stockQty)
                    )
                    sanitized.id
                }
                onResult(Result.success(id))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun adjustStock(itemId: Long, delta: Int, note: String?, onResult: (Result<Item>) -> Unit) {
        viewModelScope.launch {
            onResult(inventoryRepository.adjustStock(itemId, delta, note))
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch { repository.delete(item) }
    }

    fun searchByName(query: String, callback: (List<Item>) -> Unit) {
        viewModelScope.launch { callback(repository.searchByName(query)) }
    }

    fun getByBarcode(barcode: String, callback: (Item?) -> Unit) {
        viewModelScope.launch { callback(repository.getByBarcode(barcode)) }
    }
}
