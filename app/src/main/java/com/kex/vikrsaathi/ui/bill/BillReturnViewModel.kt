package com.kex.vikrsaathi.ui.bill

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.BillReturnLineInput
import com.kex.vikrsaathi.data.repository.CustomerFinanceRepository
import com.kex.vikrsaathi.data.repository.CustomerRepository
import com.kex.vikrsaathi.data.repository.ItemRepository
import com.kex.vikrsaathi.data.repository.OperatorRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.domain.bill.ReplacementRatePolicy
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.domain.finance.ExchangeMoney
import com.kex.vikrsaathi.domain.finance.FinanceException
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.domain.finance.RefundMethod
import com.kex.vikrsaathi.domain.finance.ReturnSettlement
import com.kex.vikrsaathi.domain.finance.ReturnValuation
import com.kex.vikrsaathi.domain.money.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

data class ReturnLineState(
    val billItemId: Long,
    val itemId: Long?,
    val name: String,
    val sold: Int,
    val already: Int,
    val unitPrice: Double,
    val quantity: Int,
    val restock: Boolean,
    val tracked: Boolean = false
) {
    val eligible: Int get() = (sold - already).coerceAtLeast(0)
}

data class ReplacementState(
    val key: Long,
    val itemId: Long,
    val name: String,
    val unitPrice: Double,
    val discountPercent: Double = 0.0,
    val quantity: Int,
    val warranty: WarrantyPolicy.Snapshot? = null
)

data class ReplacementChoice(
    val itemId: Long,
    val name: String,
    val master: ReplacementRatePolicy.PriceQuote,
    val comparison: ReplacementRatePolicy.PriceComparison
)

data class ReturnFormState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val billNumber: String = "",
    val dateLabel: String = "",
    val customerName: String = "",
    val billCustomerId: Long? = null,
    val attachedCustomerId: Long? = null,
    val attachedCustomerName: String = "",
    val lines: List<ReturnLineState> = emptyList(),
    val refundMethod: String = RefundMethod.CASH,
    val replacements: List<ReplacementState> = emptyList(),
    val dueChoice: String = "CASH",
    val reason: String = "",
    val note: String = "",
    val posting: Boolean = false
)

