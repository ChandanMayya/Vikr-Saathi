package com.kex.vikrsaathi.domain.finance

object PaymentSplitValidator {

    fun validate(
        totalPaise: Long,
        portions: List<PaymentPortion>,
        hasRegisteredCustomer: Boolean
    ): PaymentSplitResult {
        if (totalPaise < 0L) throw FinanceException(FinanceError.TOTAL_MISMATCH)
        val positive = portions.filter { it.amountPaise != 0L }
        if (totalPaise == 0L && positive.isEmpty()) {
            return PaymentSplitResult(emptyList(), BillPaymentStatus.LABEL_CASH, 0L)
        }
        if (positive.isEmpty()) throw FinanceException(FinanceError.EMPTY_PAYMENT)
        if (positive.any { it.amountPaise < 0L }) {
            throw FinanceException(FinanceError.NON_POSITIVE_PORTION)
        }
        val methods = positive.map { it.method }
        if (methods.size != methods.toSet().size) {
            throw FinanceException(FinanceError.DUPLICATE_METHOD)
        }
        val allowed = setOf(PaymentMethod.CASH, PaymentMethod.UPI, PaymentMethod.CREDIT)
        if (methods.any { it !in allowed }) throw FinanceException(FinanceError.DUPLICATE_METHOD)
        if (positive.sumOf { it.amountPaise } != totalPaise) {
            throw FinanceException(FinanceError.TOTAL_MISMATCH)
        }
        val credit = positive.filter { it.method == PaymentMethod.CREDIT }.sumOf { it.amountPaise }
        if (credit > 0L && !hasRegisteredCustomer) {
            throw FinanceException(FinanceError.CREDIT_REQUIRES_CUSTOMER)
        }
        val label = when {
            positive.size > 1 -> BillPaymentStatus.LABEL_SPLIT
            positive.first().method == PaymentMethod.UPI -> BillPaymentStatus.LABEL_UPI
            positive.first().method == PaymentMethod.CREDIT -> BillPaymentStatus.LABEL_CREDIT
            else -> BillPaymentStatus.LABEL_CASH
        }
        return PaymentSplitResult(positive, label, credit)
    }

    fun labelFor(portions: List<PaymentPortion>): String {
        if (portions.isEmpty()) return BillPaymentStatus.LABEL_LEGACY
        if (portions.size > 1) return BillPaymentStatus.LABEL_SPLIT
        return when (portions.first().method) {
            PaymentMethod.UPI -> BillPaymentStatus.LABEL_UPI
            PaymentMethod.CREDIT -> BillPaymentStatus.LABEL_CREDIT
            else -> BillPaymentStatus.LABEL_CASH
        }
    }
}
