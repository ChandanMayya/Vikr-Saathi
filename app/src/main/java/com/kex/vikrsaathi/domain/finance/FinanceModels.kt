package com.kex.vikrsaathi.domain.finance

/**
 * Customer ledger sign: a positive amount means the customer owes the shop.
 * A negative amount means the shop owes the customer or holds an advance.
 *
 * Vendor ledger sign: a positive amount means the shop owes the vendor.
 * A negative amount means the vendor owes the shop or the shop holds a credit.
 *
 * Cash and UPI sale portions are settled and do not create ledger entries.
 * Only the credit portion of a sale increases the customer balance.
 */
object LedgerSign {
    const val CUSTOMER_POSITIVE_MEANS = "Customer owes the shop"
    const val VENDOR_POSITIVE_MEANS = "Shop owes the vendor"
}

object BillPaymentStatus {
    const val LEGACY = "LEGACY"
    const val RECORDED = "RECORDED"
    const val LABEL_LEGACY = "LEGACY"
    const val LABEL_CASH = "CASH"
    const val LABEL_UPI = "UPI"
    const val LABEL_CREDIT = "CREDIT"
    const val LABEL_SPLIT = "SPLIT"
}

object PaymentMethod {
    const val CASH = "CASH"
    const val UPI = "UPI"
    const val CREDIT = "CREDIT"
}

object CustomerLedgerType {
    const val OPENING_BALANCE = "OPENING_BALANCE"
    const val CREDIT_SALE = "CREDIT_SALE"
    const val PAYMENT_RECEIVED = "PAYMENT_RECEIVED"
    const val SALES_RETURN = "SALES_RETURN"
    const val CREDIT_ADJUSTMENT = "CREDIT_ADJUSTMENT"
    const val REVERSAL = "REVERSAL"
}

object VendorLedgerType {
    const val OPENING_BALANCE = "OPENING_BALANCE"
    const val PURCHASE_INVOICE = "PURCHASE_INVOICE"
    const val VENDOR_PAYMENT = "VENDOR_PAYMENT"
    const val PURCHASE_RETURN = "PURCHASE_RETURN"
    const val REVERSAL = "REVERSAL"
}

object FinanceReference {
    const val BILL = "BILL"
    const val CUSTOMER_RECEIPT = "CUSTOMER_RECEIPT"
    const val SALES_RETURN = "SALES_RETURN"
    const val OPENING = "OPENING"
    const val ADJUSTMENT = "ADJUSTMENT"
    const val PURCHASE_INVOICE = "PURCHASE_INVOICE"
    const val VENDOR_PAYMENT = "VENDOR_PAYMENT"
    const val PURCHASE_RETURN = "PURCHASE_RETURN"
    const val GOODS_RECEIPT = "GOODS_RECEIPT"
}

object ReversalReason {
    const val BILL_EDITED = "BILL_EDITED"
    const val BILL_DELETED = "BILL_DELETED"
    const val INVOICE_EDITED = "INVOICE_EDITED"
    const val INVOICE_DELETED = "INVOICE_DELETED"
}

enum class FinanceError {
    EMPTY_PAYMENT,
    DUPLICATE_METHOD,
    NON_POSITIVE_PORTION,
    TOTAL_MISMATCH,
    CREDIT_REQUIRES_CUSTOMER,
    PAYMENT_CHANGED_WHILE_DISABLED,
    LEGACY_BILL_HAS_PAYMENTS,
    REASON_REQUIRED,
    AMOUNT_REQUIRED,
    NOTHING_TO_RECORD,
    RETURN_QTY_EXCEEDS_SOLD,
    VENDOR_REQUIRED,
    VENDOR_HAS_HISTORY,
    CUSTOMER_HAS_LEDGER,
    QUOTE_LINE_REQUIRED,
    RECEIPT_QTY_INVALID,
    INVOICE_TOTAL_MISMATCH,
    REQUEST_LINE_REQUIRED,
    DUPLICATE_QUOTE,
    AWARD_EXCEEDS,
    REQUEST_NOT_EDITABLE,
    INVALID_QUOTE
}

class FinanceException(val code: FinanceError) : IllegalArgumentException(code.name)

data class PaymentPortion(
    val method: String,
    val amountPaise: Long
)

sealed class BillPaymentRequest {
    /** Do not rewrite payment rows. Fails if a recorded split no longer matches the total. */
    data object LeaveUnchanged : BillPaymentRequest()

    /** Keep a pre-feature bill as legacy. Does not create customer debt. */
    data object KeepLegacy : BillPaymentRequest()

    data class Record(val portions: List<PaymentPortion>) : BillPaymentRequest()
}

data class PaymentSplitResult(
    val portions: List<PaymentPortion>,
    val label: String,
    val creditPaise: Long
)
