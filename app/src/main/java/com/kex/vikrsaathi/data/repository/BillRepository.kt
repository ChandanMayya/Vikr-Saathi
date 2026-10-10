package com.kex.vikrsaathi.data.repository

import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.kex.vikrsaathi.data.dao.BillDao
import com.kex.vikrsaathi.data.dao.BillItemDao
import com.kex.vikrsaathi.data.dao.BillPaymentDao
import com.kex.vikrsaathi.data.dao.CustomerLedgerDao
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.BillPayment
import com.kex.vikrsaathi.data.entity.CustomerLedgerEntry
import com.kex.vikrsaathi.domain.finance.BillLedgerPlanner
import com.kex.vikrsaathi.domain.finance.BillPaymentRequest
import com.kex.vikrsaathi.domain.finance.BillPaymentStatus
import com.kex.vikrsaathi.domain.finance.CustomerLedgerType
import com.kex.vikrsaathi.domain.finance.FinanceError
import com.kex.vikrsaathi.domain.finance.FinanceException
import com.kex.vikrsaathi.domain.finance.FinanceReference
import com.kex.vikrsaathi.domain.finance.LedgerRow
import com.kex.vikrsaathi.domain.finance.PaymentPortion
import com.kex.vikrsaathi.domain.finance.PaymentSplitValidator
import com.kex.vikrsaathi.domain.finance.ReversalReason
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.data.model.DashboardTodayStats
import com.kex.vikrsaathi.data.model.DashboardTopItem
import com.kex.vikrsaathi.domain.inventory.StockDeltaCalculator
import com.kex.vikrsaathi.util.DayRange
import com.kex.vikrsaathi.util.InvoiceNumberFormatter

