package com.kex.vikrsaathi.data.repository

import androidx.room.withTransaction
import com.kex.vikrsaathi.data.dao.ItemDao
import com.kex.vikrsaathi.data.dao.PurchaseDao
import com.kex.vikrsaathi.data.dao.VendorDao
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.GoodsReceipt
import com.kex.vikrsaathi.data.entity.GoodsReceiptLine
import com.kex.vikrsaathi.data.entity.PurchaseInvoice
import com.kex.vikrsaathi.data.entity.PurchaseInvoiceLine
import com.kex.vikrsaathi.data.entity.PurchaseOrder
import com.kex.vikrsaathi.data.entity.PurchaseOrderLine
import com.kex.vikrsaathi.data.entity.StockMovementType
import com.kex.vikrsaathi.data.entity.StockReferenceType
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.data.entity.VendorLedgerEntry
import com.kex.vikrsaathi.data.entity.VendorPayment
import com.kex.vikrsaathi.data.entity.VendorPaymentAllocation
import com.kex.vikrsaathi.data.entity.VendorQuote
import com.kex.vikrsaathi.data.entity.VendorQuoteLine
import com.kex.vikrsaathi.domain.finance.AllocationPlan
import com.kex.vikrsaathi.domain.finance.BillLedgerPlanner
import com.kex.vikrsaathi.domain.finance.FinanceError
import com.kex.vikrsaathi.domain.finance.FinanceException
import com.kex.vikrsaathi.domain.finance.FinanceReference
import com.kex.vikrsaathi.domain.finance.LedgerMath
import com.kex.vikrsaathi.domain.finance.LedgerRow
import com.kex.vikrsaathi.domain.finance.LedgerStatement
import com.kex.vikrsaathi.domain.finance.OutstandingBill
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.domain.finance.PurchaseMath
import com.kex.vikrsaathi.domain.finance.QtyReconciliation
import com.kex.vikrsaathi.domain.finance.QuoteComparisonRow
import com.kex.vikrsaathi.domain.finance.QuoteLandedTotal
import com.kex.vikrsaathi.domain.finance.QuoteLineSnapshot
import com.kex.vikrsaathi.domain.finance.QuoteSnapshot
import com.kex.vikrsaathi.domain.finance.ReceiptQtyInput
import com.kex.vikrsaathi.domain.finance.ReversalReason
import com.kex.vikrsaathi.domain.finance.VendorLedgerType

data class OrderLineInput(
    val itemId: Long?,
    val itemName: String,
    val quantity: Int,
    val ratePaise: Long
)

data class ReceiptLineInput(
    val orderLineId: Long?,
    val itemId: Long?,
    val itemName: String,
    val orderedQty: Int,
    val receivedQty: Int,
    val damagedQty: Int,
    val rejectedQty: Int
)

data class InvoiceLineInput(
    val itemId: Long?,
    val itemName: String,
    val quantity: Int,
    val ratePaise: Long
)

data class QuoteLineInput(
    val itemId: Long?,
    val itemName: String,
    val ratePaise: Long,
    val availableQty: Int,
    val notes: String = ""
)

