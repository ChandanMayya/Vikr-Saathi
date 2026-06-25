package com.kex.vikrsaathi.ui.bills

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.CustomerRepository
import com.kex.vikrsaathi.data.repository.ItemRepository
import com.kex.vikrsaathi.util.SalesImportResult
import com.kex.vikrsaathi.util.SalesReportImporter
import com.kex.vikrsaathi.util.SalesReportRow
import com.kex.vikrsaathi.util.SalesReportExcelParser
import kotlinx.coroutines.launch
import android.content.Context

class ExcelUploadViewModel(
    private val billRepository: BillRepository,
    private val customerRepository: CustomerRepository,
    private val itemRepository: ItemRepository
) : ViewModel() {

    private val importer by lazy {
        SalesReportImporter(billRepository, customerRepository, itemRepository)
    }

    private val _previewRows = MutableLiveData<List<SalesReportRow>>(emptyList())
    val previewRows: LiveData<List<SalesReportRow>> = _previewRows

    private val _selectedUri = MutableLiveData<Uri?>()
    val selectedUri: LiveData<Uri?> = _selectedUri

    fun loadPreview(context: Context, uri: Uri) {
        _selectedUri.value = uri
        viewModelScope.launch {
            val rows = SalesReportExcelParser.parse(context, uri)
            _previewRows.value = rows
        }
    }

    fun importFile(context: Context, onResult: (SalesImportResult) -> Unit) {
        val uri = _selectedUri.value ?: return
        viewModelScope.launch {
            onResult(importer.import(context, uri))
        }
    }
}
