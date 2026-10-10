package com.kex.vikrsaathi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

@Dao
interface BillPaymentDao {
    @Insert
    suspend fun insert(payment: BillPayment): Long

    @Query("SELECT * FROM bill_payments WHERE billId = :billId")
    suspend fun getForBill(billId: Long): List<BillPayment>

    @Query("SELECT * FROM bill_payments")
    suspend fun getAll(): List<BillPayment>

    @Query("DELETE FROM bill_payments WHERE billId = :billId")
    suspend fun deleteForBill(billId: Long)

    @Query("DELETE FROM bill_payments")
    suspend fun deleteAll()
}

@Dao
interface CustomerLedgerDao {
    @Insert
    suspend fun insert(entry: CustomerLedgerEntry): Long

    @Query("SELECT * FROM customer_ledger_entries WHERE customerId = :customerId ORDER BY occurredAt ASC, id ASC")
    suspend fun getForCustomer(customerId: Long): List<CustomerLedgerEntry>

    @Query("SELECT * FROM customer_ledger_entries")
    suspend fun getAll(): List<CustomerLedgerEntry>

    @Query("SELECT * FROM customer_ledger_entries WHERE id = :id")
    suspend fun getById(id: Long): CustomerLedgerEntry?

    @Query("SELECT * FROM customer_ledger_entries WHERE billId = :billId")
    suspend fun getForBill(billId: Long): List<CustomerLedgerEntry>

    @Query("SELECT COUNT(*) FROM customer_ledger_entries WHERE customerId = :customerId")
    suspend fun countForCustomer(customerId: Long): Int

    @Query("DELETE FROM customer_ledger_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM customer_ledger_entries")
    suspend fun deleteAll()

    @Query("UPDATE customer_ledger_entries SET reversesEntryId = :reversesId, referenceId = :referenceId WHERE id = :id")
    suspend fun setLinks(id: Long, reversesId: Long?, referenceId: Long?)

    @Insert
    suspend fun insertReceipt(receipt: CustomerReceipt): Long

    @Query("SELECT * FROM customer_receipts WHERE customerId = :customerId ORDER BY occurredAt ASC")
    suspend fun getReceipts(customerId: Long): List<CustomerReceipt>

    @Query("SELECT * FROM customer_receipts")
    suspend fun getAllReceipts(): List<CustomerReceipt>

    @Insert
    suspend fun insertAllocation(allocation: CustomerReceiptAllocation): Long

    @Query("SELECT * FROM customer_receipt_allocations")
    suspend fun getAllAllocations(): List<CustomerReceiptAllocation>

    @Query("SELECT * FROM customer_receipt_allocations WHERE receiptId = :receiptId")
    suspend fun getAllocationsForReceipt(receiptId: Long): List<CustomerReceiptAllocation>

    @Query("DELETE FROM customer_receipt_allocations WHERE billId = :billId")
    suspend fun deleteAllocationsForBill(billId: Long)

    @Query("DELETE FROM customer_receipt_allocations")
    suspend fun deleteAllAllocations()

    @Query("DELETE FROM customer_receipts")
    suspend fun deleteAllReceipts()

    @Insert
    suspend fun insertReturn(salesReturn: SalesReturn): Long

    @Insert
    suspend fun insertReturnLine(line: SalesReturnLine): Long

    @Query("SELECT * FROM sales_returns")
    suspend fun getAllReturns(): List<SalesReturn>

    @Query("SELECT * FROM sales_returns WHERE billId = :billId")
    suspend fun getReturnsForBill(billId: Long): List<SalesReturn>

    @Query("SELECT * FROM sales_return_lines")
    suspend fun getAllReturnLines(): List<SalesReturnLine>

    @Query("SELECT * FROM sales_return_lines WHERE returnId = :returnId")
    suspend fun getLinesForReturn(returnId: Long): List<SalesReturnLine>

    @Query("DELETE FROM sales_return_lines")
    suspend fun deleteAllReturnLines()

    @Query("DELETE FROM sales_returns")
    suspend fun deleteAllReturns()
}

@Dao
interface VendorDao {
    @Insert
    suspend fun insert(vendor: Vendor): Long

    @androidx.room.Update
    suspend fun update(vendor: Vendor)

    @androidx.room.Delete
    suspend fun delete(vendor: Vendor)

    @Query("SELECT * FROM vendors ORDER BY name ASC")
    suspend fun getAll(): List<Vendor>

