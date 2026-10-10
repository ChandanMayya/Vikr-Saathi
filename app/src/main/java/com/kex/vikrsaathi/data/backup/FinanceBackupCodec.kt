package com.kex.vikrsaathi.data.backup

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
import com.kex.vikrsaathi.domain.finance.FinanceReference
import com.kex.vikrsaathi.domain.finance.FinanceResetPolicy
import com.kex.vikrsaathi.domain.finance.LedgerRow
import org.json.JSONArray
import org.json.JSONObject

data class FinanceSnapshot(
    val customerLedger: List<CustomerLedgerEntry> = emptyList(),
    val receipts: List<CustomerReceipt> = emptyList(),
    val allocations: List<CustomerReceiptAllocation> = emptyList(),
    val returns: List<SalesReturn> = emptyList(),
    val returnLines: List<SalesReturnLine> = emptyList(),
    val billPayments: List<BillPaymentBackup> = emptyList(),
    val vendors: List<Vendor> = emptyList(),
    val vendorLedger: List<VendorLedgerEntry> = emptyList(),
    val vendorPayments: List<VendorPayment> = emptyList(),
    val vendorAllocations: List<VendorPaymentAllocation> = emptyList(),
    val orders: List<PurchaseOrder> = emptyList(),
    val orderLines: List<PurchaseOrderLine> = emptyList(),
    val goodsReceipts: List<GoodsReceipt> = emptyList(),
    val goodsReceiptLines: List<GoodsReceiptLine> = emptyList(),
    val invoices: List<PurchaseInvoice> = emptyList(),
    val invoiceLines: List<PurchaseInvoiceLine> = emptyList(),
    val quotes: List<VendorQuote> = emptyList(),
    val quoteLines: List<VendorQuoteLine> = emptyList(),
    val preferredVendorLinks: List<PreferredVendorLink> = emptyList()
)

data class BillPaymentBackup(
    val billLegacyId: Long,
    val method: String,
    val amountPaise: Long
)

data class PreferredVendorLink(
    val itemLegacyId: Long,
    val vendorLegacyId: Long
)

object FinanceBackupCodec {

    fun attach(root: JSONObject, options: BackupExportOptions, snapshot: FinanceSnapshot) {
        val includes = root.optJSONArray("includes") ?: JSONArray()
        val includeNames = buildSet {
            for (i in 0 until includes.length()) add(includes.getString(i))
        }.toMutableSet()

        val fullCustomerFinance = options.includeCustomers || options.includeCustomerBalances
        val hasCustomerFinance = snapshot.customerLedger.isNotEmpty() ||
            snapshot.receipts.isNotEmpty() ||
            snapshot.returns.isNotEmpty()
        if (fullCustomerFinance && hasCustomerFinance) {
            root.put("customerLedger", entries(snapshot.customerLedger))
            root.put("customerReceipts", receipts(snapshot.receipts))
            root.put("customerReceiptAllocations", allocations(snapshot.allocations))
            root.put("salesReturns", returns(snapshot.returns))
            root.put("salesReturnLines", returnLines(snapshot.returnLines))
            includeNames += "customer_balances"
        } else if (options.includeSales) {
            val linked = snapshot.customerLedger.filter { entry ->
                FinanceResetPolicy.isBillLinkedCustomerEntry(entry.toPolicyRow())
            }
            if (linked.isNotEmpty() || snapshot.returns.isNotEmpty()) {
                root.put("salesFinanceLedger", entries(linked))
                root.put("salesReturns", returns(snapshot.returns))
                root.put("salesReturnLines", returnLines(snapshot.returnLines))
                includeNames += "sales_finance"
            }
        }

        if (options.includeCustomerBalances && !options.includeSales && snapshot.billPayments.isNotEmpty()) {
                root.put("billPayments", billPayments(snapshot.billPayments))
            includeNames += "bill_payments"
        }

        val hasVendorData = snapshot.vendors.isNotEmpty() ||
            snapshot.orders.isNotEmpty() ||
            snapshot.goodsReceipts.isNotEmpty() ||
            snapshot.invoices.isNotEmpty() ||
            snapshot.quotes.isNotEmpty() ||
            snapshot.vendorLedger.isNotEmpty()
        if (options.includeVendors && hasVendorData) {
            root.put("vendors", vendors(snapshot.vendors))
            root.put("vendorLedger", vendorEntries(snapshot.vendorLedger))
            root.put("vendorPayments", vendorPayments(snapshot.vendorPayments))
            root.put("vendorPaymentAllocations", vendorAllocations(snapshot.vendorAllocations))
            root.put("purchaseOrders", orders(snapshot.orders))
            root.put("purchaseOrderLines", orderLines(snapshot.orderLines))
            root.put("goodsReceipts", receiptsGoods(snapshot.goodsReceipts))
            root.put("goodsReceiptLines", receiptLines(snapshot.goodsReceiptLines))
            root.put("purchaseInvoices", invoices(snapshot.invoices))
            root.put("purchaseInvoiceLines", invoiceLines(snapshot.invoiceLines))
            root.put("vendorQuotes", quotes(snapshot.quotes))
            root.put("vendorQuoteLines", quoteLines(snapshot.quoteLines))
            root.put("preferredVendorLinks", links(snapshot.preferredVendorLinks))
            includeNames += "vendors"
        }

        root.put("includes", JSONArray(includeNames.toList()))
    }

