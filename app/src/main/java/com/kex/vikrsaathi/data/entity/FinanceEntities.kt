package com.kex.vikrsaathi.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kex.vikrsaathi.domain.finance.QuoteAvailability
import com.kex.vikrsaathi.domain.finance.RequestStatus

@Entity(
    tableName = "bill_payments",
    foreignKeys = [
        ForeignKey(
            entity = Bill::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["billId", "method"], unique = true)]
)
data class BillPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val method: String,
    val amountPaise: Long
)

@Entity(
    tableName = "customer_ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["customerId", "occurredAt"]),
        Index("billId"),
        Index(value = ["referenceType", "referenceId"])
    ]
)
data class CustomerLedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val entryType: String,
    val amountPaise: Long,
    val occurredAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val method: String? = null,
    val note: String? = null,
    val reason: String? = null,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val reversesEntryId: Long? = null,
    val billId: Long? = null
)

@Entity(
    tableName = "customer_receipts",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("customerId")]
)
data class CustomerReceipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val amountPaise: Long,
    val occurredAt: Long,
    val method: String,
    val referenceNote: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "customer_receipt_allocations",
    foreignKeys = [
        ForeignKey(
            entity = CustomerReceipt::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("receiptId"), Index("billId")]
)
data class CustomerReceiptAllocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val billId: Long,
    val amountPaise: Long
)

@Entity(
    tableName = "sales_returns",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("customerId"), Index("billId")]
)
data class SalesReturn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val billId: Long? = null,
    val reason: String,
    val moneyRefundedPaise: Long = 0,
    val creditAdjustmentPaise: Long = 0,
    val occurredAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sales_return_lines",
    foreignKeys = [
        ForeignKey(
            entity = SalesReturn::class,
            parentColumns = ["id"],
            childColumns = ["returnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("returnId"), Index("itemId")]
)
data class SalesReturnLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnId: Long,
    val itemId: Long? = null,
    val itemName: String,
    val quantity: Int
)

@Entity(tableName = "vendors", indices = [Index("name"), Index("archived")])
data class Vendor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val email: String = "",
    val notes: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vendor_ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = Vendor::class,
            parentColumns = ["id"],
            childColumns = ["vendorId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["vendorId", "occurredAt"]),
        Index("invoiceId"),
        Index(value = ["referenceType", "referenceId"])
    ]
)
data class VendorLedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendorId: Long,
    val entryType: String,
    val amountPaise: Long,
    val occurredAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val method: String? = null,
    val note: String? = null,
    val reason: String? = null,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val reversesEntryId: Long? = null,
    val invoiceId: Long? = null
)

@Entity(
    tableName = "vendor_payments",
    foreignKeys = [
        ForeignKey(
            entity = Vendor::class,
            parentColumns = ["id"],
            childColumns = ["vendorId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("vendorId")]
)
data class VendorPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendorId: Long,
    val amountPaise: Long,
    val occurredAt: Long,
    val method: String,
    val referenceNote: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vendor_payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = VendorPayment::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paymentId"), Index("invoiceId")]
)
data class VendorPaymentAllocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val invoiceId: Long,
    val amountPaise: Long
)

@Entity(tableName = "purchase_requests")
data class PurchaseRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestNumber: String,
    val createdAt: Long = System.currentTimeMillis(),
    val requiredBy: Long? = null,
    val status: String = RequestStatus.DRAFT,
    val notes: String = ""
)

@Entity(
    tableName = "purchase_request_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseRequest::class,
            parentColumns = ["id"],
            childColumns = ["requestId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("requestId"), Index("itemId")]
)
data class PurchaseRequestLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: Long,
    val itemId: Long? = null,
    val itemName: String,
    val quantity: Int,
    val notes: String = ""
)

