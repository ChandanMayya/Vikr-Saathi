package com.kex.vikrsaathi.data.repository

import androidx.room.withTransaction
import com.kex.vikrsaathi.data.dao.BillDao
import com.kex.vikrsaathi.data.dao.BillItemDao
import com.kex.vikrsaathi.data.dao.BillPaymentDao
import com.kex.vikrsaathi.data.dao.CustomerLedgerDao
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.CustomerLedgerEntry
import com.kex.vikrsaathi.data.entity.CustomerReceipt
import com.kex.vikrsaathi.data.entity.CustomerReceiptAllocation
import com.kex.vikrsaathi.data.entity.SalesReturn
import com.kex.vikrsaathi.data.entity.SalesReturnLine
import com.kex.vikrsaathi.data.entity.StockMovementType
import com.kex.vikrsaathi.data.entity.StockReferenceType
import com.kex.vikrsaathi.domain.finance.AgeBucket
import com.kex.vikrsaathi.domain.finance.AllocationPlan
import com.kex.vikrsaathi.domain.finance.BillLedgerPlanner
import com.kex.vikrsaathi.domain.finance.CustomerLedgerType
import com.kex.vikrsaathi.domain.finance.FinanceError
import com.kex.vikrsaathi.domain.finance.FinanceException
import com.kex.vikrsaathi.domain.finance.FinanceReference
import com.kex.vikrsaathi.domain.finance.FinanceResetPolicy
import com.kex.vikrsaathi.domain.finance.LedgerMath
import com.kex.vikrsaathi.domain.finance.LedgerRow
import com.kex.vikrsaathi.domain.finance.LedgerStatement
import com.kex.vikrsaathi.domain.finance.OutstandingBill
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.domain.money.Money

data class CustomerBalanceSummary(
    val customerId: Long,
    val balancePaise: Long,
    val ageBucket: AgeBucket?,
    val anchorAt: Long?
)

data class CustomerPaymentResult(
    val receiptId: Long,
    val plan: AllocationPlan
)

data class ReturnLineInput(
    val itemId: Long?,
    val itemName: String,
    val quantity: Int
)