    fun customerLedger(root: JSONObject, key: String = "customerLedger"): List<CustomerLedgerEntry> =
        array(root, key).map { decodeEntry(it) }

    fun customerReceipts(root: JSONObject) = array(root, "customerReceipts").map { obj ->
        CustomerReceipt(
            id = obj.getLong("legacyId"),
            customerId = obj.getLong("customerLegacyId"),
            amountPaise = obj.getLong("amountPaise"),
            occurredAt = obj.getLong("occurredAt"),
            method = obj.getString("method"),
            referenceNote = obj.optString("referenceNote").ifBlank { null },
            createdAt = obj.optLong("createdAt")
        )
    }

    fun customerAllocations(root: JSONObject) = array(root, "customerReceiptAllocations").map { obj ->
        CustomerReceiptAllocation(
            id = obj.getLong("legacyId"),
            receiptId = obj.getLong("receiptLegacyId"),
            billId = obj.getLong("billLegacyId"),
            amountPaise = obj.getLong("amountPaise")
        )
    }

    fun salesReturns(root: JSONObject) = array(root, "salesReturns").map { obj ->
        SalesReturn(
            id = obj.getLong("legacyId"),
            customerId = obj.getLong("customerLegacyId"),
            billId = obj.optLong("billLegacyId").takeIf { obj.has("billLegacyId") && !obj.isNull("billLegacyId") },
            reason = obj.optString("reason"),
            moneyRefundedPaise = obj.optLong("moneyRefundedPaise"),
            creditAdjustmentPaise = obj.optLong("creditAdjustmentPaise"),
            occurredAt = obj.optLong("occurredAt"),
            createdAt = obj.optLong("createdAt")
        )
    }

    fun salesReturnLines(root: JSONObject) = array(root, "salesReturnLines").map { obj ->
        SalesReturnLine(
            id = obj.getLong("legacyId"),
            returnId = obj.getLong("returnLegacyId"),
            itemId = obj.optLong("itemLegacyId").takeIf { obj.has("itemLegacyId") && !obj.isNull("itemLegacyId") },
            itemName = obj.optString("itemName"),
            quantity = obj.optInt("quantity")
        )
    }

    fun billPayments(root: JSONObject) = array(root, "billPayments").map { obj ->
        BillPaymentBackup(
            billLegacyId = obj.getLong("billLegacyId"),
            method = obj.getString("method"),
            amountPaise = obj.getLong("amountPaise")
        )
    }

    fun vendors(root: JSONObject) = array(root, "vendors").map { obj ->
        Vendor(
            id = obj.getLong("legacyId"),
            name = obj.getString("name"),
            phone = obj.optString("phone"),
            address = obj.optString("address"),
            email = obj.optString("email"),
            notes = obj.optString("notes"),
            archived = obj.optBoolean("archived"),
            createdAt = obj.optLong("createdAt")
        )
    }