    @Query("SELECT * FROM vendors WHERE archived = 0 ORDER BY name ASC")
    suspend fun getActive(): List<Vendor>

    @Query("SELECT * FROM vendors WHERE id = :id")
    suspend fun getById(id: Long): Vendor?

    @Query("SELECT * FROM vendors WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun search(query: String): List<Vendor>

    @Query("DELETE FROM vendors")
    suspend fun deleteAll()

    @Insert
    suspend fun insertLedger(entry: VendorLedgerEntry): Long

    @Query("SELECT * FROM vendor_ledger_entries WHERE vendorId = :vendorId ORDER BY occurredAt ASC, id ASC")
    suspend fun getLedger(vendorId: Long): List<VendorLedgerEntry>

    @Query("SELECT * FROM vendor_ledger_entries")
    suspend fun getAllLedger(): List<VendorLedgerEntry>

    @Query("SELECT * FROM vendor_ledger_entries WHERE id = :id")
    suspend fun getLedgerById(id: Long): VendorLedgerEntry?

    @Query("SELECT * FROM vendor_ledger_entries WHERE invoiceId = :invoiceId")
    suspend fun getLedgerForInvoice(invoiceId: Long): List<VendorLedgerEntry>

    @Query("SELECT COUNT(*) FROM vendor_ledger_entries WHERE vendorId = :vendorId")
    suspend fun countLedger(vendorId: Long): Int

    @Query("DELETE FROM vendor_ledger_entries WHERE id IN (:ids)")
    suspend fun deleteLedgerByIds(ids: List<Long>)

    @Query("DELETE FROM vendor_ledger_entries")
    suspend fun deleteAllLedger()

    @Query("UPDATE vendor_ledger_entries SET reversesEntryId = :reversesId, referenceId = :referenceId WHERE id = :id")
    suspend fun setLedgerLinks(id: Long, reversesId: Long?, referenceId: Long?)

    @Insert
    suspend fun insertPayment(payment: VendorPayment): Long

    @Query("SELECT * FROM vendor_payments")
    suspend fun getAllPayments(): List<VendorPayment>

    @Query("SELECT * FROM vendor_payments WHERE vendorId = :vendorId")
    suspend fun getPayments(vendorId: Long): List<VendorPayment>

    @Insert
    suspend fun insertPaymentAllocation(allocation: VendorPaymentAllocation): Long

    @Query("SELECT * FROM vendor_payment_allocations")
    suspend fun getAllPaymentAllocations(): List<VendorPaymentAllocation>

    @Query("SELECT * FROM vendor_payment_allocations WHERE paymentId = :paymentId")
    suspend fun getAllocationsForPayment(paymentId: Long): List<VendorPaymentAllocation>

    @Query("DELETE FROM vendor_payment_allocations")
    suspend fun deleteAllPaymentAllocations()

    @Query("DELETE FROM vendor_payments")
    suspend fun deleteAllPayments()
}

@Dao
interface PurchaseDao {
    @Insert
    suspend fun insertOrder(order: PurchaseOrder): Long

    @androidx.room.Update
    suspend fun updateOrder(order: PurchaseOrder)

    @Insert
    suspend fun insertOrderLine(line: PurchaseOrderLine): Long

    @Query("SELECT * FROM purchase_orders ORDER BY createdAt DESC")
    suspend fun getOrders(): List<PurchaseOrder>

    @Query("SELECT * FROM purchase_orders WHERE id = :id")
    suspend fun getOrder(id: Long): PurchaseOrder?

    @Query("SELECT * FROM purchase_order_lines WHERE orderId = :orderId")
    suspend fun getOrderLines(orderId: Long): List<PurchaseOrderLine>

    @Query("SELECT * FROM purchase_order_lines")
    suspend fun getAllOrderLines(): List<PurchaseOrderLine>

    @Query("SELECT COUNT(*) FROM purchase_orders WHERE vendorId = :vendorId")
    suspend fun countOrders(vendorId: Long): Int

    @Query("DELETE FROM purchase_order_lines")
    suspend fun deleteAllOrderLines()

    @Query("DELETE FROM purchase_orders")
    suspend fun deleteAllOrders()

    @Insert
    suspend fun insertReceipt(receipt: GoodsReceipt): Long

    @Query("UPDATE goods_receipts SET receiptNumber = :receiptNumber WHERE id = :id")
    suspend fun setReceiptNumber(id: Long, receiptNumber: String)

