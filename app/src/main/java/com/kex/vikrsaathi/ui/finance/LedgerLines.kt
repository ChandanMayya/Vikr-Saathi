package com.kex.vikrsaathi.ui.finance

import android.graphics.Typeface
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.databinding.ItemLedgerEntryBinding
import com.kex.vikrsaathi.domain.finance.LedgerRow
import com.kex.vikrsaathi.domain.finance.LedgerStatement
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.util.PriceCalculator

object LedgerLines {

    fun show(
        container: LinearLayout,
        statement: LedgerStatement,
        symbol: String,
        customer: Boolean,
        particulars: (LedgerRow) -> String
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        addRow(
            inflater,
            container,
            date = "",
            particulars = container.context.getString(R.string.ledger_row_opening),
            amount = "",
            balancePaise = statement.openingPaise,
            symbol = symbol,
            customer = customer,
            emphasize = true
        )
        if (statement.lines.isEmpty()) {
            addRow(
                inflater,
                container,
                date = "",
                particulars = container.context.getString(R.string.ledger_empty),
                amount = "",
                balancePaise = null,
                symbol = symbol,
                customer = customer,
                emphasize = false
            )
        } else {
            statement.lines.forEach { line ->
                addRow(
                    inflater,
                    container,
                    date = FinanceUi.formatDate(line.row.occurredAt),
                    particulars = particulars(line.row),
                    amount = PriceCalculator.formatSignedAmount(Money.paiseToRupees(line.row.amountPaise), symbol),
                    balancePaise = line.runningBalancePaise,
                    symbol = symbol,
                    customer = customer,
                    emphasize = false
                )
            }
        }
        addRow(
            inflater,
            container,
            date = "",
            particulars = container.context.getString(R.string.ledger_row_closing),
            amount = "",
            balancePaise = statement.closingPaise,
            symbol = symbol,
            customer = customer,
            emphasize = true
        )
    }

    private fun addRow(
        inflater: LayoutInflater,
        container: LinearLayout,
        date: String,
        particulars: String,
        amount: String,
        balancePaise: Long?,
        symbol: String,
        customer: Boolean,
        emphasize: Boolean
    ) {
        val row = ItemLedgerEntryBinding.inflate(inflater, container, true)
        row.textLedgerDate.text = date
        row.textLedgerParticulars.text = particulars
        row.textLedgerAmount.text = amount
        row.textLedgerBalance.text = balancePaise?.let { FinanceUi.formatBalance(it, symbol) }.orEmpty()
        val color = if (balancePaise == null) R.color.text_primary_dark
        else FinanceUi.balanceColorRes(balancePaise, customer)
        row.textLedgerBalance.setTextColor(container.context.getColor(color))
        if (emphasize) {
            row.textLedgerParticulars.setTypeface(null, Typeface.BOLD)
            row.root.setBackgroundColor(container.context.getColor(R.color.surface_muted))
        }
    }
}
