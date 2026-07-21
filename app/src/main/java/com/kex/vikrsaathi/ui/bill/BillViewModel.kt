package com.kex.vikrsaathi.ui.bill

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.data.draft.HeldBillRestore
import com.kex.vikrsaathi.data.draft.HeldDraftSummary
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.BillDraftRepository
import com.kex.vikrsaathi.data.repository.CustomerRepository
import com.kex.vikrsaathi.data.repository.InventoryRepository
import com.kex.vikrsaathi.data.repository.ItemRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.data.repository.StockShortfall
import android.content.Context
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.kex.vikrsaathi.domain.inventory.StockDeltaCalculator
import com.kex.vikrsaathi.util.InventoryMode
import com.kex.vikrsaathi.util.NumberToWords
import com.kex.vikrsaathi.util.PdfGenerator
import com.kex.vikrsaathi.util.PriceCalculator
import java.io.File
import kotlinx.coroutines.launch

class BillViewModel(
    private val customerRepository: CustomerRepository,
    private val itemRepository: ItemRepository,
    private val billRepository: BillRepository,
    private val billDraftRepository: BillDraftRepository,
    private val settingsRepository: SettingsRepository,
    private val invoiceTemplateRepository: InvoiceTemplateRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    data class AutoRegisterResult(
        val customerCreated: Boolean = false,
        val itemCreatedCount: Int = 0
    ) {
        val hasChanges: Boolean
            get() = customerCreated || itemCreatedCount > 0
    }

    private val _lineItems = MutableLiveData<List<BillLineItem>>(emptyList())
    val lineItems: LiveData<List<BillLineItem>> = _lineItems

    private val _selectedCustomer = MutableLiveData<Customer?>()
    val selectedCustomer: LiveData<Customer?> = _selectedCustomer

    private val _grandTotal = MutableLiveData(0.0)
    val grandTotal: LiveData<Double> = _grandTotal

    private val _totalDiscount = MutableLiveData(0.0)
    val totalDiscount: LiveData<Double> = _totalDiscount

    private val _totalInWords = MutableLiveData("")
    val totalInWords: LiveData<String> = _totalInWords

    private var editingBillId: Long? = null

    val currencySymbol: String
        get() = settingsRepository.currencySymbol

    val defaultDiscount: Double
        get() = settingsRepository.defaultDiscount

    val inventoryMode: InventoryMode
        get() = settingsRepository.inventoryMode

    val currentBillId: Long?
        get() = editingBillId

    val isNewBillSession: Boolean
        get() = editingBillId == null

    /**
     * Checks stock without saving or creating masters.
     * New (unsaved) line items are treated as available = 0.
     */
    fun prepareSaveBill(
        buyerName: String,
        buyerAddress: String,
        buyerPhone: String,
        onPrepared: (shortfalls: List<StockShortfall>) -> Unit
    ) {
        val lines = _lineItems.value.orEmpty()
        if (lines.isEmpty()) return
        viewModelScope.launch {
            if (inventoryMode == InventoryMode.OFF) {
                onPrepared(emptyList())
                return@launch
            }
            val oldQuantities = if (editingBillId != null) {
                val details = billRepository.getBillWithDetails(editingBillId!!)
                StockDeltaCalculator.aggregateQuantities(
                    details?.items.orEmpty().map { it.itemId to it.quantity }
                )
            } else {
                emptyMap()
            }
            val shortfalls = inventoryRepository.findShortfalls(
                lineItems = lines,
                existingBillId = editingBillId,
                oldBillQuantities = oldQuantities
            ).toMutableList()
            lines.filter { it.itemId == null && it.name.isNotBlank() && it.quantity > 0 }
                .groupBy { it.name.trim().lowercase() }
                .forEach { (_, group) ->
                    val required = group.sumOf { it.quantity }
                    val name = group.first().name
                    shortfalls.add(
                        StockShortfall(
                            itemId = 0L,
                            itemName = name,
                            available = 0,
                            required = required
                        )
                    )
                }
            onPrepared(shortfalls)
        }
    }

    fun holdBill(
        buyerName: String,
        buyerAddress: String,
        buyerPhone: String,
        onHeld: (HeldDraftSummary) -> Unit,
        onEmpty: () -> Unit
    ) {
        val lines = _lineItems.value.orEmpty()
        if (lines.isEmpty()) {
            onEmpty()
            return
        }
        viewModelScope.launch {
            val name = buyerName.ifBlank {
                _selectedCustomer.value?.name.orEmpty()
            }.ifBlank { "Walk-in customer" }
            val summary = billDraftRepository.holdBill(
                customerId = _selectedCustomer.value?.id,
                customerName = name,
                buyerAddress = buyerAddress,
                buyerPhone = buyerPhone,
                lineItems = lines
            )
            clearBill()
            onHeld(summary)
        }
    }

    fun resumeHeldBill(
        draftId: Long,
        onRestored: (HeldBillRestore) -> Unit,
        onMissing: () -> Unit
    ) {
        viewModelScope.launch {
            val restored = billDraftRepository.consumeHeldBill(draftId)
            if (restored == null || restored.lineItems.isEmpty()) {
                onMissing()
                return@launch
            }
            editingBillId = null
            _selectedCustomer.value = restored.customerId?.let { customerRepository.getById(it) }
            _lineItems.value = restored.lineItems
            recalculate()
            onRestored(restored)
        }
    }

    fun hasUnsavedNewBillContent(
        buyerName: String,
        buyerAddress: String,
        buyerPhone: String
    ): Boolean {
        if (!isNewBillSession) return false
        if (_lineItems.value.orEmpty().isNotEmpty()) return true
        return buyerName.isNotBlank() || buyerAddress.isNotBlank() || buyerPhone.isNotBlank()
    }

    fun canHoldCurrentBill(): Boolean = _lineItems.value.orEmpty().isNotEmpty()

    fun loadBill(billId: Long) {
        viewModelScope.launch {
            val bill = billRepository.getBillWithDetails(billId) ?: return@launch
            editingBillId = billId
            _selectedCustomer.value = bill.customer
            _lineItems.value = bill.items.map {
                BillLineItem(
                    itemId = it.itemId,
                    name = it.itemName,
                    mrp = it.mrp,
                    discount = it.discount,
                    quantity = it.quantity
                )
            }
            recalculate()
        }
    }

    fun clearBill() {
        editingBillId = null
        _selectedCustomer.value = null
        _lineItems.value = emptyList()
        recalculate()
    }

    fun setCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
    }

    fun searchCustomers(query: String, callback: (List<Customer>) -> Unit) {
        viewModelScope.launch { callback(customerRepository.search(query)) }
    }

    fun saveCustomer(customer: Customer, onSaved: (Customer) -> Unit) {
        viewModelScope.launch {
            val id = customerRepository.insert(customer)
            val saved = customer.copy(id = id)
            _selectedCustomer.value = saved
            onSaved(saved)
        }
    }

    fun saveItem(item: Item, onResult: (Result<Item>) -> Unit) {
        viewModelScope.launch {
            try {
                val barcode = item.barcode?.trim().orEmpty()
                if (barcode.isNotEmpty() && !itemRepository.isBarcodeUnique(barcode, item.id)) {
                    onResult(Result.failure(IllegalStateException("Barcode already exists")))
                    return@launch
                }
                val id = itemRepository.insert(item.copy(barcode = barcode.ifEmpty { null }))
                onResult(Result.success(item.copy(id = id, barcode = barcode.ifEmpty { null })))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun searchItems(query: String, callback: (List<Item>) -> Unit) {
        viewModelScope.launch { callback(itemRepository.searchByName(query)) }
    }

    fun addItemFromMaster(item: Item, quantity: Int = 1) {
        val qty = quantity.coerceAtLeast(1)
        val discount = if (item.discount > 0) item.discount else defaultDiscount
        val sellingPrice = item.sellingPrice
        val effectiveDiscount = if (sellingPrice != null && sellingPrice > 0) {
            ((item.mrp - sellingPrice) / item.mrp) * 100.0
        } else {
            discount
        }
        addOrIncrementLine(
            BillLineItem(
                itemId = item.id,
                name = item.name,
                mrp = item.mrp,
                discount = effectiveDiscount.coerceAtLeast(0.0)
            ),
            qty
        )
    }

    fun findItemByBarcode(barcode: String, callback: (Item?) -> Unit) {
        viewModelScope.launch {
            callback(itemRepository.getByBarcode(barcode))
        }
    }

    fun updateLineQuantity(index: Int, quantity: Int) {
        val items = _lineItems.value?.toMutableList() ?: return
        if (index !in items.indices || quantity < 1) return
        items[index] = items[index].copy(quantity = quantity)
        _lineItems.value = items
        recalculate()
    }

    fun updateLineDiscount(index: Int, discount: Double) {
        val items = _lineItems.value?.toMutableList() ?: return
        if (index !in items.indices) return
        items[index] = items[index].copy(discount = discount.coerceAtLeast(0.0))
        _lineItems.value = items
        recalculate()
    }

    fun removeLine(index: Int) {
        val items = _lineItems.value?.toMutableList() ?: return
        if (index !in items.indices) return
        items.removeAt(index)
        _lineItems.value = items
        recalculate()
    }

    fun saveBill(
        buyerName: String,
        buyerAddress: String,
        buyerPhone: String,
        onSaved: (Long, AutoRegisterResult) -> Unit
    ) {
        val lines = _lineItems.value.orEmpty()
        if (lines.isEmpty()) return
        viewModelScope.launch {
            val (resolvedCustomer, customerCreated) = resolveOrCreateCustomer(
                buyerName = buyerName,
                buyerAddress = buyerAddress,
                buyerPhone = buyerPhone
            )
            val (resolvedLines, createdItemsCount) = resolveOrCreateItems(lines)
            if (resolvedLines != lines) {
                _lineItems.value = resolvedLines
                recalculate()
            }
            val id = billRepository.saveBill(
                customerId = resolvedCustomer?.id,
                lineItems = resolvedLines,
                existingBillId = editingBillId
            )
            editingBillId = id
            onSaved(
                id,
                AutoRegisterResult(
                    customerCreated = customerCreated,
                    itemCreatedCount = createdItemsCount
                )
            )
        }
    }

    fun getBillDetails(billId: Long, callback: (BillWithDetails?) -> Unit) {
        viewModelScope.launch {
            callback(billRepository.getBillWithDetails(billId))
        }
    }

    fun exportBillPdf(context: Context, billId: Long, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val bill = billRepository.getBillWithDetails(billId) ?: run {
                onResult(null)
                return@launch
            }
            val template = invoiceTemplateRepository.getDefaultTemplate()
            val file = PdfGenerator.generateBillPdf(
                context = context,
                template = template,
                bill = bill,
                shopName = settingsRepository.shopName,
                currencySymbol = settingsRepository.currencySymbol,
                headerImage = settingsRepository.getHeaderImage(),
                signatureImage = settingsRepository.getSignatureImage(),
                shopLogoImage = settingsRepository.getShopLogoImage()
            )
            onResult(file)
        }
    }

    private fun addOrIncrementLine(line: BillLineItem, quantityToAdd: Int = 1) {
        val items = _lineItems.value?.toMutableList() ?: mutableListOf()
        val existingIndex = items.indexOfFirst {
            it.itemId != null && it.itemId == line.itemId
        }
        if (existingIndex >= 0) {
            val existing = items[existingIndex]
            items[existingIndex] = existing.copy(quantity = existing.quantity + quantityToAdd)
        } else {
            items.add(line.copy(quantity = quantityToAdd))
        }
        _lineItems.value = items
        recalculate()
    }

    private fun recalculate() {
        val lines = _lineItems.value.orEmpty()
        val totalDiscount = lines.sumOf {
            PriceCalculator.discountAmount(it.mrp, it.discount, it.quantity)
        }
        val total = lines.sumOf { it.lineTotal }
        _totalDiscount.value = totalDiscount
        _grandTotal.value = total
        _totalInWords.value = NumberToWords.convert(total)
    }

    private suspend fun resolveOrCreateCustomer(
        buyerName: String,
        buyerAddress: String,
        buyerPhone: String
    ): Pair<Customer?, Boolean> {
        val existingSelected = _selectedCustomer.value
        val normalizedName = buyerName.trim()
        val normalizedPhone = buyerPhone.trim()
        val normalizedAddress = buyerAddress.trim()
        if (normalizedName.isBlank()) return existingSelected to false

        if (existingSelected != null &&
            existingSelected.name.trim().equals(normalizedName, ignoreCase = true)
        ) {
            val hasDetailsChanged = existingSelected.phone.trim() != normalizedPhone ||
                existingSelected.address1.trim() != normalizedAddress
            return if (!hasDetailsChanged) {
                existingSelected to false
            } else {
                val updated = existingSelected.copy(
                    name = normalizedName,
                    address1 = normalizedAddress,
                    phone = normalizedPhone
                )
                customerRepository.update(updated)
                _selectedCustomer.value = updated
                updated to false
            }
        }

        val existingByName = customerRepository.findByNameExact(normalizedName)
        if (existingByName != null) {
            _selectedCustomer.value = existingByName
            return existingByName to false
        }

        val created = Customer(
            name = normalizedName,
            address1 = normalizedAddress,
            phone = normalizedPhone
        )
        val id = customerRepository.insert(created)
        val saved = created.copy(id = id)
        _selectedCustomer.value = saved
        return saved to true
    }

    private suspend fun resolveOrCreateItems(lines: List<BillLineItem>): Pair<List<BillLineItem>, Int> {
        var createdCount = 0
        val resolved = lines.map { line ->
            if (line.itemId != null || line.name.isBlank()) return@map line

            val exact = itemRepository.findByNameExact(line.name)
            if (exact != null) {
                return@map line.copy(itemId = exact.id)
            }

            val created = Item(
                name = line.name.trim(),
                mrp = line.mrp,
                discount = line.discount,
                sellingPrice = PriceCalculator.priceAfterDiscount(line.mrp, line.discount)
            )
            val id = itemRepository.insert(created)
            createdCount += 1
            line.copy(itemId = id)
        }
        return resolved to createdCount
    }
}
