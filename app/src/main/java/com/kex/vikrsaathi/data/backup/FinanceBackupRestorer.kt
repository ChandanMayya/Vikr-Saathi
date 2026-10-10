package com.kex.vikrsaathi.data.backup

import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.BillPayment
import com.kex.vikrsaathi.data.entity.CustomerLedgerEntry
import com.kex.vikrsaathi.data.entity.CustomerReceipt
import com.kex.vikrsaathi.data.entity.CustomerReceiptAllocation
import com.kex.vikrsaathi.data.entity.GoodsReceipt
import com.kex.vikrsaathi.data.entity.GoodsReceiptLine
import com.kex.vikrsaathi.data.entity.PurchaseInvoice
import com.kex.vikrsaathi.data.entity.PurchaseInvoiceLine
import com.kex.vikrsaathi.data.entity.PurchaseOrder
import com.kex.vikrsaathi.data.entity.PurchaseOrderLine
import com.kex.vikrsaathi.data.entity.SalesReturn
import com.kex.vikrsaathi.data.entity.SalesReturnLine
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.data.entity.VendorLedgerEntry
import com.kex.vikrsaathi.data.entity.VendorPayment
import com.kex.vikrsaathi.data.entity.VendorPaymentAllocation
import com.kex.vikrsaathi.data.entity.VendorQuote
import com.kex.vikrsaathi.data.entity.VendorQuoteLine
import com.kex.vikrsaathi.domain.finance.BillPaymentStatus
import com.kex.vikrsaathi.domain.finance.FinanceRestoreLinks
import com.kex.vikrsaathi.domain.finance.IdRemapper
import com.kex.vikrsaathi.domain.finance.PaymentSplitValidator
import org.json.JSONObject

