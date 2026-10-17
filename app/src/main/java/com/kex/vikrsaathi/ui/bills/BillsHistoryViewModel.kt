package com.kex.vikrsaathi.ui.bills

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.CustomerRepository
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.kex.vikrsaathi.data.repository.ItemRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.util.BillsFilterHelper
import com.kex.vikrsaathi.util.SalesImportResult
import com.kex.vikrsaathi.util.SalesReportExcelExporter
import com.kex.vikrsaathi.util.SalesReportFilter
import com.kex.vikrsaathi.util.SalesReportImporter
import com.kex.vikrsaathi.util.PdfGenerator
import com.kex.vikrsaathi.util.SalesReportPdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BillsHistoryViewModel(
    private val repository: BillRepository,
    private val settingsRepository: SettingsRepository,
    private val customerRepository: CustomerRepository,
    private val itemRepository: ItemRepository,
    private val invoiceTemplateRepository: InvoiceTemplateRepository
) : ViewModel() {

    val allBills: LiveData<List<BillWithDetails>> = repository.allBillsWithDetails

    /** Catalog stock-unit labels, loaded once for the history list rather than per bill. */
    val itemUnits: LiveData<Map<Long, String>> = itemRepository.labeledUnits.map { rows ->
        rows.associate { it.id to it.baseUnit.trim() }
    }

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

    fun printBillPdf(context: Context, billId: Long, onResult: (File?) -> Unit) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val bill = repository.getBillWithDetails(billId) ?: return@withContext null
                val template = invoiceTemplateRepository.getDefaultTemplate()
                PdfGenerator.generateBillPdf(
                    context = appContext,
                    template = template,
                    bill = bill,
                    shopName = settingsRepository.shopName,
                    currencySymbol = settingsRepository.currencySymbol,
                    headerImage = settingsRepository.getHeaderImage(),
                    signatureImage = settingsRepository.getSignatureImage(),
                    shopLogoImage = settingsRepository.getShopLogoImage()
                )
            }
            onResult(file)
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