class VendorRepository(
    private val database: AppDatabase,
    private val vendorDao: VendorDao,
    private val purchaseDao: PurchaseDao,
    private val itemDao: ItemDao,
    private val inventoryRepository: InventoryRepository
) {
    suspend fun getActive(): List<Vendor> = vendorDao.getActive()
    suspend fun getAll(): List<Vendor> = vendorDao.getAll()
    suspend fun getById(id: Long): Vendor? = vendorDao.getById(id)
    suspend fun search(query: String): List<Vendor> = vendorDao.search(query)

    suspend fun save(vendor: Vendor): Long {
        if (vendor.name.isBlank()) throw FinanceException(FinanceError.VENDOR_REQUIRED)
        return if (vendor.id == 0L) vendorDao.insert(vendor.copy(name = vendor.name.trim()))
        else {
            vendorDao.update(vendor.copy(name = vendor.name.trim()))
            vendor.id
        }
    }

    suspend fun archive(vendorId: Long) {
        val vendor = vendorDao.getById(vendorId) ?: return
        vendorDao.update(vendor.copy(archived = true))
    }

    suspend fun deleteIfNoHistory(vendor: Vendor) {
        val history = vendorDao.countLedger(vendor.id) > 0 ||
            purchaseDao.countOrders(vendor.id) > 0 ||
            purchaseDao.countReceipts(vendor.id) > 0 ||
            purchaseDao.countInvoices(vendor.id) > 0 ||
            purchaseDao.countQuotes(vendor.id) > 0
        if (history) throw FinanceException(FinanceError.VENDOR_HAS_HISTORY)
        vendorDao.delete(vendor)
    }

    suspend fun balancePaise(vendorId: Long): Long =
        LedgerMath.balance(vendorDao.getLedger(vendorId).map { it.toRow() })

    suspend fun totalPayablePaise(): Long =
        vendorDao.getAllLedger().groupBy { it.vendorId }.values.sumOf { rows ->
            LedgerMath.balance(rows.map { it.toRow() }).coerceAtLeast(0L)
        }

    suspend fun statement(vendorId: Long, fromInclusive: Long, toInclusive: Long): LedgerStatement =
        LedgerMath.statement(vendorDao.getLedger(vendorId).map { it.toRow() }, fromInclusive, toInclusive)

    suspend fun addOpeningBalance(vendorId: Long, amountPaise: Long, occurredAt: Long, note: String?) {
        if (amountPaise == 0L) throw FinanceException(FinanceError.AMOUNT_REQUIRED)
        vendorDao.insertLedger(
            VendorLedgerEntry(
                vendorId = vendorId,
                entryType = VendorLedgerType.OPENING_BALANCE,
                amountPaise = amountPaise,
                occurredAt = occurredAt,
                note = note?.trim()?.ifEmpty { null },
                referenceType = FinanceReference.OPENING
            )
        )
    }

    suspend fun addPayment(
        vendorId: Long,
        amountPaise: Long,
        occurredAt: Long,
        method: String,
        referenceNote: String?,
        preferredInvoiceId: Long? = null
    ): AllocationPlan {
        if (amountPaise <= 0L) throw FinanceException(FinanceError.AMOUNT_REQUIRED)
        if (method != PaymentMethod.CASH && method != PaymentMethod.UPI) {
            throw FinanceException(FinanceError.AMOUNT_REQUIRED)
        }
        return database.withTransaction {
            val paymentId = vendorDao.insertPayment(
                VendorPayment(
                    vendorId = vendorId,
                    amountPaise = amountPaise,
                    occurredAt = occurredAt,
                    method = method,
                    referenceNote = referenceNote?.trim()?.ifEmpty { null }
                )
            )
            val plan = LedgerMath.allocateOldestFirst(
                outstandingInvoices(vendorId),
                amountPaise,
                preferredInvoiceId
            )
            plan.lines.forEach { line ->
                vendorDao.insertPaymentAllocation(
                    VendorPaymentAllocation(
                        paymentId = paymentId,
                        invoiceId = line.billId,
                        amountPaise = line.amountPaise
                    )
                )
            }
            vendorDao.insertLedger(
                VendorLedgerEntry(
                    vendorId = vendorId,
                    entryType = VendorLedgerType.VENDOR_PAYMENT,
                    amountPaise = -amountPaise,
                    occurredAt = occurredAt,
                    method = method,
                    note = referenceNote?.trim()?.ifEmpty { null },
                    referenceType = FinanceReference.VENDOR_PAYMENT,
                    referenceId = paymentId
                )
            )
            plan
        }
    }

    suspend fun addCorrection(vendorId: Long, amountPaise: Long, occurredAt: Long, reason: String, reversesEntryId: Long?) {
        if (reason.isBlank()) throw FinanceException(FinanceError.REASON_REQUIRED)
        if (reversesEntryId != null) {
            val original = vendorDao.getLedgerById(reversesEntryId)?.toRow()
                ?: throw FinanceException(FinanceError.NOTHING_TO_RECORD)
            val reversal = BillLedgerPlanner.planNegatingReversal(original, reason)
            vendorDao.insertLedger(
                VendorLedgerEntry(
                    vendorId = reversal.partyId,
                    entryType = VendorLedgerType.REVERSAL,
                    amountPaise = reversal.amountPaise,
                    occurredAt = occurredAt,
                    reason = reversal.reason,
                    referenceType = FinanceReference.ADJUSTMENT,
                    reversesEntryId = reversal.reversesEntryId,
                    invoiceId = reversal.billId
                )
            )
            return
        }
        if (amountPaise == 0L) throw FinanceException(FinanceError.AMOUNT_REQUIRED)
        vendorDao.insertLedger(
            VendorLedgerEntry(
                vendorId = vendorId,
                entryType = VendorLedgerType.PURCHASE_RETURN,
                amountPaise = amountPaise,
                occurredAt = occurredAt,
                reason = reason.trim(),
                referenceType = FinanceReference.ADJUSTMENT
            )
        )
    }

    suspend fun saveOrder(
        vendorId: Long,
        expectedDelivery: Long?,
        notes: String,
        quoteId: Long?,
        lines: List<OrderLineInput>
    ): Long {
        if (lines.isEmpty() || lines.any { it.quantity <= 0 || it.itemName.isBlank() }) {
            throw FinanceException(FinanceError.QUOTE_LINE_REQUIRED)
        }
        return database.withTransaction {
            val id = purchaseDao.insertOrder(
                PurchaseOrder(
                    vendorId = vendorId,
                    orderNumber = "PENDING",
                    quoteId = quoteId,
                    expectedDelivery = expectedDelivery,
                    notes = notes.trim(),
                    status = "ORDERED"
                )
            )
            val number = "PO-" + id.toString().padStart(3, '0')
            purchaseDao.updateOrder(
                purchaseDao.getOrder(id)!!.copy(orderNumber = number)
            )
            lines.forEach { line ->
                purchaseDao.insertOrderLine(
                    PurchaseOrderLine(
                        orderId = id,
                        itemId = line.itemId,
                        itemName = line.itemName.trim(),
                        quantity = line.quantity,
                        ratePaise = line.ratePaise
                    )
                )
            }
            id
        }
    }

    suspend fun saveReceipt(
        vendorId: Long,
        orderId: Long?,
        notes: String,
        receivedAt: Long,
        lines: List<ReceiptLineInput>
    ): Long {
        if (lines.isEmpty()) throw FinanceException(FinanceError.RECEIPT_QTY_INVALID)
        val computed = lines.map { input ->
            val qty = PurchaseMath.receiptQuantities(
                ReceiptQtyInput(input.orderedQty, input.receivedQty, input.damagedQty, input.rejectedQty)
            )
            input to qty
        }
        return database.withTransaction {
            val id = purchaseDao.insertReceipt(
                GoodsReceipt(
                    vendorId = vendorId,
                    orderId = orderId,
                    receiptNumber = "PENDING",
                    receivedAt = receivedAt,
                    notes = notes.trim(),
                    directReceipt = orderId == null
                )
            )
            purchaseDao.setReceiptNumber(id, "GR-" + id.toString().padStart(3, '0'))
            computed.forEach { (input, qty) ->
                purchaseDao.insertReceiptLine(
                    GoodsReceiptLine(
                        receiptId = id,
                        orderLineId = input.orderLineId,
                        itemId = input.itemId,
                        itemName = input.itemName,
                        orderedQty = qty.orderedQty,
                        receivedQty = qty.receivedQty,
                        shortQty = qty.shortQty,
                        damagedQty = qty.damagedQty,
                        rejectedQty = qty.rejectedQty,
                        acceptedQty = qty.acceptedQty
                    )
                )
                val itemId = input.itemId
                if (itemId != null && qty.acceptedQty > 0) {
                    inventoryRepository.applyMovementUnchecked(
                        itemId = itemId,
                        delta = qty.acceptedQty,
                        type = StockMovementType.PURCHASE_RECEIPT,
                        referenceType = StockReferenceType.GOODS_RECEIPT,
                        referenceId = id,
                        note = "Goods receipt"
                    )
                }
            }
            id
        }
    }

    suspend fun saveInvoice(
        vendorId: Long,
        invoiceNumber: String,
        invoiceDate: Long,
        orderId: Long?,
        receiptId: Long?,
        deliveryChargesPaise: Long,
        otherChargesPaise: Long,
        discountPaise: Long,
        notes: String,
        lines: List<InvoiceLineInput>,
        existingInvoiceId: Long? = null
    ): Long {
        if (invoiceNumber.isBlank() || lines.isEmpty()) throw FinanceException(FinanceError.QUOTE_LINE_REQUIRED)
        val lineTotals = lines.map { it.ratePaise * it.quantity }
        val total = PurchaseMath.invoiceTotalPaise(
            lineTotals, deliveryChargesPaise, otherChargesPaise, discountPaise
        )
        return database.withTransaction {
            if (existingInvoiceId != null) {
                reverseInvoiceLedger(existingInvoiceId, ReversalReason.INVOICE_EDITED)
                purchaseDao.deleteInvoiceLines(existingInvoiceId)
            }
            val invoiceId = if (existingInvoiceId != null) {
                val existing = purchaseDao.getInvoice(existingInvoiceId)
                    ?: throw FinanceException(FinanceError.NOTHING_TO_RECORD)
                purchaseDao.updateInvoice(
                    existing.copy(
                        vendorId = vendorId,
                        invoiceNumber = invoiceNumber.trim(),
                        invoiceDate = invoiceDate,
                        orderId = orderId,
                        receiptId = receiptId,
                        deliveryChargesPaise = deliveryChargesPaise,
                        otherChargesPaise = otherChargesPaise,
                        discountPaise = discountPaise,
                        totalPaise = total,
                        notes = notes.trim()
                    )
                )
                existingInvoiceId
            } else {
                purchaseDao.insertInvoice(
                    PurchaseInvoice(
                        vendorId = vendorId,
                        invoiceNumber = invoiceNumber.trim(),
                        invoiceDate = invoiceDate,
                        orderId = orderId,
                        receiptId = receiptId,
                        deliveryChargesPaise = deliveryChargesPaise,
                        otherChargesPaise = otherChargesPaise,
                        discountPaise = discountPaise,
                        totalPaise = total,
                        notes = notes.trim()
                    )
                )
            }
            lines.forEach { line ->
                purchaseDao.insertInvoiceLine(
                    PurchaseInvoiceLine(
                        invoiceId = invoiceId,
                        itemId = line.itemId,
                        itemName = line.itemName.trim(),
                        quantity = line.quantity,
                        ratePaise = line.ratePaise,
                        lineTotalPaise = line.ratePaise * line.quantity
                    )
                )
            }
            if (total != 0L) {
                vendorDao.insertLedger(
                    VendorLedgerEntry(
                        vendorId = vendorId,
                        entryType = VendorLedgerType.PURCHASE_INVOICE,
                        amountPaise = total,
                        occurredAt = invoiceDate,
                        referenceType = FinanceReference.PURCHASE_INVOICE,
                        referenceId = invoiceId,
                        invoiceId = invoiceId,
                        note = invoiceNumber.trim()
                    )
                )
            }
            invoiceId
        }
    }

    suspend fun addPurchaseReturnCredit(vendorId: Long, invoiceId: Long?, amountPaise: Long, occurredAt: Long, reason: String) {
        if (reason.isBlank()) throw FinanceException(FinanceError.REASON_REQUIRED)
        if (amountPaise <= 0L) throw FinanceException(FinanceError.AMOUNT_REQUIRED)
        vendorDao.insertLedger(
            VendorLedgerEntry(
                vendorId = vendorId,
                entryType = VendorLedgerType.PURCHASE_RETURN,
                amountPaise = -amountPaise,
                occurredAt = occurredAt,
                reason = reason.trim(),
                referenceType = FinanceReference.PURCHASE_RETURN,
                invoiceId = invoiceId
            )
        )
    }

    suspend fun saveQuote(
        vendorId: Long,
        quoteDate: Long,
        validUntil: Long?,
        deliveryChargesPaise: Long,
        discountPaise: Long,
        expectedDelivery: Long?,
        notes: String,
        lines: List<QuoteLineInput>
    ): Long {
        if (lines.isEmpty()) throw FinanceException(FinanceError.QUOTE_LINE_REQUIRED)
        return database.withTransaction {
            val id = purchaseDao.insertQuote(
                VendorQuote(
                    vendorId = vendorId,
                    quoteDate = quoteDate,
                    validUntil = validUntil,
                    deliveryChargesPaise = deliveryChargesPaise,
                    discountPaise = discountPaise,
                    expectedDelivery = expectedDelivery,
                    notes = notes.trim()
                )
            )
            lines.forEach { line ->
                purchaseDao.insertQuoteLine(
                    VendorQuoteLine(
                        quoteId = id,
                        itemId = line.itemId,
                        itemName = line.itemName.trim(),
                        ratePaise = line.ratePaise,
                        availableQty = line.availableQty,
                        notes = line.notes.trim()
                    )
                )
            }
            id
        }
    }

    suspend fun quoteSnapshots(quoteIds: List<Long>? = null): List<QuoteSnapshot> {
        val quotes = purchaseDao.getQuotes().filter { quoteIds == null || it.id in quoteIds }
        return quotes.map { quote ->
            val vendor = vendorDao.getById(quote.vendorId)
            QuoteSnapshot(
                quoteId = quote.id,
                vendorName = vendor?.name ?: "Vendor",
                deliveryChargesPaise = quote.deliveryChargesPaise,
                discountPaise = quote.discountPaise,
                lines = purchaseDao.getQuoteLines(quote.id).map { line ->
                    QuoteLineSnapshot(
                        itemKey = line.itemId?.toString() ?: line.itemName.lowercase(),
                        itemName = line.itemName,
                        ratePaise = line.ratePaise,
                        quantity = line.availableQty
                    )
                }
            )
        }
    }

    suspend fun compare(quoteIds: List<Long>): Pair<List<QuoteComparisonRow>, List<QuoteLandedTotal>> {
        val snapshots = quoteSnapshots(quoteIds)
        val rows = PurchaseMath.compareQuotes(snapshots)
        val required = rows.map { it.itemKey }.toSet()
        val totals = snapshots.map { PurchaseMath.landedTotal(it, required) }
        return rows to totals
    }

    suspend fun reconcile(orderId: Long?, receiptId: Long?, invoiceId: Long?): List<QtyReconciliation> {
        val ordered = linkedMapOf<String, Pair<String, Int>>()
        if (orderId != null) {
            purchaseDao.getOrderLines(orderId).forEach { line ->
                val key = line.itemId?.toString() ?: line.itemName.lowercase()
                val current = ordered[key]
                ordered[key] = line.itemName to ((current?.second ?: 0) + line.quantity)
            }
        }
        val accepted = mutableMapOf<String, Int>()
        val receiptLines = if (receiptId != null) purchaseDao.getReceiptLines(receiptId) else purchaseDao.getAllReceiptLines()
            .filter { orderId == null || purchaseDao.getReceipt(it.receiptId)?.orderId == orderId }
        receiptLines.forEach { line ->
            val key = line.itemId?.toString() ?: line.itemName.lowercase()
            accepted[key] = (accepted[key] ?: 0) + line.acceptedQty
        }
        val invoiced = mutableMapOf<String, Int>()
        val invoiceLines = if (invoiceId != null) purchaseDao.getInvoiceLines(invoiceId) else emptyList()
        invoiceLines.forEach { line ->
            val key = line.itemId?.toString() ?: line.itemName.lowercase()
            invoiced[key] = (invoiced[key] ?: 0) + line.quantity
        }
        return PurchaseMath.reconcile(ordered, accepted, invoiced)
    }

    suspend fun orders() = purchaseDao.getOrders()
    suspend fun orderLines(orderId: Long) = purchaseDao.getOrderLines(orderId)
    suspend fun receipts() = purchaseDao.getReceipts()
    suspend fun receiptLines(receiptId: Long) = purchaseDao.getReceiptLines(receiptId)
    suspend fun invoices() = purchaseDao.getInvoices()
    suspend fun invoiceLines(invoiceId: Long) = purchaseDao.getInvoiceLines(invoiceId)
    suspend fun quotes() = purchaseDao.getQuotes()
    suspend fun quoteLines(quoteId: Long) = purchaseDao.getQuoteLines(quoteId)
    suspend fun paymentAllocations(paymentId: Long) = vendorDao.getAllocationsForPayment(paymentId)

    suspend fun clearVendors() {
        database.withTransaction {
            purchaseDao.getAllReceiptLines().forEach { line ->
                val itemId = line.itemId ?: return@forEach
                if (line.acceptedQty == 0) return@forEach
                inventoryRepository.applyMovementUnchecked(
                    itemId = itemId,
                    delta = -line.acceptedQty,
                    type = StockMovementType.PURCHASE_REVERSAL,
                    referenceType = StockReferenceType.GOODS_RECEIPT,
                    referenceId = line.receiptId,
                    note = "Vendor reset reversed goods receipt"
                )
            }
            vendorDao.deleteAllPaymentAllocations()
            vendorDao.deleteAllPayments()
            vendorDao.deleteAllLedger()
            purchaseDao.deleteAllInvoiceLines()
            purchaseDao.deleteAllInvoices()
            purchaseDao.deleteAllReceiptLines()
            purchaseDao.deleteAllReceipts()
            purchaseDao.deleteAllOrderLines()
            purchaseDao.deleteAllOrders()
            purchaseDao.deleteAllQuoteLines()
            purchaseDao.deleteAllQuotes()
            itemDao.clearPreferredVendors()
            vendorDao.deleteAll()
        }
    }

    private suspend fun outstandingInvoices(vendorId: Long): List<OutstandingBill> {
        val entries = vendorDao.getLedger(vendorId).map { it.toRow() }
        val reversed = entries.mapNotNull { it.reversesEntryId }.toSet()
        val invoices = entries.filter {
            it.entryType == VendorLedgerType.PURCHASE_INVOICE && it.billId != null && it.id !in reversed
        }.groupBy { it.billId!! }
        val credits = entries.filter {
            it.entryType == VendorLedgerType.PURCHASE_RETURN && it.billId != null
        }.groupBy { it.billId!! }
        val allocated = vendorDao.getAllPaymentAllocations().groupBy { it.invoiceId }
            .mapValues { (_, rows) -> rows.sumOf { it.amountPaise } }
        return invoices.map { (invoiceId, rows) ->
            val payable = rows.sumOf { it.amountPaise }
            val relieved = credits[invoiceId].orEmpty().sumOf { -it.amountPaise }
            val open = LedgerMath.billOutstanding(payable, allocated[invoiceId] ?: 0L, relieved)
            OutstandingBill(invoiceId, rows.minOf { it.occurredAt }, open)
        }
    }

    private suspend fun reverseInvoiceLedger(invoiceId: Long, reason: String) {
        val entries = vendorDao.getLedgerForInvoice(invoiceId).map { it.toRow() }
        val reversed = entries.mapNotNull { it.reversesEntryId }.toSet()
        entries.filter { it.entryType == VendorLedgerType.PURCHASE_INVOICE && it.id !in reversed }
            .forEach { row ->
                val reversal = BillLedgerPlanner.planNegatingReversal(row, reason)
                vendorDao.insertLedger(
                    VendorLedgerEntry(
                        vendorId = reversal.partyId,
                        entryType = VendorLedgerType.REVERSAL,
                        amountPaise = reversal.amountPaise,
                        occurredAt = System.currentTimeMillis(),
                        reason = reversal.reason,
                        referenceType = FinanceReference.PURCHASE_INVOICE,
                        referenceId = invoiceId,
                        reversesEntryId = reversal.reversesEntryId,
                        invoiceId = invoiceId
                    )
                )
            }
    }
}

private fun VendorLedgerEntry.toRow(): LedgerRow = LedgerRow(
    id = id,
    partyId = vendorId,
    entryType = entryType,
    amountPaise = amountPaise,
    occurredAt = occurredAt,
    reversesEntryId = reversesEntryId,
    billId = invoiceId,
    referenceType = referenceType,
    referenceId = referenceId,
    method = method,
    note = note,
    reason = reason
)