    @Insert
    suspend fun insertReceiptLine(line: GoodsReceiptLine): Long

    @Query("SELECT * FROM goods_receipts ORDER BY receivedAt DESC")
    suspend fun getReceipts(): List<GoodsReceipt>

    @Query("SELECT * FROM goods_receipts WHERE id = :id")
    suspend fun getReceipt(id: Long): GoodsReceipt?

    @Query("SELECT * FROM goods_receipt_lines WHERE receiptId = :receiptId")
    suspend fun getReceiptLines(receiptId: Long): List<GoodsReceiptLine>

    @Query("SELECT * FROM goods_receipt_lines")
    suspend fun getAllReceiptLines(): List<GoodsReceiptLine>

    @Query("SELECT COUNT(*) FROM goods_receipts WHERE vendorId = :vendorId")
    suspend fun countReceipts(vendorId: Long): Int

    @Query("DELETE FROM goods_receipt_lines WHERE receiptId = :receiptId")
    suspend fun deleteReceiptLines(receiptId: Long)

    @Query("DELETE FROM goods_receipts WHERE id = :id")
    suspend fun deleteReceipt(id: Long)

    @Query("DELETE FROM goods_receipt_lines")
    suspend fun deleteAllReceiptLines()

    @Query("DELETE FROM goods_receipts")
    suspend fun deleteAllReceipts()

    @Insert
    suspend fun insertInvoice(invoice: PurchaseInvoice): Long

    @androidx.room.Update
    suspend fun updateInvoice(invoice: PurchaseInvoice)

    @Insert
    suspend fun insertInvoiceLine(line: PurchaseInvoiceLine): Long

    @Query("SELECT * FROM purchase_invoices ORDER BY invoiceDate DESC")
    suspend fun getInvoices(): List<PurchaseInvoice>

    @Query("SELECT * FROM purchase_invoices WHERE vendorId = :vendorId ORDER BY invoiceDate ASC")
    suspend fun getInvoicesForVendor(vendorId: Long): List<PurchaseInvoice>

    @Query("SELECT * FROM purchase_invoices WHERE id = :id")
    suspend fun getInvoice(id: Long): PurchaseInvoice?

    @Query("SELECT * FROM purchase_invoice_lines WHERE invoiceId = :invoiceId")
    suspend fun getInvoiceLines(invoiceId: Long): List<PurchaseInvoiceLine>

    @Query("SELECT * FROM purchase_invoice_lines")
    suspend fun getAllInvoiceLines(): List<PurchaseInvoiceLine>

    @Query("SELECT COUNT(*) FROM purchase_invoices WHERE vendorId = :vendorId")
    suspend fun countInvoices(vendorId: Long): Int

    @Query("DELETE FROM purchase_invoice_lines WHERE invoiceId = :invoiceId")
    suspend fun deleteInvoiceLines(invoiceId: Long)

    @Query("DELETE FROM purchase_invoices WHERE id = :id")
    suspend fun deleteInvoice(id: Long)

    @Query("DELETE FROM purchase_invoice_lines")
    suspend fun deleteAllInvoiceLines()

    @Query("DELETE FROM purchase_invoices")
    suspend fun deleteAllInvoices()

    @Insert
    suspend fun insertQuote(quote: VendorQuote): Long

    @Insert
    suspend fun insertQuoteLine(line: VendorQuoteLine): Long

    @Query("SELECT * FROM vendor_quotes ORDER BY quoteDate DESC")
    suspend fun getQuotes(): List<VendorQuote>

    @Query("SELECT * FROM vendor_quotes WHERE id = :id")
    suspend fun getQuote(id: Long): VendorQuote?

    @Query("SELECT * FROM vendor_quote_lines WHERE quoteId = :quoteId")
    suspend fun getQuoteLines(quoteId: Long): List<VendorQuoteLine>

    @Query("SELECT * FROM vendor_quote_lines")
    suspend fun getAllQuoteLines(): List<VendorQuoteLine>

    @Query("SELECT COUNT(*) FROM vendor_quotes WHERE vendorId = :vendorId")
    suspend fun countQuotes(vendorId: Long): Int

    @Query("DELETE FROM vendor_quote_lines")
    suspend fun deleteAllQuoteLines()

    @Query("DELETE FROM vendor_quotes")
    suspend fun deleteAllQuotes()
}
