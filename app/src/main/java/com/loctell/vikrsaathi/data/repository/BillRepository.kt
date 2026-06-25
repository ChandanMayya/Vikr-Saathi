package com.loctell.vikrsaathi.data.repository

import androidx.lifecycle.LiveData
import com.loctell.vikrsaathi.data.dao.BillDao
import com.loctell.vikrsaathi.data.dao.BillItemDao
import com.loctell.vikrsaathi.data.entity.Bill
import com.loctell.vikrsaathi.data.entity.BillItem
import com.loctell.vikrsaathi.data.model.BillLineItem
import com.loctell.vikrsaathi.data.model.BillWithDetails

class BillRepository(
    private val billDao: BillDao,
    private val billItemDao: BillItemDao
) {

    val allBills: LiveData<List<Bill>> = billDao.getAllBills()
    val allBillsWithDetails: LiveData<List<BillWithDetails>> = billDao.getAllBillsWithDetails()

    fun searchBills(query: String): LiveData<List<Bill>> = billDao.searchBills(query)

    suspend fun getBillWithDetails(billId: Long): BillWithDetails? =
        billDao.getBillWithDetails(billId)

    suspend fun deleteBill(bill: Bill) {
        billItemDao.deleteItemsForBill(bill.id)
        billDao.deleteBill(bill)
    }

    suspend fun saveBill(
        customerId: Long?,
        lineItems: List<BillLineItem>,
        existingBillId: Long? = null
    ): Long {
        val total = lineItems.sumOf { it.lineTotal }
        val billId: Long

        if (existingBillId != null) {
            val existing = billDao.getBillById(existingBillId)
                ?: return saveBill(customerId, lineItems, null)
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
            val bill = Bill(
                billNumber = generateNextBillNumber(),
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

    private suspend fun generateNextBillNumber(): String {
        val max = billDao.getMaxBillNumber() ?: 0
        return (max + 1).toString()
    }
}
