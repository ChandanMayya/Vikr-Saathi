package com.kex.vikrsaathi.data.repository

import androidx.lifecycle.LiveData
import com.kex.vikrsaathi.data.dao.BillDao
import com.kex.vikrsaathi.data.dao.BillItemDao
import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.data.model.DashboardTodayStats
import com.kex.vikrsaathi.data.model.DashboardTopItem
import com.kex.vikrsaathi.domain.inventory.StockDeltaCalculator
import com.kex.vikrsaathi.util.DayRange
import com.kex.vikrsaathi.util.InvoiceNumberFormatter

class BillRepository(
    private val billDao: BillDao,
    private val billItemDao: BillItemDao,
    private val settingsRepository: SettingsRepository,
    private val inventoryRepository: InventoryRepository
) {

    val allBills: LiveData<List<Bill>> = billDao.getAllBills()
    val allBillsWithDetails: LiveData<List<BillWithDetails>> = billDao.getAllBillsWithDetails()

    fun searchBills(query: String): LiveData<List<Bill>> = billDao.searchBills(query)

    suspend fun getBillWithDetails(billId: Long): BillWithDetails? =
        billDao.getBillWithDetails(billId)

    suspend fun deleteBill(bill: Bill) {
        val items = billItemDao.getItemsForBill(bill.id)
        val quantities = StockDeltaCalculator.aggregateQuantities(
            items.map { it.itemId to it.quantity }
        )
        billItemDao.deleteItemsForBill(bill.id)
        billDao.deleteBill(bill)
        inventoryRepository.reverseBillStock(bill.id, quantities)
    }

    suspend fun saveBill(
        customerId: Long?,
        lineItems: List<BillLineItem>,
        existingBillId: Long? = null
    ): Long {
        val total = lineItems.sumOf { it.lineTotal }
        val billId: Long
        val oldQuantities: Map<Long, Int>

        if (existingBillId != null) {
            val existing = billDao.getBillById(existingBillId)
                ?: return saveBill(customerId, lineItems, null)
            oldQuantities = StockDeltaCalculator.aggregateQuantities(
                billItemDao.getItemsForBill(existingBillId).map { it.itemId to it.quantity }
            )
            billId = existingBillId
            billDao.updateBill(
                existing.copy(
                    customerId = customerId,
                    total = total,
                    date = System.currentTimeMillis()
                )
            )
            billItemDao.deleteItemsForBill(existingBillId)
        } else {
            oldQuantities = emptyMap()
            val (billNumber, counter) = generateNextInvoiceNumber()
            val bill = Bill(
                billNumber = billNumber,
                invoiceCounter = counter,
                customerId = customerId,
                total = total
            )
            billId = billDao.insertBill(bill)
        }

        val billItems = lineItems.map { line ->
            BillItem(
                billId = billId,
                itemId = line.itemId,
                itemName = line.name,
                quantity = line.quantity,
                mrp = line.mrp,
                discount = line.discount,
                finalPrice = line.unitPriceAfterDiscount
            )
        }
        billItemDao.insertAll(billItems)
        inventoryRepository.applyBillStockChanges(
            billId = billId,
            oldQuantities = oldQuantities,
            newLineItems = lineItems
        )
        return billId
    }

    suspend fun duplicateBill(billId: Long): Long? {
        val source = billDao.getBillWithDetails(billId) ?: return null
        val duplicatedLines = source.items.map { item ->
            BillLineItem(
                itemId = item.itemId,
                name = item.itemName,
                mrp = item.mrp,
                discount = item.discount,
                quantity = item.quantity
            )
        }
        return saveBill(source.bill.customerId, duplicatedLines)
    }

    suspend fun billNumberExists(billNumber: String): Boolean =
        billDao.countByBillNumber(billNumber) > 0

    suspend fun getDashboardTodayStats(): DashboardTodayStats {
        val (start, end) = DayRange.todayMillis()
        val total = billDao.getTotalSalesBetween(start, end)
        val billCount = billDao.getBillCountBetween(start, end)
        val topItems = billDao.getTopSoldItemsBetween(start, end).map { row ->
            DashboardTopItem(name = row.itemName, quantity = row.totalQuantity)
        }
        return DashboardTodayStats(
            totalSales = total,
            billCount = billCount,
            topItems = topItems
        )
    }

    suspend fun importBillFromBackup(
        billNumber: String,
        invoiceCounter: Int,
        date: Long,
        customerId: Long?,
        lineItems: List<BillLineItem>
    ): Long? {
        if (billNumber.isBlank() || lineItems.isEmpty()) return null
        if (billDao.countByBillNumber(billNumber) > 0) return null

        val total = lineItems.sumOf { it.lineTotal }
        val bill = Bill(
            billNumber = billNumber,
            invoiceCounter = invoiceCounter.coerceAtLeast(0),
            customerId = customerId,
            total = total,
            date = date
        )
        val billId = billDao.insertBill(bill)
        val billItems = lineItems.map { line ->
            BillItem(
                billId = billId,
                itemId = line.itemId,
                itemName = line.name,
                quantity = line.quantity,
                mrp = line.mrp,
                discount = line.discount,
                finalPrice = line.unitPriceAfterDiscount
            )
        }
        billItemDao.insertAll(billItems)
        // Do not apply SALE movements on backup import — stockQty comes from item export.

        if (invoiceCounter > 0) {
            val dbMax = billDao.getMaxInvoiceCounter() ?: 0
            settingsRepository.invoiceCounter = maxOf(settingsRepository.invoiceCounter, dbMax + 1)
        }
        return billId
    }

    private suspend fun generateNextInvoiceNumber(): Pair<String, Int> {
        val dbMax = billDao.getMaxInvoiceCounter() ?: 0
        val counter = maxOf(settingsRepository.invoiceCounter, dbMax + 1)
        val billNumber = InvoiceNumberFormatter.format(
            prefix = settingsRepository.invoicePrefix,
            counter = counter,
            suffix = settingsRepository.invoiceSuffix,
            separator = settingsRepository.invoiceSeparator,
            counterMinDigits = settingsRepository.invoiceCounterMinDigits
        )
        settingsRepository.invoiceCounter = counter + 1
        return billNumber to counter
    }
}
