package com.kex.vikrsaathi.data.backup

data class BackupExportOptions(
    val includeSales: Boolean = true,
    val includeSettings: Boolean = true,
    val includeInvoiceConfig: Boolean = false,
    val includeTemplates: Boolean = true,
    val includeItems: Boolean = true,
    val includeCustomers: Boolean = true
) {
    fun hasAnySelected(): Boolean =
        includeSales || includeSettings || includeInvoiceConfig ||
            includeTemplates || includeItems || includeCustomers
}

data class BackupManifest(
    val schemaVersion: Int,
    val exportedAt: Long,
    val includes: List<String>,
    val customerCount: Int,
    val itemCount: Int,
    val billCount: Int,
    val templateCount: Int,
    val templateVersionCount: Int,
    val hasHeaderImage: Boolean,
    val hasSignatureImage: Boolean,
    val hasLogoImage: Boolean,
    val templateImageCount: Int
)

data class BackupImportResult(
    val customersImported: Int,
    val itemsImported: Int,
    val itemsSkipped: Int,
    val billsImported: Int,
    val billsSkipped: Int,
    val templatesImported: Int,
    val settingsRestored: Boolean,
    val errors: List<String>
)

typealias BackupProgressListener = (message: String, percent: Int) -> Unit
