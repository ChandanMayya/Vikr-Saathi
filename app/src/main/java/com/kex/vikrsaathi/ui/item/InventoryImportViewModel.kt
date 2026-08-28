package com.kex.vikrsaathi.ui.item

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.repository.InventoryRepository
import com.kex.vikrsaathi.data.repository.ItemRepository
import com.kex.vikrsaathi.util.ItemCatalogExcelParser
import com.kex.vikrsaathi.util.ItemCatalogImportResult
import com.kex.vikrsaathi.util.ItemCatalogImporter
import com.kex.vikrsaathi.util.ItemCatalogRow
import kotlinx.coroutines.launch
import java.io.File

class InventoryImportViewModel(
    private val itemRepository: ItemRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val importer by lazy {
        ItemCatalogImporter(itemRepository, inventoryRepository)
    }

    private val _previewRows = MutableLiveData<List<ItemCatalogRow>>(emptyList())
    val previewRows: LiveData<List<ItemCatalogRow>> = _previewRows

    private val _selectedUri = MutableLiveData<Uri?>()
    val selectedUri: LiveData<Uri?> = _selectedUri

    private val _importing = MutableLiveData(false)
    val importing: LiveData<Boolean> = _importing

    fun loadPreview(context: Context, uri: Uri) {
        _selectedUri.value = uri
        viewModelScope.launch {
            _previewRows.value = ItemCatalogExcelParser.parse(context, uri)
        }
    }

    fun importFile(context: Context, onResult: (ItemCatalogImportResult) -> Unit) {
        val uri = _selectedUri.value ?: return
        viewModelScope.launch {
            _importing.value = true
            try {
                onResult(importer.import(context, uri))
            } finally {
                _importing.value = false
            }
        }
    }

    fun exportItems(context: Context, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val items = itemRepository.getAllSync()
            if (items.isEmpty()) {
                onResult(null)
            } else {
                onResult(com.kex.vikrsaathi.util.ItemCatalogExcelExporter.exportItems(context, items))
            }
        }
    }
}
