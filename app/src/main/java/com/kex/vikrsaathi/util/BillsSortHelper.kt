package com.kex.vikrsaathi.util

import com.kex.vikrsaathi.data.model.BillWithDetails

object BillsSortHelper {

    fun apply(
        bills: List<BillWithDetails>,
        preferences: BillsHistoryPreferences
    ): List<BillWithDetails> {
        return when {
            preferences.sortByDateAsc ->
                bills.sortedBy { it.bill.date }
            preferences.sortByInvoiceNumberAsc ->
                bills.sortedWith(
                    compareBy<BillWithDetails> { it.bill.invoiceCounter }
                        .thenBy { it.bill.billNumber }
                )
            else ->
                bills.sortedByDescending { it.bill.date }
        }
    }
}
