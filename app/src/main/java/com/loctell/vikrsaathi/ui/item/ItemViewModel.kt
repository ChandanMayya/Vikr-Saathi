package com.loctell.vikrsaathi.ui.item

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loctell.vikrsaathi.data.entity.Item
import com.loctell.vikrsaathi.data.repository.ItemRepository
import com.loctell.vikrsaathi.data.repository.SettingsRepository
import kotlinx.coroutines.launch

class ItemViewModel(
    private val repository: ItemRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val items: LiveData<List<Item>> = repository.allItems

    val defaultDiscount: Double
        get() = settingsRepository.defaultDiscount

    fun saveItem(item: Item, onResult: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            try {
                val barcode = item.barcode?.trim().orEmpty()
                if (barcode.isNotEmpty() && !repository.isBarcodeUnique(barcode, item.id)) {
                    onResult(Result.failure(IllegalStateException("Barcode already exists")))
                    return@launch
                }
                val id = if (item.id == 0L) {
                    repository.insert(item.copy(barcode = barcode.ifEmpty { null }))
                } else {
                    repository.update(item.copy(barcode = barcode.ifEmpty { null }))
                    item.id
                }
                onResult(Result.success(id))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
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
