package com.loctell.vikrsaathi.ui.bill

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loctell.vikrsaathi.data.entity.Customer
import com.loctell.vikrsaathi.data.entity.Item
import com.loctell.vikrsaathi.data.model.BillLineItem
import com.loctell.vikrsaathi.data.model.BillWithDetails
import com.loctell.vikrsaathi.data.repository.BillRepository
import com.loctell.vikrsaathi.data.repository.CustomerRepository
import com.loctell.vikrsaathi.data.repository.ItemRepository
import com.loctell.vikrsaathi.data.repository.SettingsRepository
import android.content.Context
import com.loctell.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.loctell.vikrsaathi.util.NumberToWords
import com.loctell.vikrsaathi.util.PdfGenerator
import java.io.File
import kotlinx.coroutines.launch

class BillViewModel(
    private val customerRepository: CustomerRepository,
    private val itemRepository: ItemRepository,
    private val billRepository: BillRepository,
    private val settingsRepository: SettingsRepository,
    private val invoiceTemplateRepository: InvoiceTemplateRepository
) : ViewModel() {

    private val _lineItems = MutableLiveData<List<BillLineItem>>(emptyList())
    val lineItems: LiveData<List<BillLineItem>> = _lineItems

    private val _selectedCustomer = MutableLiveData<Customer?>()
    val selectedCustomer: LiveData<Customer?> = _selectedCustomer

    private val _grandTotal = MutableLiveData(0.0)
    val grandTotal: LiveData<Double> = _grandTotal

    private val _totalInWords = MutableLiveData("")
    val totalInWords: LiveData<String> = _totalInWords

    private var editingBillId: Long? = null

    val currencySymbol: String
        get() = settingsRepository.currencySymbol

    val defaultDiscount: Double
        get() = settingsRepository.defaultDiscount

    val currentBillId: Long?
        get() = editingBillId

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

    fun saveBill(onSaved: (Long) -> Unit) {
        val lines = _lineItems.value.orEmpty()
        if (lines.isEmpty()) return
        viewModelScope.launch {
            val id = billRepository.saveBill(
                customerId = _selectedCustomer.value?.id,
                lineItems = lines,
                existingBillId = editingBillId
            )
            editingBillId = id
            onSaved(id)
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
        val total = _lineItems.value.orEmpty().sumOf { it.lineTotal }
        _grandTotal.value = total
        _totalInWords.value = NumberToWords.convert(total)
    }
}
