package com.kex.vikrsaathi.util

object SalesReportColumns {
    const val BILL_NUMBER = "Bill Number"
    const val INVOICE_COUNTER = "Invoice Counter"
    const val DATE = "Date"
    const val CUSTOMER_NAME = "Customer Name"
    const val CUSTOMER_PHONE = "Customer Phone"
    const val CUSTOMER_ADDRESS = "Customer Address"
    const val ITEM_NAME = "Item Name"
    const val QUANTITY = "Quantity"
    const val MRP = "MRP"
    const val DISCOUNT_PERCENT = "Discount %"
    const val LINE_TOTAL = "Line Total"
    const val BILL_TOTAL = "Bill Total"
    const val WARRANTY_PERIOD = "Warranty Period"
    const val WARRANTY_UNIT = "Warranty Unit"
    const val WARRANTY_PROVIDER = "Warranty Provider"
    const val WARRANTY_PROVIDER_NAME = "Warranty Provider Name"
    const val WARRANTY_TERMS = "Warranty Terms"
    const val WARRANTY_START = "Warranty Start"
    const val WARRANTY_END = "Warranty End"

    val HEADERS = listOf(
        BILL_NUMBER,
        INVOICE_COUNTER,
        DATE,
        CUSTOMER_NAME,
        CUSTOMER_PHONE,
        CUSTOMER_ADDRESS,
        ITEM_NAME,
        QUANTITY,
        MRP,
        DISCOUNT_PERCENT,
        LINE_TOTAL,
        BILL_TOTAL,
        WARRANTY_PERIOD,
        WARRANTY_UNIT,
        WARRANTY_PROVIDER,
        WARRANTY_PROVIDER_NAME,
        WARRANTY_TERMS,
        WARRANTY_START,
        WARRANTY_END
    )
}

data class SalesReportRow(
    val billNumber: String,
    val invoiceCounter: Int,
    val dateMillis: Long,
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val itemName: String,
    val quantity: Int,
    val mrp: Double,
    val discount: Double,
    val lineTotal: Double,
    val billTotal: Double,
    val warrantyPeriod: Int? = null,
    val warrantyUnit: String = "",
    val warrantyProvider: String = "",
    val warrantyProviderName: String = "",
    val warrantyTerms: String = "",
    val warrantyStart: String = "",
    val warrantyEnd: String = ""
)

data class SalesImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>
)