    fun vendorLedger(root: JSONObject) = array(root, "vendorLedger").map { decodeVendorEntry(it) }
    fun vendorPayments(root: JSONObject) = array(root, "vendorPayments").map { obj ->
        VendorPayment(
            id = obj.getLong("legacyId"),
            vendorId = obj.getLong("vendorLegacyId"),
            amountPaise = obj.getLong("amountPaise"),
            occurredAt = obj.getLong("occurredAt"),
            method = obj.getString("method"),
            referenceNote = obj.optString("referenceNote").ifBlank { null },
            createdAt = obj.optLong("createdAt")
        )
    }

    fun vendorAllocations(root: JSONObject) = array(root, "vendorPaymentAllocations").map { obj ->
        VendorPaymentAllocation(
            id = obj.getLong("legacyId"),
            paymentId = obj.getLong("paymentLegacyId"),
            invoiceId = obj.getLong("invoiceLegacyId"),
            amountPaise = obj.getLong("amountPaise")
        )
    }

    fun purchaseOrders(root: JSONObject) = array(root, "purchaseOrders").map { obj ->
        PurchaseOrder(
            id = obj.getLong("legacyId"),
            vendorId = obj.getLong("vendorLegacyId"),
            orderNumber = obj.optString("orderNumber"),
            quoteId = optionalLong(obj, "quoteLegacyId"),
            expectedDelivery = optionalLong(obj, "expectedDelivery"),
            notes = obj.optString("notes"),
            status = obj.optString("status", "ORDERED"),
            createdAt = obj.optLong("createdAt")
        )
    }

    fun purchaseOrderLines(root: JSONObject) = array(root, "purchaseOrderLines").map { obj ->
        PurchaseOrderLine(
            id = obj.getLong("legacyId"),
            orderId = obj.getLong("orderLegacyId"),
            itemId = optionalLong(obj, "itemLegacyId"),
            itemName = obj.optString("itemName"),
            quantity = obj.optInt("quantity"),
            ratePaise = obj.optLong("ratePaise")
        )
    }

    fun goodsReceipts(root: JSONObject) = array(root, "goodsReceipts").map { obj ->
        GoodsReceipt(
            id = obj.getLong("legacyId"),
            vendorId = obj.getLong("vendorLegacyId"),
            orderId = optionalLong(obj, "orderLegacyId"),
            receiptNumber = obj.optString("receiptNumber"),
            receivedAt = obj.optLong("receivedAt"),
            notes = obj.optString("notes"),
            directReceipt = obj.optBoolean("directReceipt")
        )
    }

    fun goodsReceiptLines(root: JSONObject) = array(root, "goodsReceiptLines").map { obj ->
        GoodsReceiptLine(
            id = obj.getLong("legacyId"),
            receiptId = obj.getLong("receiptLegacyId"),
            orderLineId = optionalLong(obj, "orderLineLegacyId"),
            itemId = optionalLong(obj, "itemLegacyId"),
            itemName = obj.optString("itemName"),
            orderedQty = obj.optInt("orderedQty"),
            receivedQty = obj.optInt("receivedQty"),
            shortQty = obj.optInt("shortQty"),
            damagedQty = obj.optInt("damagedQty"),
            rejectedQty = obj.optInt("rejectedQty"),
            acceptedQty = obj.optInt("acceptedQty")
        )
    }

    fun purchaseInvoices(root: JSONObject) = array(root, "purchaseInvoices").map { obj ->
        PurchaseInvoice(
            id = obj.getLong("legacyId"),
            vendorId = obj.getLong("vendorLegacyId"),
            invoiceNumber = obj.optString("invoiceNumber"),
            invoiceDate = obj.optLong("invoiceDate"),
            orderId = optionalLong(obj, "orderLegacyId"),
            receiptId = optionalLong(obj, "receiptLegacyId"),
            deliveryChargesPaise = obj.optLong("deliveryChargesPaise"),
            otherChargesPaise = obj.optLong("otherChargesPaise"),
            discountPaise = obj.optLong("discountPaise"),
            totalPaise = obj.optLong("totalPaise"),
            notes = obj.optString("notes")
        )
    }

