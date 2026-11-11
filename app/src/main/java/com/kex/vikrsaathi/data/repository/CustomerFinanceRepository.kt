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
import com.kex.vikrsaathi.data.entity.CostSourceType
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
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.domain.bill.OperatorLabels
import com.kex.vikrsaathi.domain.finance.BillPaymentRequest
import com.kex.vikrsaathi.domain.finance.ExchangeMoney
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.domain.finance.PaymentPortion
import com.kex.vikrsaathi.domain.finance.RefundMethod
import com.kex.vikrsaathi.domain.finance.ReturnSettlement
import com.kex.vikrsaathi.domain.finance.ReturnValuation
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

data class BillReturnLineInput(
    val billItemId: Long,
    val quantity: Int,
    val restock: Boolean,
    val restockBatchId: Long? = null
)

data class PostedReturn(
    val returnId: Long,
    val returnValuePaise: Long,
    val replacementBillId: Long? = null
)

class CustomerFinanceRepository(
    private val database: AppDatabase,
    private val ledgerDao: CustomerLedgerDao,
    private val billDao: BillDao,
    private val billItemDao: BillItemDao,
    private val billPaymentDao: BillPaymentDao,
    private val inventoryRepository: InventoryRepository,
    private val itemCostRepository: ItemCostRepository = ItemCostRepository(database),
    private val billRepository: BillRepository? = null,
    private val batches: BatchRepository? = null
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
        if (lines.any { it.quantity <= 0 }) throw FinanceException(FinanceError.RETURN_QTY_INVALID)
        if (lines.isEmpty() && moneyRefundedPaise == 0L && creditAdjustmentPaise == 0L) {
            throw FinanceException(FinanceError.NOTHING_TO_RECORD)
        }
        return database.withTransaction {
            if (billId != null) validateReturnQuantities(billId, lines)
            val returnId = ledgerDao.insertReturn(
                SalesReturn(
                    customerId = customerId,
                    billId = billId,
                    reason = reason.trim(),
                    moneyRefundedPaise = moneyRefundedPaise,
                    creditAdjustmentPaise = creditAdjustmentPaise,
                    occurredAt = occurredAt,
                    refundMethod = when {
                        creditAdjustmentPaise > 0L && moneyRefundedPaise == 0L -> RefundMethod.STORE_CREDIT
                        moneyRefundedPaise > 0L && creditAdjustmentPaise == 0L -> RefundMethod.CASH
                        else -> ""
                    },
                    returnValuePaise = moneyRefundedPaise + creditAdjustmentPaise,
                    actorLabel = OperatorLabels.THIS_DEVICE
                )
            )
            val billItems = billId?.let { billItemDao.getItemsForBill(it) }.orEmpty()
            lines.forEach { line ->
                val matches = billItems.filter { it.itemId != null && it.itemId == line.itemId && it.quantity > 0 }
                val unitCost = if (matches.isNotEmpty() && matches.all { it.unitCostPaise != null }) {
                    val qty = matches.sumOf { it.quantity }
                    val total = matches.sumOf { it.quantity * (it.unitCostPaise ?: 0L) }
                    if (qty > 0) total / qty else null
                } else {
                    null
                }
                insertReturnLineEffects(
                    returnId = returnId,
                    itemId = line.itemId,
                    itemName = line.itemName,
                    quantity = line.quantity,
                    unitCost = unitCost,
                    billItemId = null,
                    lineValuePaise = 0L,
                    restock = true,
                    occurredAt = occurredAt
                )
            }
            insertStoreCreditLedger(
                customerId = customerId,
                creditPaise = creditAdjustmentPaise,
                moneyRefundedPaise = moneyRefundedPaise,
                reason = reason.trim(),
                occurredAt = occurredAt,
                returnId = returnId,
                billId = billId
            )
            returnId
        }
    }

    suspend fun returnsForBill(billId: Long): List<SalesReturn> = ledgerDao.getReturnsForBill(billId)

    suspend fun linesForReturn(returnId: Long): List<SalesReturnLine> = ledgerDao.getLinesForReturn(returnId)

    suspend fun postBillReturn(
        billId: Long,
        lines: List<BillReturnLineInput>,
        refundMethod: String,
        reason: String,
        note: String?,
        occurredAt: Long,
        actorLabel: String,
        walkInCustomerId: Long? = null
    ): PostedReturn {
        return database.withTransaction {
            val prepared = prepareBillReturn(billId, lines, walkInCustomerId)
            val refund = ReturnSettlement.resolveRefund(
                prepared.value.totalPaise,
                refundMethod,
                prepared.customerId != null
            )
            val returnId = insertPreparedReturn(
                prepared = prepared,
                refund = refund,
                reason = reason,
                note = note,
                occurredAt = occurredAt,
                actorLabel = actorLabel,
                exchangeBillId = null
            )
            PostedReturn(returnId, prepared.value.totalPaise, null)
        }
    }

    suspend fun postExchange(
        billId: Long,
        lines: List<BillReturnLineInput>,
        replacementLines: List<BillLineItem>,
        replacementRoundOff: Double,
        dueMethod: String,
        reason: String,
        note: String?,
        occurredAt: Long,
        actorLabel: String,
        walkInCustomerId: Long? = null,
        shortageAcknowledged: Boolean = false
    ): PostedReturn {
        val bills = billRepository ?: throw FinanceException(FinanceError.NOTHING_TO_RECORD)
        if (replacementLines.isEmpty() || replacementLines.any { it.quantity <= 0 }) {
            throw FinanceException(FinanceError.NOTHING_TO_RECORD)
        }
        return database.withTransaction {
            val prepared = prepareBillReturn(billId, lines, walkInCustomerId)
            val replacementPaise = Money.rupeesToPaise(
                BillTotalsCalculator.grandTotal(replacementLines, replacementRoundOff)
            )
            val exchange = ReturnSettlement.resolveExchange(
                prepared.value.totalPaise,
                replacementPaise,
                dueMethod,
                prepared.customerId != null
            )
            val returnId = insertPreparedReturn(
                prepared = prepared,
                refund = exchange.refund,
                reason = reason,
                note = note,
                occurredAt = occurredAt,
                actorLabel = actorLabel,
                exchangeBillId = null
            )
            val portions = exchangePortions(exchange)
            val replacementId = bills.saveBill(
                customerId = prepared.customerId,
                lineItems = replacementLines,
                billRoundOff = replacementRoundOff,
                payment = BillPaymentRequest.Record(portions),
                shortageAcknowledged = shortageAcknowledged
            )
            ledgerDao.setExchangeBill(returnId, replacementId)
            PostedReturn(returnId, prepared.value.totalPaise, replacementId)
        }
    }

    suspend fun voidSalesReturn(returnId: Long, reason: String, occurredAt: Long, actorLabel: String) {
        if (reason.isBlank()) throw FinanceException(FinanceError.REASON_REQUIRED)
        database.withTransaction {
            val original = ledgerDao.getReturnById(returnId)
                ?: throw FinanceException(FinanceError.NOTHING_TO_RECORD)
            if (original.voidedAt != null || original.voidsReturnId != null) {
                throw FinanceException(FinanceError.ALREADY_VOIDED)
            }
            if (original.exchangeBillId != null) {
                throw FinanceException(FinanceError.EXCHANGE_NOT_VOIDABLE)
            }
            val lines = ledgerDao.getLinesForReturn(returnId)
            lines.filter { it.restock }.forEach { line ->
                val itemId = line.itemId ?: return@forEach
                batches?.reverseRestock(line.restockBatchId, line.quantity, returnId, itemId)
                inventoryRepository.applyMovementUnchecked(
                    itemId = itemId,
                    delta = -line.quantity,
                    type = StockMovementType.ADJUSTMENT,
                    referenceType = StockReferenceType.SALES_RETURN,
                    referenceId = returnId,
                    note = "Void sales return"
                )
            }
            itemCostRepository.discardSource(CostSourceType.SALES_RETURN, returnId)
            val entries = ledgerDao.getAll().filter {
                it.referenceType == FinanceReference.SALES_RETURN && it.referenceId == returnId
            }
            entries.forEach { entry ->
                val reversal = BillLedgerPlanner.planNegatingReversal(entry.toLedgerRow(), reason.trim())
                ledgerDao.insert(
                    CustomerLedgerEntry(
                        customerId = reversal.partyId,
                        entryType = CustomerLedgerType.REVERSAL,
                        amountPaise = reversal.amountPaise,
                        occurredAt = occurredAt,
                        reason = reversal.reason,
                        referenceType = FinanceReference.SALES_RETURN,
                        referenceId = returnId,
                        reversesEntryId = reversal.reversesEntryId,
                        billId = reversal.billId
                    )
                )
            }
            ledgerDao.markReturnVoided(returnId, occurredAt)
            ledgerDao.insertReturn(
                SalesReturn(
                    customerId = original.customerId,
                    billId = original.billId,
                    reason = reason.trim(),
                    occurredAt = occurredAt,
                    refundMethod = RefundMethod.NONE,
                    voidsReturnId = returnId,
                    actorLabel = actorLabel
                )
            )
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
        val already = activeReturnLines(billId)
            .groupBy { it.itemId }
            .mapValues { (_, items) -> items.sumOf { it.quantity } }
        lines.forEach { line ->
            val allowed = (sold[line.itemId] ?: 0) - (already[line.itemId] ?: 0)
            if (line.quantity > allowed) throw FinanceException(FinanceError.RETURN_QTY_EXCEEDS_SOLD)
        }
    }

    private suspend fun activeReturnLines(billId: Long): List<SalesReturnLine> {
        return ledgerDao.getReturnsForBill(billId)
            .filter { it.voidedAt == null && it.voidsReturnId == null }
            .flatMap { ledgerDao.getLinesForReturn(it.id) }
    }

    private data class PreparedReturn(
        val billId: Long,
        val customerId: Long?,
        val sources: List<ReturnValuation.SourceLine>,
        val unitCostByLine: Map<Long, Long?>,
        val value: ReturnValuation.Result,
        val requests: List<BillReturnLineInput>
    )

    private suspend fun prepareBillReturn(
        billId: Long,
        lines: List<BillReturnLineInput>,
        walkInCustomerId: Long?
    ): PreparedReturn {
        val bill = billDao.getBillById(billId) ?: throw FinanceException(FinanceError.NOTHING_TO_RECORD)
        val items = billItemDao.getItemsForBill(billId)
        val sources = items.map {
            ReturnValuation.SourceLine(
                billItemId = it.id,
                itemId = it.itemId,
                itemName = it.itemName,
                quantity = it.quantity,
                finalPrice = it.finalPrice,
                lineRoundOff = it.roundOff
            )
        }
        val prior = activeReturnLines(billId).map {
            ReturnValuation.PriorLine(it.billItemId, it.itemId, it.itemName, it.quantity)
        }
        val already = ReturnValuation.alreadyByBillItem(sources, prior)
        val value = ReturnValuation.value(
            sources,
            already,
            lines.map { ReturnValuation.Request(it.billItemId, it.quantity) },
            bill.roundOff
        )
        val customerId = when {
            bill.customerId != null -> bill.customerId
            walkInCustomerId != null -> {
                if (database.customerDao().getCustomerById(walkInCustomerId) == null) {
                    throw FinanceException(FinanceError.CREDIT_REQUIRES_CUSTOMER)
                }
                walkInCustomerId
            }
            else -> null
        }
        lines.forEach { request ->
            val source = sources.first { it.billItemId == request.billItemId }
            if (request.restock && source.itemId == null) {
                throw FinanceException(FinanceError.RESTOCK_NEEDS_ITEM)
            }
        }
        return PreparedReturn(
            billId = bill.id,
            customerId = customerId,
            sources = sources,
            unitCostByLine = items.associate { it.id to it.unitCostPaise },
            value = value,
            requests = lines
        )
    }

    private suspend fun insertPreparedReturn(
        prepared: PreparedReturn,
        refund: com.kex.vikrsaathi.domain.finance.ResolvedRefund,
        reason: String,
        note: String?,
        occurredAt: Long,
        actorLabel: String,
        exchangeBillId: Long?
    ): Long {
        val returnId = ledgerDao.insertReturn(
            SalesReturn(
                customerId = prepared.customerId,
                billId = prepared.billId,
                reason = reason.trim(),
                moneyRefundedPaise = refund.moneyRefundedPaise,
                creditAdjustmentPaise = refund.creditAdjustmentPaise,
                occurredAt = occurredAt,
                refundMethod = refund.method,
                note = note?.trim()?.ifEmpty { null },
                exchangeBillId = exchangeBillId,
                returnValuePaise = prepared.value.totalPaise,
                actorLabel = actorLabel.ifBlank { OperatorLabels.THIS_DEVICE }
            )
        )
        val requests = prepared.requests.associateBy { it.billItemId }
        prepared.value.lines.forEach { valued ->
            val source = prepared.sources.first { it.billItemId == valued.billItemId }
            val request = requests.getValue(valued.billItemId)
            insertReturnLineEffects(
                returnId = returnId,
                itemId = source.itemId,
                itemName = source.itemName,
                quantity = valued.quantity,
                unitCost = prepared.unitCostByLine[valued.billItemId],
                billItemId = valued.billItemId,
                lineValuePaise = valued.valuePaise,
                restock = request.restock,
                occurredAt = occurredAt,
                restockBatchId = request.restockBatchId
            )
        }
        insertStoreCreditLedger(
            customerId = prepared.customerId,
            creditPaise = refund.creditAdjustmentPaise,
            moneyRefundedPaise = refund.moneyRefundedPaise,
            reason = reason.trim(),
            occurredAt = occurredAt,
            returnId = returnId,
            billId = prepared.billId
        )
        return returnId
    }

    private fun exchangePortions(exchange: ExchangeMoney): List<PaymentPortion> {
        val portions = mutableListOf<PaymentPortion>()
        if (exchange.exchangeCreditPaise > 0L) {
            portions += PaymentPortion(PaymentMethod.EXCHANGE, exchange.exchangeCreditPaise)
        }
        val method = exchange.customerPayMethod
        if (exchange.customerPaysPaise > 0L && method != null) {
            portions += PaymentPortion(method, exchange.customerPaysPaise)
        }
        return portions
    }

    private suspend fun insertReturnLineEffects(
        returnId: Long,
        itemId: Long?,
        itemName: String,
        quantity: Int,
        unitCost: Long?,
        billItemId: Long?,
        lineValuePaise: Long,
        restock: Boolean,
        occurredAt: Long,
        restockBatchId: Long? = null
    ) {
        val lineId = ledgerDao.insertReturnLine(
            SalesReturnLine(
                returnId = returnId,
                itemId = itemId,
                itemName = itemName,
                quantity = quantity,
                unitCostPaise = unitCost,
                billItemId = billItemId,
                restock = restock,
                lineValuePaise = lineValuePaise
            )
        )
        if (!restock || itemId == null || itemId <= 0L) return
        if (unitCost != null) {
            itemCostRepository.restore(
                itemId = itemId,
                qty = quantity,
                unitCostPaise = unitCost,
                sourceType = CostSourceType.SALES_RETURN,
                sourceId = returnId,
                receivedAt = occurredAt
            )
        }
        inventoryRepository.applyMovementUnchecked(
            itemId = itemId,
            delta = quantity,
            type = StockMovementType.SALES_RETURN,
            referenceType = StockReferenceType.SALES_RETURN,
            referenceId = returnId,
            note = "Sales return"
        )
        val placed = batches?.placeReturn(itemId, quantity, returnId, restockBatchId) ?: 0L
        if (placed != 0L) ledgerDao.setRestockBatch(lineId, placed)
    }

    private suspend fun insertStoreCreditLedger(
        customerId: Long?,
        creditPaise: Long,
        moneyRefundedPaise: Long,
        reason: String,
        occurredAt: Long,
        returnId: Long,
        billId: Long?
    ) {
        if (creditPaise == 0L || customerId == null) return
        ledgerDao.insert(
            CustomerLedgerEntry(
                customerId = customerId,
                entryType = CustomerLedgerType.SALES_RETURN,
                amountPaise = -creditPaise,
                occurredAt = occurredAt,
                reason = reason,
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

    suspend fun reverseSalesReturnStock() {
        val returns = ledgerDao.getAllReturns().associateBy { it.id }
        ledgerDao.getAllReturnLines().forEach { line ->
            val parent = returns[line.returnId] ?: return@forEach
            if (parent.voidedAt != null || parent.voidsReturnId != null) return@forEach
            if (!line.restock) return@forEach
            val itemId = line.itemId ?: return@forEach
            if (line.quantity == 0) return@forEach
            batches?.reverseRestock(line.restockBatchId, line.quantity, line.returnId, itemId)
            inventoryRepository.applyMovementUnchecked(
                itemId = itemId,
                delta = -line.quantity,
                type = StockMovementType.ADJUSTMENT,
                referenceType = StockReferenceType.SALES_RETURN,
                referenceId = line.returnId,
                note = "Reversed sales return ${parent.reason}".trim()
            )
        }
    }
}