class FinanceBackupRestorer(
    private val database: AppDatabase
) {
    data class Result(
        val vendorsImported: Int = 0,
        val ledgerImported: Int = 0,
        val errors: List<String> = emptyList()
    )

    suspend fun restore(
        root: JSONObject,
        customerIds: Map<Long, Long>,
        itemIds: Map<Long, Long>,
        billIds: Map<Long, Long>,
        billsAlreadyPaid: Set<Long>,
        presentCustomerIds: Set<Long>,
        presentItemIds: Set<Long>,
        presentBillIds: Set<Long>,
        presentVendorIds: Set<Long>,
        applyStockEffects: Boolean
    ): Result {
        val errors = mutableListOf<String>()
        var vendorsImported = 0
        var ledgerImported = 0
        val vendorIds = mutableMapOf<Long, Long>()
        val quoteIds = mutableMapOf<Long, Long>()
        val orderIds = mutableMapOf<Long, Long>()
        val orderLineIds = mutableMapOf<Long, Long>()
        val receiptIds = mutableMapOf<Long, Long>()
        val invoiceIds = mutableMapOf<Long, Long>()
        val paymentIds = mutableMapOf<Long, Long>()
        val receiptCustomerIds = mutableMapOf<Long, Long>()
        val returnIds = mutableMapOf<Long, Long>()
        val customerLedgerIds = mutableMapOf<Long, Long>()
        val vendorLedgerIds = mutableMapOf<Long, Long>()
        val importedLedgerLegacy = mutableSetOf<Long>()
        val pendingCustomerLedger = mutableListOf<CustomerLedgerEntry>()
        val pendingVendorLedger = mutableListOf<VendorLedgerEntry>()

        fun customer(legacy: Long?) = IdRemapper.resolve(legacy, customerIds, presentCustomerIds)
        fun item(legacy: Long?) = IdRemapper.resolve(legacy, itemIds, presentItemIds)
        fun bill(legacy: Long?) = IdRemapper.resolve(legacy, billIds, presentBillIds)
        fun vendor(legacy: Long?) = IdRemapper.resolve(legacy, vendorIds, presentVendorIds)

        if (root.has("vendors")) {
            FinanceBackupCodec.vendors(root).forEach { legacy ->
                try {
                    val id = database.vendorDao().insert(legacy.copy(id = 0))
                    vendorIds[legacy.id] = id
                    vendorsImported++
                } catch (e: Exception) {
                    errors.add("Vendor ${legacy.name}: ${e.message}")
                }
            }
            restorePurchase(
                root, vendorIds, itemIds, presentItemIds, presentVendorIds,
                quoteIds, orderIds, orderLineIds, receiptIds, invoiceIds, errors, applyStockEffects
            )
            FinanceBackupCodec.vendorPayments(root).forEach { legacy ->
                val vendorId = vendor(legacy.vendorId) ?: return@forEach
                val id = database.vendorDao().insertPayment(legacy.copy(id = 0, vendorId = vendorId))
                paymentIds[legacy.id] = id
            }
            FinanceBackupCodec.vendorAllocations(root).forEach { legacy ->
                val paymentId = paymentIds[legacy.paymentId] ?: return@forEach
                val invoiceId = invoiceIds[legacy.invoiceId] ?: IdRemapper.resolve(legacy.invoiceId, invoiceIds, emptySet())
                    ?: return@forEach
                database.vendorDao().insertPaymentAllocation(
                    VendorPaymentAllocation(paymentId = paymentId, invoiceId = invoiceId, amountPaise = legacy.amountPaise)
                )
            }
            val vendorLedgerLegacy = FinanceBackupCodec.vendorLedger(root)
            vendorLedgerLegacy.forEach { legacy ->
                val vendorId = vendor(legacy.vendorId) ?: return@forEach
                val id = database.vendorDao().insertLedger(
                    legacy.copy(
                        id = 0,
                        vendorId = vendorId,
                        invoiceId = legacy.invoiceId?.let { invoiceIds[it] },
                        reversesEntryId = null,
                        referenceId = null
                    )
                )
                vendorLedgerIds[legacy.id] = id
                pendingVendorLedger += legacy
            }
            pendingVendorLedger.forEach { legacy ->
                val id = vendorLedgerIds[legacy.id] ?: return@forEach
                database.vendorDao().setLedgerLinks(
                    id = id,
                    reversesId = legacy.reversesEntryId?.let { vendorLedgerIds[it] },
                    referenceId = FinanceRestoreLinks.vendorReference(
                        legacy.referenceType,
                        legacy.referenceId,
                        invoiceIds,
                        paymentIds
                    )
                )
            }
            FinanceBackupCodec.preferredLinks(root).forEach { link ->
                val itemId = item(link.itemLegacyId) ?: return@forEach
                val vendorId = vendor(link.vendorLegacyId) ?: return@forEach
                database.itemDao().setPreferredVendor(itemId, vendorId)
            }
        }

        suspend fun importLedger(entries: List<CustomerLedgerEntry>) {
            entries.forEach { legacy ->
                if (!importedLedgerLegacy.add(legacy.id)) return@forEach
                val customerId = customer(legacy.customerId) ?: return@forEach
                try {
                    val id = database.customerLedgerDao().insert(
                        legacy.copy(
                            id = 0,
                            customerId = customerId,
                            billId = bill(legacy.billId),
                            reversesEntryId = null,
                            referenceId = null
                        )
                    )
                    customerLedgerIds[legacy.id] = id
                    pendingCustomerLedger += legacy
                    ledgerImported++
                } catch (e: Exception) {
                    errors.add("Customer ledger: ${e.message}")
                }
            }
        }

        if (root.has("customerLedger")) importLedger(FinanceBackupCodec.customerLedger(root))
        if (root.has("salesFinanceLedger")) importLedger(FinanceBackupCodec.customerLedger(root, "salesFinanceLedger"))

        if (root.has("customerReceipts")) {
            FinanceBackupCodec.customerReceipts(root).forEach { legacy ->
                val customerId = customer(legacy.customerId) ?: return@forEach
                val id = database.customerLedgerDao().insertReceipt(legacy.copy(id = 0, customerId = customerId))
                receiptCustomerIds[legacy.id] = id
            }
            FinanceBackupCodec.customerAllocations(root).forEach { legacy ->
                val receiptId = receiptCustomerIds[legacy.receiptId] ?: return@forEach
                val billId = bill(legacy.billId) ?: return@forEach
                database.customerLedgerDao().insertAllocation(
                    CustomerReceiptAllocation(receiptId = receiptId, billId = billId, amountPaise = legacy.amountPaise)
                )
            }
        }

        if (root.has("salesReturns")) {
            FinanceBackupCodec.salesReturns(root).forEach { legacy ->
                val customerId = customer(legacy.customerId) ?: return@forEach
                val id = database.customerLedgerDao().insertReturn(
                    legacy.copy(id = 0, customerId = customerId, billId = bill(legacy.billId))
                )
                returnIds[legacy.id] = id
            }
            FinanceBackupCodec.salesReturnLines(root).forEach { legacy ->
                val returnId = returnIds[legacy.returnId] ?: return@forEach
                database.customerLedgerDao().insertReturnLine(
                    legacy.copy(id = 0, returnId = returnId, itemId = item(legacy.itemId))
                )
                val itemId = item(legacy.itemId)
                if (applyStockEffects && itemId != null && legacy.quantity > 0) {
                    applyStock(itemId, legacy.quantity, com.kex.vikrsaathi.data.entity.StockMovementType.SALES_RETURN, "SALES_RETURN", returnId)
                }
            }
        }

        pendingCustomerLedger.forEach { legacy ->
            val id = customerLedgerIds[legacy.id] ?: return@forEach
            database.customerLedgerDao().setLinks(
                id = id,
                reversesId = legacy.reversesEntryId?.let { customerLedgerIds[it] },
                referenceId = FinanceRestoreLinks.customerReference(
                    legacy.referenceType,
                    legacy.referenceId,
                    billIds,
                    presentBillIds,
                    receiptCustomerIds,
                    returnIds
                )
            )
        }

        if (root.has("billPayments")) {
            val grouped = FinanceBackupCodec.billPayments(root).groupBy { it.billLegacyId }
            grouped.forEach { (legacyBillId, portions) ->
                if (legacyBillId in billsAlreadyPaid) return@forEach
                val billId = bill(legacyBillId) ?: return@forEach
                database.billPaymentDao().deleteForBill(billId)
                portions.forEach { portion ->
                    database.billPaymentDao().insert(
                        BillPayment(billId = billId, method = portion.method, amountPaise = portion.amountPaise)
                    )
                }
                val label = PaymentSplitValidator.labelFor(
                    portions.map { com.kex.vikrsaathi.domain.finance.PaymentPortion(it.method, it.amountPaise) }
                )
                database.billDao().updatePaymentStatus(
                    billId,
                    if (portions.isEmpty()) BillPaymentStatus.LEGACY else BillPaymentStatus.RECORDED,
                    label
                )
            }
        }

        return Result(vendorsImported, ledgerImported, errors)
    }

    private suspend fun restorePurchase(
        root: JSONObject,
        vendorIds: MutableMap<Long, Long>,
        itemIds: Map<Long, Long>,
        presentItemIds: Set<Long>,
        presentVendorIds: Set<Long>,
        quoteIds: MutableMap<Long, Long>,
        orderIds: MutableMap<Long, Long>,
        orderLineIds: MutableMap<Long, Long>,
        receiptIds: MutableMap<Long, Long>,
        invoiceIds: MutableMap<Long, Long>,
        errors: MutableList<String>,
        applyStockEffects: Boolean
    ) {
        fun vendor(legacy: Long?) = IdRemapper.resolve(legacy, vendorIds, presentVendorIds)
        fun item(legacy: Long?) = IdRemapper.resolve(legacy, itemIds, presentItemIds)
        FinanceBackupCodec.vendorQuotes(root).forEach { legacy ->
            val vendorId = vendor(legacy.vendorId) ?: return@forEach
            val id = database.purchaseDao().insertQuote(legacy.copy(id = 0, vendorId = vendorId))
            quoteIds[legacy.id] = id
        }
        FinanceBackupCodec.vendorQuoteLines(root).forEach { legacy ->
            val quoteId = quoteIds[legacy.quoteId] ?: return@forEach
            database.purchaseDao().insertQuoteLine(legacy.copy(id = 0, quoteId = quoteId, itemId = item(legacy.itemId)))
        }
        FinanceBackupCodec.purchaseOrders(root).forEach { legacy ->
            val vendorId = vendor(legacy.vendorId) ?: return@forEach
            val id = database.purchaseDao().insertOrder(
                legacy.copy(id = 0, vendorId = vendorId, quoteId = legacy.quoteId?.let { quoteIds[it] })
            )
            orderIds[legacy.id] = id
        }
        FinanceBackupCodec.purchaseOrderLines(root).forEach { legacy ->
            val orderId = orderIds[legacy.orderId] ?: return@forEach
            val id = database.purchaseDao().insertOrderLine(
                legacy.copy(id = 0, orderId = orderId, itemId = item(legacy.itemId))
            )
            orderLineIds[legacy.id] = id
        }
        FinanceBackupCodec.goodsReceipts(root).forEach { legacy ->
            val vendorId = vendor(legacy.vendorId) ?: return@forEach
            try {
                val id = database.purchaseDao().insertReceipt(
                    legacy.copy(id = 0, vendorId = vendorId, orderId = legacy.orderId?.let { orderIds[it] })
                )
                receiptIds[legacy.id] = id
            } catch (e: Exception) {
                errors.add("Goods receipt ${legacy.receiptNumber}: ${e.message}")
            }
        }
        FinanceBackupCodec.goodsReceiptLines(root).forEach { legacy ->
            val receiptId = receiptIds[legacy.receiptId] ?: return@forEach
            database.purchaseDao().insertReceiptLine(
                legacy.copy(
                    id = 0,
                    receiptId = receiptId,
                    itemId = item(legacy.itemId),
                    orderLineId = legacy.orderLineId?.let { orderLineIds[it] }
                )
            )
            val itemId = item(legacy.itemId)
            if (applyStockEffects && itemId != null && legacy.acceptedQty > 0) {
                applyStock(
                    itemId,
                    legacy.acceptedQty,
                    com.kex.vikrsaathi.data.entity.StockMovementType.PURCHASE_RECEIPT,
                    com.kex.vikrsaathi.data.entity.StockReferenceType.GOODS_RECEIPT,
                    receiptId
                )
            }
        }
        FinanceBackupCodec.purchaseInvoices(root).forEach { legacy ->
            val vendorId = vendor(legacy.vendorId) ?: return@forEach
            val id = database.purchaseDao().insertInvoice(
                legacy.copy(
                    id = 0,
                    vendorId = vendorId,
                    orderId = legacy.orderId?.let { orderIds[it] },
                    receiptId = legacy.receiptId?.let { receiptIds[it] }
                )
            )
            invoiceIds[legacy.id] = id
        }
        FinanceBackupCodec.purchaseInvoiceLines(root).forEach { legacy ->
            val invoiceId = invoiceIds[legacy.invoiceId] ?: return@forEach
            database.purchaseDao().insertInvoiceLine(
                legacy.copy(id = 0, invoiceId = invoiceId, itemId = item(legacy.itemId))
            )
        }
    }

    private suspend fun applyStock(
        itemId: Long,
        delta: Int,
        type: com.kex.vikrsaathi.data.entity.StockMovementType,
        referenceType: String,
        referenceId: Long
    ) {
        val item = database.itemDao().getItemById(itemId) ?: return
        val quantityAfter = item.stockQty + delta
        database.itemDao().updateStockQty(itemId, quantityAfter)
        database.stockMovementDao().insert(
            com.kex.vikrsaathi.data.entity.StockMovement(
                itemId = itemId,
                delta = delta,
                quantityAfter = quantityAfter,
                type = type.name,
                referenceType = referenceType,
                referenceId = referenceId,
                note = "Restored from backup"
            )
        )
    }
}
