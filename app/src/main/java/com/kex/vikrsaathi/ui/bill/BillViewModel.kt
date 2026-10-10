package com.kex.vikrsaathi.ui.bill

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.data.draft.HeldBillRestore
import com.kex.vikrsaathi.data.draft.HeldDraftSummary
import com.kex.vikrsaathi.data.repository.BillTotalsCalculator
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
    private val database: AppDatabase,
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

    private val _lineRoundOffTotal = MutableLiveData(0.0)
    val lineRoundOffTotal: LiveData<Double> = _lineRoundOffTotal

    private val _subtotal = MutableLiveData(0.0)
    val subtotal: LiveData<Double> = _subtotal

    private val _billRoundOff = MutableLiveData(0.0)
    val billRoundOff: LiveData<Double> = _billRoundOff

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
     * Unsaved line items are checked against their entered opening stock (blank = 0).
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
                    val available = group.firstNotNullOfOrNull { it.openingStock } ?: 0
                    if (required > available) {
                        shortfalls.add(
                            StockShortfall(
                                itemId = 0L,
                                itemName = group.first().name,
                                available = available,
                                required = required
                            )
                        )
                    }
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
                lineItems = lines,
                billRoundOff = _billRoundOff.value ?: 0.0
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
            _billRoundOff.value = restored.roundOff
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
                    quantity = it.quantity,
                    roundOff = it.roundOff
                )
            }
            _billRoundOff.value = bill.bill.roundOff
            recalculate()
        }
    }

    fun clearBill() {
        editingBillId = null
        _selectedCustomer.value = null
        _lineItems.value = emptyList()
        _billRoundOff.value = 0.0
        recalculate()
    }

    fun setCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
    }

    fun searchCustomers(query: String, callback: (List<Customer>) -> Unit) {
        viewModelScope.launch { callback(customerRepository.search(query)) }
    }

    fun findCustomerByExactName(name: String, callback: (Customer?) -> Unit) {
        viewModelScope.launch { callback(customerRepository.findByNameExact(name)) }
    }

    fun findItemByExactName(name: String, callback: (Item?) -> Unit) {
        viewModelScope.launch { callback(itemRepository.findByNameExact(name)) }
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

    /** Adds an item that is not in the master yet; [rate] is the per-unit price billed. */
    fun addNewItem(name: String, rate: Double, openingStock: Int?, barcode: String?) {
        if (name.isBlank() || rate <= 0.0) return
        addOrIncrementLine(
            BillLineItem(
                name = name.trim(),
                mrp = rate,
                discount = 0.0,
                isNew = true,
                openingStock = openingStock?.coerceAtLeast(0),
                barcode = barcode?.trim()?.ifEmpty { null }
            )
        )
    }

    fun updateLineRate(index: Int, rate: Double) {
        val items = _lineItems.value?.toMutableList() ?: return
        if (index !in items.indices || !items[index].isNew || rate <= 0.0) return
        items[index] = items[index].copy(mrp = rate)
        _lineItems.value = items
        recalculate()
    }

    fun firstNewLineWithoutRate(): Int =
        _lineItems.value.orEmpty().indexOfFirst { it.isNew && it.mrp <= 0.0 }

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

    fun updateLineRoundOff(index: Int, roundOff: Double) {
        val items = _lineItems.value?.toMutableList() ?: return
        if (index !in items.indices) return
        items[index] = items[index].copy(roundOff = roundOff)
        _lineItems.value = items
        recalculate()
    }

    fun updateBillRoundOff(roundOff: Double) {
        _billRoundOff.value = roundOff
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
        onError: (Throwable) -> Unit = {},
        onSaved: (Long, AutoRegisterResult) -> Unit
    ) {
        val lines = _lineItems.value.orEmpty()
        if (lines.isEmpty()) return
        val selectedCustomer = _selectedCustomer.value
        val billRoundOff = _billRoundOff.value ?: 0.0
        val existingBillId = editingBillId
        viewModelScope.launch {
            // Room runs this block off the main thread: no LiveData writes inside.
            val outcome = try {
                database.withTransaction {
                    val (customer, customerCreated) = resolveOrCreateCustomer(
                        existingSelected = selectedCustomer,
                        buyerName = buyerName,
                        buyerAddress = buyerAddress,
                        buyerPhone = buyerPhone
                    )
                    val (resolvedLines, createdItemsCount) = resolveOrCreateItems(lines)
                    val id = billRepository.saveBill(
                        customerId = customer?.id,
                        lineItems = resolvedLines,
                        billRoundOff = billRoundOff,
                        existingBillId = existingBillId
                    )
                    SaveOutcome(id, customer, customerCreated, resolvedLines, createdItemsCount)
                }
            } catch (e: Exception) {
                onError(e)
                return@launch
            }
            _selectedCustomer.value = outcome.customer
            if (outcome.lines != lines) {
                _lineItems.value = outcome.lines
                recalculate()
            }
            editingBillId = outcome.billId
            onSaved(
                outcome.billId,
                AutoRegisterResult(
                    customerCreated = outcome.customerCreated,
                    itemCreatedCount = outcome.itemCreatedCount
                )
            )
        }
    }

    private data class SaveOutcome(
        val billId: Long,
        val customer: Customer?,
        val customerCreated: Boolean,
        val lines: List<BillLineItem>,
        val itemCreatedCount: Int
    )

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
            (it.itemId != null && it.itemId == line.itemId) ||
                (line.isNew && it.isNew && it.name.trim().equals(line.name.trim(), ignoreCase = true))
        }
        if (existingIndex >= 0) {
            val existing = items[existingIndex]
            items[existingIndex] = if (line.isNew) {
                existing.copy(
                    quantity = existing.quantity + quantityToAdd,
                    mrp = line.mrp,
                    openingStock = line.openingStock ?: existing.openingStock,
                    barcode = existing.barcode ?: line.barcode
                )
            } else {
                existing.copy(quantity = existing.quantity + quantityToAdd)
            }
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
        val lineRoundOff = BillTotalsCalculator.lineItemsRoundOffTotal(lines)
        val subtotal = BillTotalsCalculator.lineItemsSubtotal(lines)
        val billRoundOff = _billRoundOff.value ?: 0.0
        val total = subtotal + billRoundOff
        _totalDiscount.value = totalDiscount
        _lineRoundOffTotal.value = lineRoundOff
        _subtotal.value = subtotal
        _grandTotal.value = total
        _totalInWords.value = NumberToWords.convert(total)
    }

    private suspend fun resolveOrCreateCustomer(
        existingSelected: Customer?,
        buyerName: String,
        buyerAddress: String,
        buyerPhone: String
    ): Pair<Customer?, Boolean> {
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
                updated to false
            }
        }

        val existingByName = customerRepository.findByNameExact(normalizedName)
        if (existingByName != null) {
            return existingByName to false
        }

        val created = Customer(
            name = normalizedName,
            address1 = normalizedAddress,
            phone = normalizedPhone
        )
        val id = customerRepository.insert(created)
        return created.copy(id = id) to true
    }

    private suspend fun resolveOrCreateItems(lines: List<BillLineItem>): Pair<List<BillLineItem>, Int> {
        var createdCount = 0
        val resolved = lines.map { line ->
            if (line.itemId != null || line.name.isBlank()) return@map line

            val exact = itemRepository.findByNameExact(line.name)
            if (exact != null) {
                return@map line.asSavedLine(exact.id)
            }

            val created = if (line.isNew) {
                val barcode = line.barcode?.takeIf { itemRepository.isBarcodeUnique(it) }
                Item(
                    name = line.name.trim(),
                    barcode = barcode,
                    mrp = line.mrp,
                    discount = 0.0,
                    sellingPrice = line.mrp
                )
            } else {
                Item(
                    name = line.name.trim(),
                    mrp = line.mrp,
                    discount = line.discount,
                    sellingPrice = PriceCalculator.priceAfterDiscount(line.mrp, line.discount)
                )
            }
            val id = itemRepository.insert(created)
            val openingStock = line.openingStock ?: 0
            if (openingStock > 0) {
                inventoryRepository.setOpeningStock(id, openingStock).getOrThrow()
            }
            createdCount += 1
            line.asSavedLine(id)
        }
        return resolved to createdCount
    }

    private fun BillLineItem.asSavedLine(id: Long) =
        copy(itemId = id, isNew = false, openingStock = null, barcode = null)
}