    fun purchaseInvoiceLines(root: JSONObject) = array(root, "purchaseInvoiceLines").map { obj ->
        PurchaseInvoiceLine(
            id = obj.getLong("legacyId"),
            invoiceId = obj.getLong("invoiceLegacyId"),
            itemId = optionalLong(obj, "itemLegacyId"),
            itemName = obj.optString("itemName"),
            quantity = obj.optInt("quantity"),
            ratePaise = obj.optLong("ratePaise"),
            lineTotalPaise = obj.optLong("lineTotalPaise")
        )
    }

    fun vendorQuotes(root: JSONObject) = array(root, "vendorQuotes").map { obj ->
        VendorQuote(
            id = obj.getLong("legacyId"),
            vendorId = obj.getLong("vendorLegacyId"),
            quoteDate = obj.optLong("quoteDate"),
            validUntil = optionalLong(obj, "validUntil"),
            deliveryChargesPaise = obj.optLong("deliveryChargesPaise"),
            discountPaise = obj.optLong("discountPaise"),
            expectedDelivery = optionalLong(obj, "expectedDelivery"),
            notes = obj.optString("notes")
        )
    }

    fun vendorQuoteLines(root: JSONObject) = array(root, "vendorQuoteLines").map { obj ->
        VendorQuoteLine(
            id = obj.getLong("legacyId"),
            quoteId = obj.getLong("quoteLegacyId"),
            itemId = optionalLong(obj, "itemLegacyId"),
            itemName = obj.optString("itemName"),
            ratePaise = obj.optLong("ratePaise"),
            availableQty = obj.optInt("availableQty"),
            notes = obj.optString("notes")
        )
    }

    fun preferredLinks(root: JSONObject) = array(root, "preferredVendorLinks").map { obj ->
        PreferredVendorLink(obj.getLong("itemLegacyId"), obj.getLong("vendorLegacyId"))
    }

    private fun entries(list: List<CustomerLedgerEntry>) = JSONArray().apply {
        list.forEach { entry ->
            put(JSONObject().apply {
                put("legacyId", entry.id)
                put("customerLegacyId", entry.customerId)
                put("entryType", entry.entryType)
                put("amountPaise", entry.amountPaise)
                put("occurredAt", entry.occurredAt)
                put("createdAt", entry.createdAt)
                entry.method?.let { put("method", it) }
                entry.note?.let { put("note", it) }
                entry.reason?.let { put("reason", it) }
                entry.referenceType?.let { put("referenceType", it) }
                entry.referenceId?.let { put("referenceId", it) }
                entry.reversesEntryId?.let { put("reversesEntryId", it) }
                entry.billId?.let { put("billLegacyId", it) }
            })
        }
    }

    private fun decodeEntry(obj: JSONObject) = CustomerLedgerEntry(
        id = obj.getLong("legacyId"),
        customerId = obj.getLong("customerLegacyId"),
        entryType = obj.getString("entryType"),
        amountPaise = obj.getLong("amountPaise"),
        occurredAt = obj.getLong("occurredAt"),
        createdAt = obj.optLong("createdAt"),
        method = obj.optString("method").ifBlank { null },
        note = obj.optString("note").ifBlank { null },
        reason = obj.optString("reason").ifBlank { null },
        referenceType = obj.optString("referenceType").ifBlank { null },
        referenceId = optionalLong(obj, "referenceId"),
        reversesEntryId = optionalLong(obj, "reversesEntryId"),
        billId = optionalLong(obj, "billLegacyId")
    )

