package com.kex.vikrsaathi.ui.finance

import android.content.Context
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.domain.finance.AgeBucket
import com.kex.vikrsaathi.domain.finance.CustomerLedgerType
import com.kex.vikrsaathi.domain.finance.FinanceError
import com.kex.vikrsaathi.domain.finance.FinanceException
import com.kex.vikrsaathi.domain.finance.VendorLedgerType
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.util.PriceCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FinanceUi {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun formatPaise(paise: Long, symbol: String): String =
        PriceCalculator.formatAmount(Money.paiseToRupees(paise), symbol)

    /** Balance shown without a sign. Color carries who owes whom. */
    fun formatBalance(paise: Long, symbol: String): String = formatPaise(kotlin.math.abs(paise), symbol)

    /**
     * Green when the other party owes the shop. Red when the shop owes them.
     * Customer balances are positive when the customer owes the shop.
     * Vendor balances are positive when the shop owes the vendor.
     */
    fun balanceColorRes(paise: Long, customer: Boolean): Int = when {
        paise == 0L -> R.color.text_primary_dark
        customer && paise > 0L -> R.color.balance_receivable
        !customer && paise < 0L -> R.color.balance_receivable
        else -> R.color.balance_payable
    }

    fun parseDateOrToday(text: String?): Long {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return System.currentTimeMillis()
        return dateFormat.parse(value)?.time ?: System.currentTimeMillis()
    }

    fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

    fun parseAmountPaise(text: String?): Long {
        val value = text?.trim()?.toDoubleOrNull() ?: return 0L
        return Money.rupeesToPaise(value)
    }

    fun message(context: Context, error: Throwable): String {
        val code = (error as? FinanceException)?.code
        val res = when (code) {
            FinanceError.EMPTY_PAYMENT -> R.string.payment_error_empty
            FinanceError.DUPLICATE_METHOD -> R.string.payment_error_duplicate
            FinanceError.NON_POSITIVE_PORTION, FinanceError.AMOUNT_REQUIRED -> R.string.payment_error_amount
            FinanceError.TOTAL_MISMATCH -> R.string.payment_error_total
            FinanceError.CREDIT_REQUIRES_CUSTOMER -> R.string.payment_error_credit_customer
            FinanceError.PAYMENT_CHANGED_WHILE_DISABLED -> R.string.payment_error_changed_while_off
            FinanceError.LEGACY_BILL_HAS_PAYMENTS -> R.string.payment_error_legacy_recorded
            FinanceError.REASON_REQUIRED -> R.string.payment_error_reason
            FinanceError.NOTHING_TO_RECORD -> R.string.payment_error_nothing
            FinanceError.RETURN_QTY_EXCEEDS_SOLD -> R.string.payment_error_return_qty
            FinanceError.VENDOR_REQUIRED -> R.string.payment_error_vendor
            FinanceError.VENDOR_HAS_HISTORY -> R.string.payment_error_vendor_history
            FinanceError.CUSTOMER_HAS_LEDGER -> R.string.payment_error_customer_ledger
            FinanceError.QUOTE_LINE_REQUIRED -> R.string.payment_error_lines
            FinanceError.RECEIPT_QTY_INVALID -> R.string.payment_error_receipt
            FinanceError.INVOICE_TOTAL_MISMATCH -> R.string.payment_error_total
            null -> R.string.payment_error_generic
        }
        return context.getString(res)
    }

    fun customerEntryLabel(context: Context, type: String): String = when (type) {
        CustomerLedgerType.OPENING_BALANCE -> context.getString(R.string.entry_opening)
        CustomerLedgerType.CREDIT_SALE -> context.getString(R.string.entry_credit_sale)
        CustomerLedgerType.PAYMENT_RECEIVED -> context.getString(R.string.entry_payment)
        CustomerLedgerType.SALES_RETURN -> context.getString(R.string.entry_return)
        CustomerLedgerType.CREDIT_ADJUSTMENT -> context.getString(R.string.entry_adjustment)
        CustomerLedgerType.REVERSAL -> context.getString(R.string.entry_reversal)
        else -> type
    }

    fun vendorEntryLabel(context: Context, type: String): String = when (type) {
        VendorLedgerType.OPENING_BALANCE -> context.getString(R.string.entry_opening)
        VendorLedgerType.PURCHASE_INVOICE -> context.getString(R.string.entry_purchase)
        VendorLedgerType.VENDOR_PAYMENT -> context.getString(R.string.entry_vendor_payment)
        VendorLedgerType.PURCHASE_RETURN -> context.getString(R.string.entry_purchase_return)
        VendorLedgerType.REVERSAL -> context.getString(R.string.entry_reversal)
        else -> type
    }

    fun ageLabel(context: Context, bucket: AgeBucket?): String = when (bucket) {
        AgeBucket.CURRENT -> context.getString(R.string.age_current)
        AgeBucket.DAYS_31_60 -> context.getString(R.string.age_31)
        AgeBucket.DAYS_61_90 -> context.getString(R.string.age_61)
        AgeBucket.OVER_90 -> context.getString(R.string.age_90)
        null -> context.getString(R.string.age_none)
    }

    fun paymentLabel(context: Context, code: String): String = when (code) {
        "CASH" -> context.getString(R.string.payment_cash)
        "UPI" -> context.getString(R.string.payment_upi)
        "CREDIT" -> context.getString(R.string.payment_credit)
        "SPLIT" -> context.getString(R.string.payment_split)
        else -> context.getString(R.string.payment_summary_legacy)
    }
}