class CustomerFinanceRepository(
    private val database: AppDatabase,
    private val ledgerDao: CustomerLedgerDao,
    private val billDao: BillDao,
    private val billItemDao: BillItemDao,
    private val billPaymentDao: BillPaymentDao,
    private val inventoryRepository: InventoryRepository
) {

    suspend fun hasLedger(customerId: Long): Boolean = ledgerDao.countForCustomer(customerId) > 0

    suspend fun balancePaise(customerId: Long): Long =
        LedgerMath.balance(ledgerDao.getForCustomer(customerId).map { it.toLedgerRow() })

    suspend fun statement(customerId: Long, fromInclusive: Long, toInclusive: Long): LedgerStatement {
        val rows = ledgerDao.getForCustomer(customerId).map { it.toLedgerRow() }
        return LedgerMath.statement(rows, fromInclusive, toInclusive)
    }

    suspend fun outstandingCustomers(now: Long = System.currentTimeMillis()): List<CustomerBalanceSummary> {
        val rows = ledgerDao.getAll().map { it.toLedgerRow() }
        return rows.groupBy { it.partyId }.mapNotNull { (customerId, entries) ->
            val balance = LedgerMath.balance(entries)
            if (balance == 0L) return@mapNotNull null
            val outstanding = outstandingBills(customerId, entries)
            val openingAt = entries
                .filter { it.entryType == CustomerLedgerType.OPENING_BALANCE }
                .minOfOrNull { it.occurredAt }
            val anchor = LedgerMath.ageingAnchor(balance, outstanding, openingAt)
            CustomerBalanceSummary(
                customerId = customerId,
                balancePaise = balance,
                ageBucket = anchor?.let { LedgerMath.ageBucket(it, now) },
                anchorAt = anchor
            )
        }.sortedByDescending { it.balancePaise }
    }

    suspend fun totalReceivablePaise(): Long =
        outstandingCustomers().filter { it.balancePaise > 0L }.sumOf { it.balancePaise }

    suspend fun addOpeningBalance(
        customerId: Long,
        amountPaise: Long,
        occurredAt: Long,
        note: String?
    ) {
        if (amountPaise == 0L) throw FinanceException(FinanceError.AMOUNT_REQUIRED)
        ledgerDao.insert(
            CustomerLedgerEntry(
                customerId = customerId,
                entryType = CustomerLedgerType.OPENING_BALANCE,
                amountPaise = amountPaise,
                occurredAt = occurredAt,
                note = note?.trim()?.ifEmpty { null },
                referenceType = FinanceReference.OPENING
            )
        )
    }

    suspend fun addPayment(
        customerId: Long,
        amountPaise: Long,
        occurredAt: Long,
        method: String,
        referenceNote: String?,
        preferredBillId: Long? = null
    ): CustomerPaymentResult {
        if (amountPaise <= 0L) throw FinanceException(FinanceError.AMOUNT_REQUIRED)
        if (method != PaymentMethod.CASH && method != PaymentMethod.UPI) {
            throw FinanceException(FinanceError.AMOUNT_REQUIRED)
        }
        return database.withTransaction {
            val receiptId = ledgerDao.insertReceipt(
                CustomerReceipt(
                    customerId = customerId,
                    amountPaise = amountPaise,
                    occurredAt = occurredAt,
                    method = method,
                    referenceNote = referenceNote?.trim()?.ifEmpty { null }
                )
            )
            val outstanding = outstandingBills(customerId)
            val plan = LedgerMath.allocateOldestFirst(outstanding, amountPaise, preferredBillId)
            plan.lines.forEach { line ->
                ledgerDao.insertAllocation(
                    CustomerReceiptAllocation(
                        receiptId = receiptId,
                        billId = line.billId,
                        amountPaise = line.amountPaise
                    )
                )
            }
            ledgerDao.insert(
                CustomerLedgerEntry(
                    customerId = customerId,
                    entryType = CustomerLedgerType.PAYMENT_RECEIVED,
                    amountPaise = -amountPaise,
                    occurredAt = occurredAt,
                    method = method,
                    note = referenceNote?.trim()?.ifEmpty { null },
                    referenceType = FinanceReference.CUSTOMER_RECEIPT,
                    referenceId = receiptId
                )
            )
            CustomerPaymentResult(receiptId, plan)
        }
    }

    suspend fun allocationsForReceipt(receiptId: Long) = ledgerDao.getAllocationsForReceipt(receiptId)

    suspend fun addSalesReturn(
        customerId: Long,
        billId: Long?,
        reason: String,
        moneyRefundedPaise: Long,
        creditAdjustmentPaise: Long,
        lines: List<ReturnLineInput>,
        occurredAt: Long
    ): Long {
        if (reason.isBlank()) throw FinanceException(FinanceError.REASON_REQUIRED)
        if (moneyRefundedPaise < 0L || creditAdjustmentPaise < 0L) {
            throw FinanceException(FinanceError.AMOUNT_REQUIRED)
        }
        val stockLines = lines.filter { it.quantity > 0 }
        if (stockLines.isEmpty() && moneyRefundedPaise == 0L && creditAdjustmentPaise == 0L) {
            throw FinanceException(FinanceError.NOTHING_TO_RECORD)
        }
        if (billId != null) {
            validateReturnQuantities(billId, stockLines)
        }
        return database.withTransaction {
            val returnId = ledgerDao.insertReturn(
                SalesReturn(
                    customerId = customerId,
                    billId = billId,
                    reason = reason.trim(),
                    moneyRefundedPaise = moneyRefundedPaise,
                    creditAdjustmentPaise = creditAdjustmentPaise,
                    occurredAt = occurredAt
                )
            )
            stockLines.forEach { line ->
                ledgerDao.insertReturnLine(
                    SalesReturnLine(
                        returnId = returnId,
                        itemId = line.itemId,
                        itemName = line.itemName,
                        quantity = line.quantity
                    )
                )
                val itemId = line.itemId
                if (itemId != null && itemId > 0L) {
                    inventoryRepository.applyMovementUnchecked(
                        itemId = itemId,
                        delta = line.quantity,
                        type = StockMovementType.SALES_RETURN,
                        referenceType = StockReferenceType.SALES_RETURN,
                        referenceId = returnId,
                        note = "Sales return"
                    )
                }
            }
            if (creditAdjustmentPaise > 0L || moneyRefundedPaise > 0L) {
                ledgerDao.insert(
                    CustomerLedgerEntry(
                        customerId = customerId,
                        entryType = CustomerLedgerType.SALES_RETURN,
                        amountPaise = -creditAdjustmentPaise,
                        occurredAt = occurredAt,
                        reason = reason.trim(),
                        note = if (moneyRefundedPaise > 0L) {
                            "Money refunded ${Money.paiseToRupees(moneyRefundedPaise)}"
                        } else {
                            null
                        },
                        referenceType = FinanceReference.SALES_RETURN,
                        referenceId = returnId,
                        billId = billId
                    )
                )
            }
            returnId
        }
    }

    suspend fun addCorrection(
        customerId: Long,
        amountPaise: Long,
        occurredAt: Long,
        reason: String,
        reversesEntryId: Long? = null
    ) {
        if (reason.isBlank()) throw FinanceException(FinanceError.REASON_REQUIRED)
        if (reversesEntryId != null) {
            val original = ledgerDao.getById(reversesEntryId)?.toLedgerRow()
                ?: throw FinanceException(FinanceError.NOTHING_TO_RECORD)
            val reversal = BillLedgerPlanner.planNegatingReversal(original, reason)
            ledgerDao.insert(
                CustomerLedgerEntry(
                    customerId = reversal.partyId,
                    entryType = CustomerLedgerType.REVERSAL,
                    amountPaise = reversal.amountPaise,
                    occurredAt = occurredAt,
                    reason = reversal.reason,
                    referenceType = FinanceReference.ADJUSTMENT,
                    reversesEntryId = reversal.reversesEntryId,
                    billId = reversal.billId
                )
            )
            return
        }
        if (amountPaise == 0L) throw FinanceException(FinanceError.AMOUNT_REQUIRED)
        ledgerDao.insert(
            CustomerLedgerEntry(
                customerId = customerId,
                entryType = CustomerLedgerType.CREDIT_ADJUSTMENT,
                amountPaise = amountPaise,
                occurredAt = occurredAt,
                reason = reason.trim(),
                referenceType = FinanceReference.ADJUSTMENT
            )
        )
    }

    suspend fun clearCustomerBalances() {
        database.withTransaction {
            reverseSalesReturnStock()
            ledgerDao.deleteAllAllocations()
            ledgerDao.deleteAllReceipts()
            ledgerDao.deleteAllReturnLines()
            ledgerDao.deleteAllReturns()
            ledgerDao.deleteAll()
            billPaymentDao.deleteAll()
            billDao.markAllPaymentsLegacy()
        }
    }

    suspend fun clearBillLinkedFinanceForSalesReset() {
        database.withTransaction {
            reverseSalesReturnStock()
            val doomed = ledgerDao.getAll().map { it.toLedgerRow() }.filter {
                FinanceResetPolicy.isBillLinkedCustomerEntry(it)
            }.map { it.id }
            if (doomed.isNotEmpty()) ledgerDao.deleteByIds(doomed)
            ledgerDao.deleteAllAllocations()
            ledgerDao.deleteAllReturnLines()
            ledgerDao.deleteAllReturns()
        }
    }

    suspend fun outstandingBills(customerId: Long, rows: List<LedgerRow>? = null): List<OutstandingBill> {
        val entries = rows ?: ledgerDao.getForCustomer(customerId).map { it.toLedgerRow() }
        val reversed = entries.mapNotNull { it.reversesEntryId }.toSet()
        val credits = entries.filter {
            it.entryType == CustomerLedgerType.CREDIT_SALE && it.billId != null && it.id !in reversed
        }.groupBy { it.billId!! }
        val returns = entries.filter {
            it.entryType == CustomerLedgerType.SALES_RETURN && it.billId != null
        }.groupBy { it.billId!! }
        val allocated = ledgerDao.getAllAllocations()
            .groupBy { it.billId }
            .mapValues { (_, rows) -> rows.sumOf { it.amountPaise } }
        return credits.map { (billId, creditRows) ->
            val bill = billDao.getBillById(billId)
            val credit = creditRows.sumOf { it.amountPaise }
            val relieved = returns[billId].orEmpty().sumOf { -it.amountPaise }
            val open = LedgerMath.billOutstanding(credit, allocated[billId] ?: 0L, relieved)
            OutstandingBill(billId, bill?.date ?: creditRows.minOf { it.occurredAt }, open)
        }.filter { it.outstandingPaise > 0L }
    }

    private suspend fun validateReturnQuantities(billId: Long, lines: List<ReturnLineInput>) {
        val sold = billItemDao.getItemsForBill(billId).groupBy { it.itemId }.mapValues { (_, items) ->
            items.sumOf { it.quantity }
        }
        val already = ledgerDao.getReturnsForBill(billId).flatMap { ledgerDao.getLinesForReturn(it.id) }
            .groupBy { it.itemId }
            .mapValues { (_, items) -> items.sumOf { it.quantity } }
        lines.forEach { line ->
            val key = line.itemId
            val allowed = (sold[key] ?: 0) - (already[key] ?: 0)
            if (line.quantity > allowed) throw FinanceException(FinanceError.RETURN_QTY_EXCEEDS_SOLD)
        }
    }

    suspend fun reverseSalesReturnStock() {
        val returns = ledgerDao.getAllReturns().associateBy { it.id }
        ledgerDao.getAllReturnLines().forEach { line ->
            val itemId = line.itemId ?: return@forEach
            if (line.quantity == 0) return@forEach
            inventoryRepository.applyMovementUnchecked(
                itemId = itemId,
                delta = -line.quantity,
                type = StockMovementType.ADJUSTMENT,
                referenceType = StockReferenceType.SALES_RETURN,
                referenceId = line.returnId,
                note = "Reversed sales return ${returns[line.returnId]?.reason.orEmpty()}".trim()
            )
        }
    }
}
