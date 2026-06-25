package com.loctell.vikrsaathi.ui.bills

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loctell.vikrsaathi.data.entity.Bill
import com.loctell.vikrsaathi.data.model.BillWithDetails
import com.loctell.vikrsaathi.data.repository.BillRepository
import com.loctell.vikrsaathi.data.repository.CustomerRepository
import com.loctell.vikrsaathi.data.repository.ItemRepository
import com.loctell.vikrsaathi.data.repository.SettingsRepository
import com.loctell.vikrsaathi.util.BillsFilterHelper
import com.loctell.vikrsaathi.util.SalesImportResult
import com.loctell.vikrsaathi.util.SalesReportExcelExporter
import com.loctell.vikrsaathi.util.SalesReportFilter
import com.loctell.vikrsaathi.util.SalesReportImporter
import com.loctell.vikrsaathi.util.SalesReportPdfGenerator
import kotlinx.coroutines.launch
import java.io.File

class BillsHistoryViewModel(
    private val repository: BillRepository,
    private val settingsRepository: SettingsRepository,
    private val customerRepository: CustomerRepository,
    private val itemRepository: ItemRepository
) : ViewModel() {

    val allBills: LiveData<List<BillWithDetails>> = repository.allBillsWithDetails

    private val _searchQuery = MutableLiveData("")
    val searchResults: LiveData<List<Bill>> = MutableLiveData()

    private val importer by lazy {
        SalesReportImporter(repository, customerRepository, itemRepository)
    }

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

    fun exportPdfReport(
        context: Context,
        bills: List<BillWithDetails>,
        filter: SalesReportFilter,
        onResult: (File?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val filtered = BillsFilterHelper.apply(bills, filter)
                if (filtered.isEmpty()) {
                    onResult(null)
                    return@launch
                }
                val file = SalesReportPdfGenerator.generate(
                    context = context,
                    bills = filtered,
                    shopName = settingsRepository.shopName,
                    currencySymbol = settingsRepository.currencySymbol,
                    headerImage = settingsRepository.getHeaderImage(),
                    filterSummary = BillsFilterHelper.buildSummary(filter)
                )
                onResult(file)
            } catch (_: Exception) {
                onResult(null)
            }
        }
    }

    fun exportExcelReport(
        context: Context,
        bills: List<BillWithDetails>,
        filter: SalesReportFilter,
        onResult: (File?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val filtered = BillsFilterHelper.apply(bills, filter)
                if (filtered.isEmpty()) {
                    onResult(null)
                    return@launch
                }
                val file = SalesReportExcelExporter.export(context, filtered)
                onResult(file)
            } catch (_: Exception) {
                onResult(null)
            }
        }
    }

    fun importExcelBackup(
        context: Context,
        uri: Uri,
        onResult: (SalesImportResult) -> Unit
    ) {
        viewModelScope.launch {
            try {
                onResult(importer.import(context, uri))
            } catch (e: Exception) {
                onResult(
                    SalesImportResult(
                        imported = 0,
                        skipped = 0,
                        errors = listOf(e.message ?: "Import failed")
                    )
                )
            }
        }
    }
}