@Entity(
    tableName = "purchase_orders",
    foreignKeys = [
        ForeignKey(
            entity = Vendor::class,
            parentColumns = ["id"],
            childColumns = ["vendorId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PurchaseRequest::class,
            parentColumns = ["id"],
            childColumns = ["requestId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("vendorId"), Index("quoteId"), Index("requestId")]
)
data class PurchaseOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendorId: Long,
    val orderNumber: String,
    val quoteId: Long? = null,
    val expectedDelivery: Long? = null,
    val notes: String = "",
    val status: String = "ORDERED",
    val createdAt: Long = System.currentTimeMillis(),
    val requestId: Long? = null
)

@Entity(
    tableName = "purchase_order_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrder::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PurchaseRequestLine::class,
            parentColumns = ["id"],
            childColumns = ["requestLineId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("orderId"), Index("itemId"), Index("requestLineId")]
)
data class PurchaseOrderLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val itemId: Long? = null,
    val itemName: String,
    val quantity: Int,
    val ratePaise: Long,
    val requestLineId: Long? = null
)

@Entity(
    tableName = "goods_receipts",
    foreignKeys = [
        ForeignKey(
            entity = Vendor::class,
            parentColumns = ["id"],
            childColumns = ["vendorId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PurchaseOrder::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("vendorId"), Index("orderId")]
)
data class GoodsReceipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendorId: Long,
    val orderId: Long? = null,
    val receiptNumber: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val directReceipt: Boolean = false
)

@Entity(
    tableName = "goods_receipt_lines",
    foreignKeys = [
        ForeignKey(
            entity = GoodsReceipt::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("receiptId"), Index("itemId"), Index("orderLineId")]
)
data class GoodsReceiptLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val orderLineId: Long? = null,
    val itemId: Long? = null,
    val itemName: String,
    val orderedQty: Int = 0,
    val receivedQty: Int = 0,
    val shortQty: Int = 0,
    val damagedQty: Int = 0,
    val rejectedQty: Int = 0,
    val acceptedQty: Int = 0
)

@Entity(
    tableName = "purchase_invoices",
    foreignKeys = [
        ForeignKey(
            entity = Vendor::class,
            parentColumns = ["id"],
            childColumns = ["vendorId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("vendorId"), Index("orderId"), Index("receiptId")]
)
data class PurchaseInvoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendorId: Long,
    val invoiceNumber: String,
    val invoiceDate: Long = System.currentTimeMillis(),
    val orderId: Long? = null,
    val receiptId: Long? = null,
    val deliveryChargesPaise: Long = 0,
    val otherChargesPaise: Long = 0,
    val discountPaise: Long = 0,
    val totalPaise: Long = 0,
    val notes: String = ""
)

@Entity(
    tableName = "purchase_invoice_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseInvoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("invoiceId"), Index("itemId")]
)
data class PurchaseInvoiceLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val itemId: Long? = null,
    val itemName: String,
    val quantity: Int,
    val ratePaise: Long,
    val lineTotalPaise: Long
)

@Entity(
    tableName = "vendor_quotes",
    foreignKeys = [
        ForeignKey(
            entity = Vendor::class,
            parentColumns = ["id"],
            childColumns = ["vendorId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PurchaseRequest::class,
            parentColumns = ["id"],
            childColumns = ["requestId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("vendorId"),
        Index("requestId"),
        Index(value = ["vendorId", "requestId"], unique = true)
    ]
)
data class VendorQuote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendorId: Long,
    val quoteDate: Long = System.currentTimeMillis(),
    val validUntil: Long? = null,
    val deliveryChargesPaise: Long = 0,
    val discountPaise: Long = 0,
    val expectedDelivery: Long? = null,
    val notes: String = "",
    val requestId: Long? = null
)

@Entity(
    tableName = "vendor_quote_lines",
    foreignKeys = [
        ForeignKey(
            entity = VendorQuote::class,
            parentColumns = ["id"],
            childColumns = ["quoteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PurchaseRequestLine::class,
            parentColumns = ["id"],
            childColumns = ["requestLineId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("quoteId"), Index("itemId"), Index("requestLineId")]
)
data class VendorQuoteLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quoteId: Long,
    val itemId: Long? = null,
    val itemName: String,
    val ratePaise: Long,
    val availableQty: Int = 0,
    val notes: String = "",
    val requestLineId: Long? = null,
    val availability: String = QuoteAvailability.QUOTED
)