class BillReturnViewModel(
    private val finance: CustomerFinanceRepository,
    private val bills: BillRepository,
    private val customers: CustomerRepository,
    private val items: ItemRepository,
    private val settings: SettingsRepository,
    private val operators: OperatorRepository
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val _form = MutableLiveData(ReturnFormState())
    val form: LiveData<ReturnFormState> = _form

    private var billId: Long = -1L
    private var sources: List<ReturnValuation.SourceLine> = emptyList()
    private var billedPrices: List<Pair<Long?, ReplacementRatePolicy.BilledPrice>> = emptyList()
    private var already: Map<Long, Int> = emptyMap()
    private var billRoundOff: Double = 0.0
    private var nextReplacementKey = 1L
    private var posted = false

    val currencySymbol: String get() = settings.currencySymbol

    fun load(id: Long) {
        if (billId == id && _form.value?.loaded == true) return
        billId = id
        viewModelScope.launch {
            val details = bills.getBillWithDetails(id)
            if (details == null) {
                _form.value = ReturnFormState(loaded = true, missing = true)
                return@launch
            }
            val prior = finance.returnsForBill(id)
                .filter { it.voidedAt == null && it.voidsReturnId == null }
                .flatMap { finance.linesForReturn(it.id) }
            sources = details.items.map {
                ReturnValuation.SourceLine(
                    billItemId = it.id,
                    itemId = it.itemId,
                    itemName = it.itemName,
                    quantity = it.quantity,
                    finalPrice = it.finalPrice,
                    lineRoundOff = it.roundOff
                )
            }
            already = ReturnValuation.alreadyByBillItem(
                sources,
                prior.map { ReturnValuation.PriorLine(it.billItemId, it.itemId, it.itemName, it.quantity) }
            )
            billRoundOff = details.bill.roundOff
            billedPrices = details.items.map { item ->
                item.itemId to ReplacementRatePolicy.BilledPrice(item.mrp, item.discount, item.finalPrice)
            }
            val customer = details.bill.customerId?.let { customers.getById(it) }
            val trackingOn = settings.batchTrackingEnabled
            val trackedIds = if (trackingOn) {
                items.getAllSync().filter { it.trackBatches }.map { it.id }.toSet()
            } else {
                emptySet()
            }
            _form.value = ReturnFormState(
                loaded = true,
                billNumber = details.bill.billNumber,
                dateLabel = dateFormat.format(Date(details.bill.date)),
                customerName = customer?.name.orEmpty(),
                billCustomerId = details.bill.customerId,
                lines = sources.map { source ->
                    ReturnLineState(
                        billItemId = source.billItemId,
                        itemId = source.itemId,
                        name = source.itemName,
                        sold = source.quantity,
                        already = already[source.billItemId] ?: 0,
                        unitPrice = source.finalPrice,
                        quantity = 0,
                        restock = source.itemId != null,
                        tracked = source.itemId != null && source.itemId in trackedIds
                    )
                }
            )
        }
    }

    fun setQuantity(billItemId: Long, quantity: Int) = update { state ->
        state.copy(
            lines = state.lines.map { line ->
                if (line.billItemId != billItemId) line
                else line.copy(quantity = quantity.coerceIn(0, line.eligible))
            }
        )
    }

    fun setRestock(billItemId: Long, restock: Boolean) = update { state ->
        state.copy(
            lines = state.lines.map { line ->
                if (line.billItemId != billItemId) line
                else line.copy(restock = restock)
            }
        )
    }

    fun setRefundMethod(method: String) = update { it.copy(refundMethod = method) }

    fun setDueChoice(choice: String) = update { it.copy(dueChoice = choice) }

    fun rememberText(reason: String, note: String) = update {
        it.copy(reason = reason.trim(), note = note.trim())
    }

    fun attachCustomer(customerId: Long, name: String) = update {
        it.copy(attachedCustomerId = customerId, attachedCustomerName = name)
    }

    fun addReplacement(
        itemId: Long,
        name: String,
        unitPrice: Double,
        discountPercent: Double,
        quantity: Int = 1,
        source: Item? = null
    ) = update { state ->
        val warranty = source?.let { WarrantyPolicy.forNewSale(it, settings.warrantyEnabled) }
        val merged = ReplacementRatePolicy.merge(
            lines = state.replacements.map { it.toPolicyLine() },
            itemId = itemId,
            name = name,
            unitPrice = unitPrice,
            discountPercent = discountPercent,
            quantity = quantity,
            nextKey = nextReplacementKey
        )
        nextReplacementKey = merged.nextKey
        val previous = state.replacements.associateBy { it.key }
        state.copy(replacements = merged.lines.map { line ->
            val kept = previous[line.key]?.warranty
            line.toState(kept ?: if (line.key !in previous) warranty else null)
        })
    }

    suspend fun replacementChoices(query: String): List<Item> {
        val trimmed = query.trim()
        return if (trimmed.isBlank()) {
            items.getAllSync().sortedBy { it.name.lowercase() }.take(20)
        } else {
            items.searchByName(trimmed)
        }
    }

    fun replacementChoice(item: Item): ReplacementChoice {
        val master = ReplacementRatePolicy.masterQuote(item.mrp, item.discount, item.sellingPrice)
        val billed = billedPrices.filter { it.first == item.id }.map { it.second }
        return ReplacementChoice(item.id, item.name, master, ReplacementRatePolicy.compare(master, billed))
    }

    fun setReplacementQuantity(key: Long, quantity: Int) = update { state ->
        state.copy(
            replacements = if (quantity <= 0) {
                state.replacements.filterNot { it.key == key }
            } else {
                state.replacements.map { if (it.key == key) it.copy(quantity = quantity) else it }
            }
        )
    }

    suspend fun searchCustomers(query: String) = customers.search(query)

    suspend fun createCustomer(name: String): Long? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val existing = customers.findByNameExact(trimmed)
        if (existing != null) return existing.id
        return customers.insert(com.kex.vikrsaathi.data.entity.Customer(name = trimmed))
    }

    fun preview(): ReturnValuation.Result? {
        val requests = currentRequests()
        if (requests.isEmpty()) return null
        return runCatching {
            ReturnValuation.value(sources, already, requests, billRoundOff)
        }.getOrNull()
    }

    fun replacementPaise(): Long {
        val lines = replacementItems()
        if (lines.isEmpty()) return 0L
        return Money.rupeesToPaise(
            com.kex.vikrsaathi.data.repository.BillTotalsCalculator.grandTotal(lines, 0.0)
        )
    }

    fun exchangePlan(): ExchangeMoney? {
        val value = preview() ?: return null
        val replacement = replacementPaise()
        if (replacementItems().isEmpty()) return null
        val state = _form.value ?: return null
        return runCatching {
            ReturnSettlement.resolveExchange(
                value.totalPaise,
                replacement,
                dueMethod(state, shopRefunds = value.totalPaise > replacement),
                hasCustomer(state)
            )
        }.getOrNull()
    }

    fun hasCustomer(): Boolean {
        val state = _form.value ?: return false
        return hasCustomer(state)
    }

    fun post(reason: String, note: String, onSuccess: (Long) -> Unit, onError: (Throwable) -> Unit) {
        if (posted || _form.value?.posting == true) return
        val state = _form.value ?: return
        val requests = currentRequests()
        if (requests.isEmpty()) {
            onError(FinanceException(com.kex.vikrsaathi.domain.finance.FinanceError.NOTHING_TO_RECORD))
            return
        }
        val reasonProblem = com.kex.vikrsaathi.domain.bill.ExchangeReason.problem(
            isExchange = state.replacements.isNotEmpty(),
            reason = reason,
            note = note
        )
        if (reasonProblem != null) {
            onError(FinanceException(com.kex.vikrsaathi.domain.finance.FinanceError.REASON_REQUIRED))
            return
        }
        update { it.copy(posting = true) }
        viewModelScope.launch {
            try {
                val actor = operators.actorLabel()
                val lines = state.lines.filter { it.quantity > 0 }.map {
                    BillReturnLineInput(it.billItemId, it.quantity, it.restock)
                }
                val walkIn = if (state.billCustomerId == null) state.attachedCustomerId else null
                val result = if (state.replacements.isEmpty()) {
                    finance.postBillReturn(
                        billId = billId,
                        lines = lines,
                        refundMethod = state.refundMethod,
                        reason = reason,
                        note = note,
                        occurredAt = System.currentTimeMillis(),
                        actorLabel = actor,
                        walkInCustomerId = walkIn
                    )
                } else {
                    val previewValue = preview()?.totalPaise ?: 0L
                    val replacement = replacementPaise()
                    finance.postExchange(
                        billId = billId,
                        lines = lines,
                        replacementLines = replacementItems(),
                        replacementRoundOff = 0.0,
                        dueMethod = dueMethod(state, shopRefunds = previewValue > replacement),
                        reason = reason,
                        note = note,
                        occurredAt = System.currentTimeMillis(),
                        actorLabel = actor,
                        walkInCustomerId = walkIn
                    )
                }
                posted = true
                onSuccess(result.returnId)
            } catch (error: Throwable) {
                update { it.copy(posting = false) }
                onError(error)
            }
        }
    }

    private fun currentRequests(): List<ReturnValuation.Request> {
        return _form.value?.lines.orEmpty()
            .filter { it.quantity > 0 }
            .map { ReturnValuation.Request(it.billItemId, it.quantity) }
    }

    private fun replacementItems(): List<BillLineItem> {
        return _form.value?.replacements.orEmpty().map {
            BillLineItem(
                itemId = it.itemId,
                name = it.name,
                mrp = it.unitPrice,
                discount = it.discountPercent,
                quantity = it.quantity,
                warranty = it.warranty
            )
        }
    }

    private fun hasCustomer(state: ReturnFormState): Boolean {
        return state.billCustomerId != null || state.attachedCustomerId != null
    }

    private fun dueMethod(state: ReturnFormState, shopRefunds: Boolean): String {
        return when (state.dueChoice) {
            "UPI" -> if (shopRefunds) RefundMethod.UPI else PaymentMethod.UPI
            "ACCOUNT" -> if (shopRefunds) RefundMethod.STORE_CREDIT else PaymentMethod.CREDIT
            else -> if (shopRefunds) RefundMethod.CASH else PaymentMethod.CASH
        }
    }

    private fun ReplacementState.toPolicyLine() = ReplacementRatePolicy.Line(
        key = key,
        itemId = itemId,
        name = name,
        unitPrice = unitPrice,
        discountPercent = discountPercent,
        quantity = quantity
    )

    private fun ReplacementRatePolicy.Line.toState(warranty: WarrantyPolicy.Snapshot?) = ReplacementState(
        key = key,
        itemId = itemId,
        name = name,
        unitPrice = unitPrice,
        discountPercent = discountPercent,
        quantity = quantity,
        warranty = warranty
    )

    private fun update(block: (ReturnFormState) -> ReturnFormState) {
        val current = _form.value ?: ReturnFormState()
        _form.value = block(current)
    }
}
