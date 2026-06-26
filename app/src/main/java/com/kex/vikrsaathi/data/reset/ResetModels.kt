package com.kex.vikrsaathi.data.reset

data class ResetOptions(
    val resetCustomers: Boolean = false,
    val resetItems: Boolean = false,
    val resetSales: Boolean = false,
    val resetTemplates: Boolean = false,
    val resetSettings: Boolean = false,
    val resetInvoiceConfig: Boolean = false
) {
    fun hasAnySelected(): Boolean =
        resetCustomers || resetItems || resetSales || resetTemplates ||
            resetSettings || resetInvoiceConfig

    fun selectedCategoryKeys(): List<String> = buildList {
        if (resetCustomers) add(CATEGORY_CUSTOMERS)
        if (resetItems) add(CATEGORY_ITEMS)
        if (resetSales) add(CATEGORY_SALES)
        if (resetTemplates) add(CATEGORY_TEMPLATES)
        if (resetSettings) add(CATEGORY_SETTINGS)
        if (resetInvoiceConfig) add(CATEGORY_INVOICE_CONFIG)
    }

    companion object {
        const val CATEGORY_CUSTOMERS = "customers"
        const val CATEGORY_ITEMS = "items"
        const val CATEGORY_SALES = "sales"
        const val CATEGORY_TEMPLATES = "templates"
        const val CATEGORY_SETTINGS = "settings"
        const val CATEGORY_INVOICE_CONFIG = "invoice_config"
    }
}

data class ResetResult(
    val historyEntryId: String,
    val resetCategories: List<String>
)

data class ResetHistoryEntry(
    val id: String,
    val performedAt: Long,
    val resetCategories: List<String>,
    val snapshotFileName: String
)

typealias ResetProgressListener = (message: String, percent: Int) -> Unit