class BillRepository(
    private val database: AppDatabase,
    private val billDao: BillDao,
    private val billItemDao: BillItemDao,
    private val billPaymentDao: BillPaymentDao,
    private val customerLedgerDao: CustomerLedgerDao,
    private val settingsRepository: SettingsRepository,
    private val inventoryRepository: InventoryRepository
) {

    val allBills: LiveData<List<Bill>> = billDao.getAllBills()
    val allBillsWithDetails: LiveData<List<BillWithDetails>> = billDao.getAllBillsWithDetails()

    fun searchBills(query: String): LiveData<List<Bill>> = billDao.searchBills(query)

    suspend fun getBillWithDetails(billId: Long): BillWithDetails? =
        billDao.getBillWithDetails(billId)

    suspend fun getPayments(billId: Long): List<BillPayment> = billPaymentDao.getForBill(billId)

    suspend fun deleteBill(bill: Bill) {
        database.withTransaction {
            val items = billItemDao.getItemsForBill(bill.id)
            val quantities = StockDeltaCalculator.aggregateQuantities(
                items.map { it.itemId to it.quantity }
            )
            val existingLedger = customerLedgerDao.getForBill(bill.id).map { it.toLedgerRow() }
            val plan = BillLedgerPlanner.planCreditSale(
                existing = existingLedger,
                newCustomerId = null,
                newCreditPaise = 0L,
                billId = bill.id,
                reason = ReversalReason.BILL_DELETED
            )
            insertPlan(plan, bill.date)
            customerLedgerDao.deleteAllocationsForBill(bill.id)
            billItemDao.deleteItemsForBill(bill.id)
            billDao.deleteBill(bill)
            inventoryRepository.reverseBillStock(bill.id, quantities)
        }
    }

    suspend fun saveBill(
        customerId: Long?,
        lineItems: List<BillLineItem>,
        billRoundOff: Double = 0.0,
        existingBillId: Long? = null,
        payment: BillPaymentRequest = BillPaymentRequest.LeaveUnchanged
    ): Long {
        val subtotal = BillTotalsCalculator.lineItemsSubtotal(lineItems)
        val total = subtotal + billRoundOff
        return database.withTransaction {
            val billId: Long
            val oldQuantities: Map<Long, Int>
            val previousCustomerId: Long?
            val billDate: Long

            if (existingBillId != null) {
                val existing = billDao.getBillById(existingBillId)
                    ?: return@withTransaction saveBill(customerId, lineItems, billRoundOff, null, payment)
                oldQuantities = StockDeltaCalculator.aggregateQuantities(
                    billItemDao.getItemsForBill(existingBillId).map { it.itemId to it.quantity }
                )
                previousCustomerId = existing.customerId
                billDate = System.currentTimeMillis()
                billId = existingBillId
                billDao.updateBill(
                    existing.copy(
                        customerId = customerId,
                        total = total,
                        roundOff = billRoundOff,
                        date = billDate
                    )
                )
                billItemDao.deleteItemsForBill(existingBillId)
            } else {
                oldQuantities = emptyMap()
                previousCustomerId = null
                billDate = System.currentTimeMillis()
                val (billNumber, counter) = generateNextInvoiceNumber()
                val bill = Bill(
                    billNumber = billNumber,
                    invoiceCounter = counter,
                    customerId = customerId,
                    total = total,
                    roundOff = billRoundOff,
                    date = billDate
                )
                billId = billDao.insertBill(bill)
            }

            billItemDao.insertAll(lineItems.map { line ->
                BillItem(
                    billId = billId,
                    itemId = line.itemId,
                    itemName = line.name,
                    quantity = line.quantity,
                    mrp = line.mrp,
                    discount = line.discount,
                    finalPrice = line.unitPriceAfterDiscount,
                    roundOff = line.roundOff
                )
            })
            inventoryRepository.applyBillStockChanges(
                billId = billId,
                oldQuantities = oldQuantities,
                newLineItems = lineItems
            )
            applyPayment(
                billId = billId,
                customerId = customerId,
                previousCustomerId = previousCustomerId,
                total = total,
                billDate = billDate,
                payment = payment
            )
            billId
        }
    }

    suspend fun duplicateBill(billId: Long): Long? {
        val source = billDao.getBillWithDetails(billId) ?: return null
        val duplicatedLines = source.items.map { item ->
            BillLineItem(
                itemId = item.itemId,
                name = item.itemName,
                mrp = item.mrp,
                discount = item.discount,
                quantity = item.quantity,
                roundOff = item.roundOff
            )
        }
        val portions = billPaymentDao.getForBill(billId).map {
            PaymentPortion(it.method, it.amountPaise)
        }
        val request = if (portions.isEmpty()) {
            BillPaymentRequest.KeepLegacy
        } else {
            BillPaymentRequest.Record(portions)
        }
        return saveBill(
            customerId = source.bill.customerId,
            lineItems = duplicatedLines,
            billRoundOff = source.bill.roundOff,
            payment = request
        )
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
        lineItems: List<BillLineItem>,
        billRoundOff: Double = 0.0,
        paymentStatus: String = BillPaymentStatus.LEGACY,
        paymentLabel: String = BillPaymentStatus.LABEL_LEGACY,
        payments: List<PaymentPortion> = emptyList()
    ): Long? {
        if (billNumber.isBlank() || lineItems.isEmpty()) return null
        if (billDao.countByBillNumber(billNumber) > 0) return null

        val subtotal = BillTotalsCalculator.lineItemsSubtotal(lineItems)
        val total = subtotal + billRoundOff
        val bill = Bill(
            billNumber = billNumber,
            invoiceCounter = invoiceCounter.coerceAtLeast(0),
            customerId = customerId,
            total = total,
            roundOff = billRoundOff,
            date = date,
            paymentStatus = paymentStatus,
            paymentLabel = paymentLabel
        )
        val billId = billDao.insertBill(bill)
        billItemDao.insertAll(lineItems.map { line ->
            BillItem(
                billId = billId,
                itemId = line.itemId,
                itemName = line.name,
                quantity = line.quantity,
                mrp = line.mrp,
                discount = line.discount,
                finalPrice = line.unitPriceAfterDiscount,
                roundOff = line.roundOff
            )
        })
        payments.forEach { portion ->
            billPaymentDao.insert(
                BillPayment(billId = billId, method = portion.method, amountPaise = portion.amountPaise)
            )
        }

        if (invoiceCounter > 0) {
            val dbMax = billDao.getMaxInvoiceCounter() ?: 0
            settingsRepository.invoiceCounter = maxOf(settingsRepository.invoiceCounter, dbMax + 1)
        }
        return billId
    }

    private suspend fun applyPayment(
        billId: Long,
        customerId: Long?,
        previousCustomerId: Long?,
        total: Double,
        billDate: Long,
        payment: BillPaymentRequest
    ) {
        val existingPayments = billPaymentDao.getForBill(billId)
        val totalPaise = Money.rupeesToPaise(total)
        when (payment) {
            BillPaymentRequest.LeaveUnchanged -> {
                if (existingPayments.isEmpty()) return
                val sum = existingPayments.sumOf { it.amountPaise }
                if (sum != totalPaise) throw FinanceException(FinanceError.PAYMENT_CHANGED_WHILE_DISABLED)
            }
            BillPaymentRequest.KeepLegacy -> {
                if (existingPayments.isNotEmpty()) {
                    throw FinanceException(FinanceError.LEGACY_BILL_HAS_PAYMENTS)
                }
                billDao.updatePaymentStatus(billId, BillPaymentStatus.LEGACY, BillPaymentStatus.LABEL_LEGACY)
            }
            is BillPaymentRequest.Record -> {
                val split = PaymentSplitValidator.validate(
                    totalPaise = totalPaise,
                    portions = payment.portions,
                    hasRegisteredCustomer = customerId != null
                )
                billPaymentDao.deleteForBill(billId)
                split.portions.forEach { portion ->
                    billPaymentDao.insert(
                        BillPayment(billId = billId, method = portion.method, amountPaise = portion.amountPaise)
                    )
                }
                billDao.updatePaymentStatus(billId, BillPaymentStatus.RECORDED, split.label)
                val existingLedger = customerLedgerDao.getForBill(billId).map { it.toLedgerRow() }
                val plan = BillLedgerPlanner.planCreditSale(
                    existing = existingLedger,
                    newCustomerId = customerId ?: previousCustomerId,
                    newCreditPaise = split.creditPaise,
                    billId = billId,
                    reason = ReversalReason.BILL_EDITED
                )
                if (split.creditPaise > 0L && customerId == null) {
                    throw FinanceException(FinanceError.CREDIT_REQUIRES_CUSTOMER)
                }
                val corrected = if (split.creditPaise > 0L) {
                    plan.copy(
                        posting = plan.posting?.copy(partyId = customerId!!)
                    )
                } else {
                    plan
                }
                insertPlan(corrected, billDate)
            }
        }
    }

    private suspend fun insertPlan(
        plan: com.kex.vikrsaathi.domain.finance.LedgerPlan,
        occurredAt: Long
    ) {
        val now = System.currentTimeMillis()
        plan.reversals.forEach { reversal ->
            customerLedgerDao.insert(
                CustomerLedgerEntry(
                    customerId = reversal.partyId,
                    entryType = CustomerLedgerType.REVERSAL,
                    amountPaise = reversal.amountPaise,
                    occurredAt = occurredAt,
                    createdAt = now,
                    reason = reversal.reason,
                    referenceType = FinanceReference.BILL,
                    referenceId = reversal.billId,
                    reversesEntryId = reversal.reversesEntryId,
                    billId = reversal.billId
                )
            )
        }
        plan.posting?.let { posting ->
            customerLedgerDao.insert(
                CustomerLedgerEntry(
                    customerId = posting.partyId,
                    entryType = posting.entryType,
                    amountPaise = posting.amountPaise,
                    occurredAt = occurredAt,
                    createdAt = now,
                    referenceType = posting.referenceType,
                    referenceId = posting.referenceId,
                    billId = posting.billId
                )
            )
        }
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

internal fun CustomerLedgerEntry.toLedgerRow(): LedgerRow = LedgerRow(
    id = id,
    partyId = customerId,
    entryType = entryType,
    amountPaise = amountPaise,
    occurredAt = occurredAt,
    reversesEntryId = reversesEntryId,
    billId = billId,
    referenceType = referenceType,
    referenceId = referenceId,
    method = method,
    note = note,
    reason = reason
)