    private fun receipts(list: List<CustomerReceipt>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("customerLegacyId", it.customerId)
                put("amountPaise", it.amountPaise)
                put("occurredAt", it.occurredAt)
                put("method", it.method)
                it.referenceNote?.let { note -> put("referenceNote", note) }
                put("createdAt", it.createdAt)
            })
        }
    }

    private fun allocations(list: List<CustomerReceiptAllocation>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("receiptLegacyId", it.receiptId)
                put("billLegacyId", it.billId)
                put("amountPaise", it.amountPaise)
            })
        }
    }

    private fun returns(list: List<SalesReturn>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("customerLegacyId", it.customerId)
                it.billId?.let { id -> put("billLegacyId", id) }
                put("reason", it.reason)
                put("moneyRefundedPaise", it.moneyRefundedPaise)
                put("creditAdjustmentPaise", it.creditAdjustmentPaise)
                put("occurredAt", it.occurredAt)
                put("createdAt", it.createdAt)
            })
        }
    }

    private fun returnLines(list: List<SalesReturnLine>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("returnLegacyId", it.returnId)
                it.itemId?.let { id -> put("itemLegacyId", id) }
                put("itemName", it.itemName)
                put("quantity", it.quantity)
            })
        }
    }

    private fun billPayments(list: List<BillPaymentBackup>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("billLegacyId", it.billLegacyId)
                put("method", it.method)
                put("amountPaise", it.amountPaise)
            })
        }
    }

    private fun vendors(list: List<Vendor>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("name", it.name)
                put("phone", it.phone)
                put("address", it.address)
                put("email", it.email)
                put("notes", it.notes)
                put("archived", it.archived)
                put("createdAt", it.createdAt)
            })
        }
    }

    private fun vendorEntries(list: List<VendorLedgerEntry>) = JSONArray().apply {
        list.forEach { entry ->
            put(JSONObject().apply {
                put("legacyId", entry.id)
                put("vendorLegacyId", entry.vendorId)
                put("entryType", entry.entryType)
                put("amountPaise", entry.amountPaise)
                put("occurredAt", entry.occurredAt)
                put("createdAt", entry.createdAt)
                entry.method?.let { put("method", it) }
                entry.note?.let { put("note", it) }
                entry.reason?.let { put("reason", it) }
                entry.referenceType?.let { put("referenceType", it) }
                entry.referenceId?.let { put("referenceId", it) }
                entry.reversesEntryId?.let { put("reversesEntryId", it) }
                entry.invoiceId?.let { put("invoiceLegacyId", it) }
            })
        }
    }

    private fun decodeVendorEntry(obj: JSONObject) = VendorLedgerEntry(
        id = obj.getLong("legacyId"),
        vendorId = obj.getLong("vendorLegacyId"),
        entryType = obj.getString("entryType"),
        amountPaise = obj.getLong("amountPaise"),
        occurredAt = obj.getLong("occurredAt"),
        createdAt = obj.optLong("createdAt"),
        method = obj.optString("method").ifBlank { null },
        note = obj.optString("note").ifBlank { null },
        reason = obj.optString("reason").ifBlank { null },
        referenceType = obj.optString("referenceType").ifBlank { null },
        referenceId = optionalLong(obj, "referenceId"),
        reversesEntryId = optionalLong(obj, "reversesEntryId"),
        invoiceId = optionalLong(obj, "invoiceLegacyId")
    )

    private fun vendorPayments(list: List<VendorPayment>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("vendorLegacyId", it.vendorId)
                put("amountPaise", it.amountPaise)
                put("occurredAt", it.occurredAt)
                put("method", it.method)
                it.referenceNote?.let { note -> put("referenceNote", note) }
                put("createdAt", it.createdAt)
            })
        }
    }

    private fun vendorAllocations(list: List<VendorPaymentAllocation>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("paymentLegacyId", it.paymentId)
                put("invoiceLegacyId", it.invoiceId)
                put("amountPaise", it.amountPaise)
            })
        }
    }

    private fun orders(list: List<PurchaseOrder>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("vendorLegacyId", it.vendorId)
                put("orderNumber", it.orderNumber)
                it.quoteId?.let { id -> put("quoteLegacyId", id) }
                it.expectedDelivery?.let { date -> put("expectedDelivery", date) }
                put("notes", it.notes)
                put("status", it.status)
                put("createdAt", it.createdAt)
            })
        }
    }

    private fun orderLines(list: List<PurchaseOrderLine>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("orderLegacyId", it.orderId)
                it.itemId?.let { id -> put("itemLegacyId", id) }
                put("itemName", it.itemName)
                put("quantity", it.quantity)
                put("ratePaise", it.ratePaise)
            })
        }
    }

    private fun receiptsGoods(list: List<GoodsReceipt>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("vendorLegacyId", it.vendorId)
                it.orderId?.let { id -> put("orderLegacyId", id) }
                put("receiptNumber", it.receiptNumber)
                put("receivedAt", it.receivedAt)
                put("notes", it.notes)
                put("directReceipt", it.directReceipt)
            })
        }
    }

    private fun receiptLines(list: List<GoodsReceiptLine>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("receiptLegacyId", it.receiptId)
                it.orderLineId?.let { id -> put("orderLineLegacyId", id) }
                it.itemId?.let { id -> put("itemLegacyId", id) }
                put("itemName", it.itemName)
                put("orderedQty", it.orderedQty)
                put("receivedQty", it.receivedQty)
                put("shortQty", it.shortQty)
                put("damagedQty", it.damagedQty)
                put("rejectedQty", it.rejectedQty)
                put("acceptedQty", it.acceptedQty)
            })
        }
    }

    private fun invoices(list: List<PurchaseInvoice>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("vendorLegacyId", it.vendorId)
                put("invoiceNumber", it.invoiceNumber)
                put("invoiceDate", it.invoiceDate)
                it.orderId?.let { id -> put("orderLegacyId", id) }
                it.receiptId?.let { id -> put("receiptLegacyId", id) }
                put("deliveryChargesPaise", it.deliveryChargesPaise)
                put("otherChargesPaise", it.otherChargesPaise)
                put("discountPaise", it.discountPaise)
                put("totalPaise", it.totalPaise)
                put("notes", it.notes)
            })
        }
    }

    private fun invoiceLines(list: List<PurchaseInvoiceLine>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("invoiceLegacyId", it.invoiceId)
                it.itemId?.let { id -> put("itemLegacyId", id) }
                put("itemName", it.itemName)
                put("quantity", it.quantity)
                put("ratePaise", it.ratePaise)
                put("lineTotalPaise", it.lineTotalPaise)
            })
        }
    }

    private fun quotes(list: List<VendorQuote>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("vendorLegacyId", it.vendorId)
                put("quoteDate", it.quoteDate)
                it.validUntil?.let { date -> put("validUntil", date) }
                put("deliveryChargesPaise", it.deliveryChargesPaise)
                put("discountPaise", it.discountPaise)
                it.expectedDelivery?.let { date -> put("expectedDelivery", date) }
                put("notes", it.notes)
            })
        }
    }

    private fun quoteLines(list: List<VendorQuoteLine>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("legacyId", it.id)
                put("quoteLegacyId", it.quoteId)
                it.itemId?.let { id -> put("itemLegacyId", id) }
                put("itemName", it.itemName)
                put("ratePaise", it.ratePaise)
                put("availableQty", it.availableQty)
                put("notes", it.notes)
            })
        }
    }

    private fun links(list: List<PreferredVendorLink>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().apply {
                put("itemLegacyId", it.itemLegacyId)
                put("vendorLegacyId", it.vendorLegacyId)
            })
        }
    }

    private fun array(root: JSONObject, key: String): List<JSONObject> {
        val array = root.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) add(array.getJSONObject(i))
        }
    }

    private fun optionalLong(obj: JSONObject, key: String): Long? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.getLong(key)
    }
}

private fun CustomerLedgerEntry.toPolicyRow() = LedgerRow(
    id = id,
    partyId = customerId,
    entryType = entryType,
    amountPaise = amountPaise,
    occurredAt = occurredAt,
    reversesEntryId = reversesEntryId,
    billId = billId,
    referenceType = referenceType ?: if (billId != null) FinanceReference.BILL else null,
    referenceId = referenceId
)
