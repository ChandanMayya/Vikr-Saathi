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
import com.kex.vikrsaathi.data.repository.CustomerFinanceRepository
import com.kex.vikrsaathi.data.repository.CustomerRepository
import com.kex.vikrsaathi.data.repository.InsufficientStockException
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.kex.vikrsaathi.data.repository.ItemRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.data.repository.StockShortfall
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.ui.finance.FinanceUi
import com.kex.vikrsaathi.util.InventoryMode
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

data class ExchangeCardLink(
    val role: com.kex.vikrsaathi.domain.bill.ExchangeHistory.Role,
    val otherBillId: Long,
    val otherNumber: String,
    val returnedQuantity: Int
)

class BillsHistoryViewModel(
    private val repository: BillRepository,
    private val finance: CustomerFinanceRepository,
    private val settingsRepository: SettingsRepository,
    private val customerRepository: CustomerRepository,
    private val itemRepository: ItemRepository,
    private val invoiceTemplateRepository: InvoiceTemplateRepository
) : ViewModel() {

    val allBills: LiveData<List<BillWithDetails>> = repository.allBillsWithDetails

    private val _exchangeCards = MutableLiveData<Map<Long, ExchangeCardLink>>(emptyMap())
    val exchangeCards: LiveData<Map<Long, ExchangeCardLink>> = _exchangeCards

    /** Catalog stock-unit labels, loaded once for the history list rather than per bill. */
    val itemUnits: LiveData<Map<Long, String>> = itemRepository.labeledUnits.map { rows ->
        rows.associate { it.id to it.baseUnit.trim() }
    }

    private val _searchQuery = MutableLiveData("")
    val searchResults: LiveData<List<Bill>> = MutableLiveData()

    private val importer by lazy {
        SalesReportImporter(repository, customerRepository, itemRepository)
    }

    val inventoryMode: InventoryMode
        get() = settingsRepository.inventoryMode

    fun refreshExchangeCards(bills: List<BillWithDetails>) {
        viewModelScope.launch {
            val numbers = bills.associate { it.bill.id to it.bill.billNumber }
            val quantityByReturn = finance.allReturnLines().groupBy { it.returnId }
                .mapValues { (_, lines) -> lines.sumOf { it.quantity } }
            val relations = com.kex.vikrsaathi.domain.bill.ExchangeHistory.relations(
                finance.exchangeRecords().map { record ->
                    com.kex.vikrsaathi.domain.bill.ExchangeHistory.ReturnLink(
                        originalBillId = record.billId,
                        replacementBillId = record.exchangeBillId,
                        voided = record.voidedAt != null || record.voidsReturnId != null,
                        returnedQuantity = quantityByReturn[record.id] ?: 0
                    )
                }
            )
            _exchangeCards.value = relations.mapValues { (_, relation) ->
                ExchangeCardLink(
                    role = relation.role,
                    otherBillId = relation.otherBillId,
                    otherNumber = numbers[relation.otherBillId].orEmpty(),
                    returnedQuantity = relation.returnedQuantity
                )
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun deleteBill(bill: Bill, onError: (Throwable) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteBill(bill)
            } catch (error: Exception) {
                onError(error)
            }
        }
    }

    fun duplicateBill(
        billId: Long,
        shortageAcknowledged: Boolean = false,
        onResult: (DuplicateBillOutcome) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val newId = repository.duplicateBill(billId, shortageAcknowledged)
                onResult(
                    if (newId == null) DuplicateBillOutcome.Missing
                    else DuplicateBillOutcome.Created(newId)
                )
            } catch (error: InsufficientStockException) {
                onResult(DuplicateBillOutcome.Shortage(error.shortfalls))
            } catch (error: Exception) {
                onResult(DuplicateBillOutcome.Failed(error))
            }
        }
    }

    fun printBillPdf(context: Context, billId: Long, onResult: (File?) -> Unit) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val bill = repository.getBillWithDetails(billId) ?: return@withContext null
                val template = invoiceTemplateRepository.getDefaultTemplate()
                val exchange = exchangePrint(bill)
                PdfGenerator.generateBillPdf(
                    context = appContext,
                    template = template,
                    bill = bill,
                    shopName = settingsRepository.shopName,
                    currencySymbol = settingsRepository.currencySymbol,
                    headerImage = settingsRepository.getHeaderImage(),
                    signatureImage = settingsRepository.getSignatureImage(),
                    shopLogoImage = settingsRepository.getShopLogoImage(),
                    exchangeTitle = exchange?.first,
                    exchangeLines = exchange?.second.orEmpty()
                )
            }
            onResult(file)
        }
    }

    private suspend fun exchangePrint(bill: BillWithDetails): Pair<String, List<String>>? {
        val record = finance.exchangeRecords().firstOrNull {
            it.exchangeBillId == bill.bill.id && it.voidedAt == null && it.voidsReturnId == null
        } ?: return null
        val original = record.billId?.let { repository.getBillWithDetails(it)?.bill?.billNumber }.orEmpty()
        val symbol = settingsRepository.currencySymbol
        val credit = bill.payments.filter { it.method == PaymentMethod.EXCHANGE }.sumOf { it.amountPaise }
        val cash = bill.payments.filter { it.method == PaymentMethod.CASH }.sumOf { it.amountPaise }
        val upi = bill.payments.filter { it.method == PaymentMethod.UPI }.sumOf { it.amountPaise }
        val lines = buildList {
            if (original.isNotBlank()) add("Against original bill $original")
            if (credit > 0L) add("Exchange credit ${FinanceUi.formatPaise(credit, symbol)}")
            if (cash > 0L) add("Cash received ${FinanceUi.formatPaise(cash, symbol)}")
            if (upi > 0L) add("UPI received ${FinanceUi.formatPaise(upi, symbol)}")
            if (record.moneyRefundedPaise > 0L) add("Refund to customer ${FinanceUi.formatPaise(record.moneyRefundedPaise, symbol)}")
        }
        return "EXCHANGE BILL" to lines
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

sealed class DuplicateBillOutcome {
    data class Created(val billId: Long) : DuplicateBillOutcome()
    data class Shortage(val shortfalls: List<StockShortfall>) : DuplicateBillOutcome()
    data object Missing : DuplicateBillOutcome()
    data class Failed(val error: Throwable) : DuplicateBillOutcome()
}
