package com.kex.vikrsaathi.util

import com.kex.vikrsaathi.data.model.BillWithDetails

data class SalesReportFilter(
    val searchEnabled: Boolean = false,
    val searchQuery: String = "",
    val dateRangeEnabled: Boolean = false,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val counterRangeEnabled: Boolean = false,
    val counterFrom: Int? = null,
    val counterTo: Int? = null
)

object BillsFilterHelper {

    fun apply(bills: List<BillWithDetails>, filter: SalesReportFilter): List<BillWithDetails> {
        var filtered = bills

        if (filter.searchEnabled) {
            val query = filter.searchQuery.trim()
            if (query.isNotBlank()) {
                filtered = filtered.filter {
                    it.bill.billNumber.contains(query, ignoreCase = true) ||
                        it.customer?.name?.contains(query, ignoreCase = true) == true
                }
            }
        }

        if (filter.dateRangeEnabled) {
            filter.dateFrom?.let { from ->
                filtered = filtered.filter { it.bill.date >= from }
            }
            filter.dateTo?.let { to ->
                filtered = filtered.filter { it.bill.date <= to }
            }
        }

        if (filter.counterRangeEnabled) {
            filter.counterFrom?.let { from ->
                filtered = filtered.filter { it.bill.invoiceCounter >= from }
            }
            filter.counterTo?.let { to ->
                filtered = filtered.filter { it.bill.invoiceCounter <= to }
            }
        }

        return filtered
    }

    fun buildSummary(filter: SalesReportFilter): String {
        val parts = mutableListOf<String>()
        if (filter.searchEnabled && filter.searchQuery.isNotBlank()) {
            parts.add("Search: ${filter.searchQuery}")
        }
        if (filter.dateRangeEnabled) {
            val from = filter.dateFrom?.let {
                java.text.DateFormat.getDateInstance().format(java.util.Date(it))
            } ?: "Any"
            val to = filter.dateTo?.let {
                java.text.DateFormat.getDateInstance().format(java.util.Date(it))
            } ?: "Any"
            parts.add("Date: $from – $to")
        }
        if (filter.counterRangeEnabled) {
            val from = filter.counterFrom?.toString() ?: "Any"
            val to = filter.counterTo?.toString() ?: "Any"
            parts.add("Counter: $from – $to")
        }
        return if (parts.isEmpty()) "All sales" else parts.joinToString(" | ")
    }
}
