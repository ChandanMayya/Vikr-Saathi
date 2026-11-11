package com.kex.vikrsaathi.domain.finance

object RefundMethod {
    const val CASH = "CASH"
    const val UPI = "UPI"
    const val STORE_CREDIT = "STORE_CREDIT"
    const val NONE = "NONE"
}

data class ResolvedRefund(
    val method: String,
    val moneyRefundedPaise: Long,
    val creditAdjustmentPaise: Long
)

data class ExchangeMoney(
    val exchangeCreditPaise: Long,
    val customerPaysPaise: Long,
    val shopRefundsPaise: Long,
    val customerPayMethod: String?,
    val refund: ResolvedRefund
)

/**
 * Settlement is checked here so the screen cannot post a combination the books reject.
 * Cash and UPI leave the customer balance unchanged. Store credit is a negative ledger
 * entry and needs a registered customer. An exchange applies the return value to the
 * replacement bill once; only the net difference is refunded or collected.
 */
object ReturnSettlement {

    fun resolveRefund(returnValuePaise: Long, method: String, hasCustomer: Boolean): ResolvedRefund {
        if (returnValuePaise < 0L) throw FinanceException(FinanceError.TOTAL_MISMATCH)
        if (returnValuePaise == 0L) {
            return ResolvedRefund(RefundMethod.NONE, 0L, 0L)
        }
        return when (method) {
            RefundMethod.CASH, RefundMethod.UPI ->
                ResolvedRefund(method, returnValuePaise, 0L)
            RefundMethod.STORE_CREDIT -> {
                if (!hasCustomer) throw FinanceException(FinanceError.CREDIT_REQUIRES_CUSTOMER)
                ResolvedRefund(method, 0L, returnValuePaise)
            }
            else -> throw FinanceException(FinanceError.INVALID_SETTLEMENT)
        }
    }

    fun resolveExchange(
        returnValuePaise: Long,
        replacementPaise: Long,
        dueMethod: String,
        hasCustomer: Boolean
    ): ExchangeMoney {
        if (returnValuePaise < 0L || replacementPaise < 0L) {
            throw FinanceException(FinanceError.TOTAL_MISMATCH)
        }
        val exchangeCredit = minOf(returnValuePaise, replacementPaise)
        val customerPays = replacementPaise - exchangeCredit
        val shopRefunds = returnValuePaise - exchangeCredit
        if (customerPays > 0L && shopRefunds > 0L) {
            throw FinanceException(FinanceError.INVALID_SETTLEMENT)
        }
        val payMethod = if (customerPays > 0L) {
            when (dueMethod) {
                PaymentMethod.CASH, PaymentMethod.UPI -> dueMethod
                PaymentMethod.CREDIT -> {
                    if (!hasCustomer) throw FinanceException(FinanceError.CREDIT_REQUIRES_CUSTOMER)
                    dueMethod
                }
                else -> throw FinanceException(FinanceError.INVALID_SETTLEMENT)
            }
        } else {
            null
        }
        val refund = if (shopRefunds > 0L) {
            resolveRefund(shopRefunds, dueMethod, hasCustomer)
        } else {
            ResolvedRefund(RefundMethod.NONE, 0L, 0L)
        }
        return ExchangeMoney(
            exchangeCreditPaise = exchangeCredit,
            customerPaysPaise = customerPays,
            shopRefundsPaise = shopRefunds,
            customerPayMethod = payMethod,
            refund = refund
        )
    }
}
