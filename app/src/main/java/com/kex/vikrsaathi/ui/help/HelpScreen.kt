package com.kex.vikrsaathi.ui.help

data class HelpGuide(
    val title: String,
    val overview: String,
    val sections: List<HelpSection>
)

data class HelpSection(
    val title: String,
    val items: List<String>
)

enum class HelpScreen {
    DASHBOARD,
    NEW_BILL,
    BILL_VIEW,
    HELD_BILLS,
    BILL_PREVIEW,
    CUSTOMERS,
    ITEMS,
    STOCK,
    BILLS_HISTORY,
    EXCEL_UPLOAD,
    SETTINGS,
    GENERAL_SETTINGS,
    SECURITY_SETTINGS,
    INVENTORY_SETTINGS,
    INVOICE_CONFIGURATION,
    INVOICE_IMAGE,
    INVOICE_COUNTER,
    INVOICE_TEMPLATES,
    INVOICE_BUILDER,
    BACKUP,
    RESET,
    ANALYSIS
}
