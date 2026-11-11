package com.kex.vikrsaathi.domain.finance

import com.kex.vikrsaathi.domain.money.Money

object ReturnReporting {

    /** Merchandise value when it was captured, otherwise the amount actually settled. */
    fun merchandisePaise(
        returnValuePaise: Long,
        moneyRefundedPaise: Long,
        creditAdjustmentPaise: Long
    ): Long {
        return if (returnValuePaise > 0L) returnValuePaise else moneyRefundedPaise + creditAdjustmentPaise
    }

    fun netSalesRupees(grossRupees: Double, returnPaise: Long): Double {
        return Money.paiseToRupees(Money.rupeesToPaise(grossRupees) - returnPaise)
    }
}
