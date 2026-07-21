package com.kex.vikrsaathi.data.reset

import android.content.Context
import androidx.room.withTransaction
import com.kex.vikrsaathi.data.backup.BackupExportOptions
import com.kex.vikrsaathi.data.backup.BackupImportResult
import com.kex.vikrsaathi.data.backup.BackupManager
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.util.InvoiceBuilderPreferences
import com.kex.vikrsaathi.util.TemplateImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResetManager(
    private val context: Context,
    private val database: AppDatabase,
    private val backupManager: BackupManager,
    private val settingsRepository: SettingsRepository,
    private val invoiceTemplateRepository: InvoiceTemplateRepository,
    private val invoiceBuilderPreferences: InvoiceBuilderPreferences,
    private val historyStore: ResetHistoryStore
) {

    fun loadHistory(): List<ResetHistoryEntry> = historyStore.loadEntries()

    suspend fun performReset(
        options: ResetOptions,
        onProgress: ResetProgressListener
    ): ResetResult = withContext(Dispatchers.IO) {
        require(options.hasAnySelected()) { "No reset categories selected" }

        val categories = options.selectedCategoryKeys().toSet()
        val snapshotOptions = BackupExportOptions(
            includeCustomers = options.resetCustomers,
            includeItems = options.resetItems,
            includeSales = options.resetSales,
            includeTemplates = options.resetTemplates,
            includeSettings = options.resetSettings,
            includeInvoiceConfig = options.resetInvoiceConfig
        )

        onProgress("Creating safety snapshot…", 5)
        val snapshotJson = backupManager.buildBackupJson(snapshotOptions) { message, percent ->
            val mapped = 5 + (percent * 35 / 100)
            onProgress(message, mapped)
        }

        val entryId = historyStore.createEntryId()
        val entry = ResetHistoryEntry(
            id = entryId,
            performedAt = System.currentTimeMillis(),
            resetCategories = categories.toList(),
            snapshotFileName = "$entryId.json"
        )
        historyStore.addEntry(entry, snapshotJson)
        onProgress("Safety snapshot saved", 42)

        clearSections(categories, onProgress)

        if (options.resetTemplates) {
            onProgress("Restoring default invoice template…", 92)
            invoiceTemplateRepository.ensureDefaultTemplateExists()
        }

        onProgress("Reset complete", 100)
        ResetResult(historyEntryId = entryId, resetCategories = categories.toList())
    }

    suspend fun restoreFromHistory(
        entryId: String,
        onProgress: ResetProgressListener
    ): BackupImportResult = withContext(Dispatchers.IO) {
        val entry = historyStore.getEntry(entryId)
            ?: throw IllegalArgumentException("Reset history entry not found")
        val json = historyStore.readSnapshotJson(entryId)
            ?: throw IllegalArgumentException("Reset snapshot file is missing")

        val sections = entry.resetCategories.toSet()
        onProgress("Preparing restore…", 5)
        clearSections(sections) { message, percent ->
            val mapped = 5 + (percent * 25 / 100)
            onProgress(message, mapped)
        }

        onProgress("Restoring data from snapshot…", 35)
        val result = backupManager.importBackupSections(json, sections) { message, percent ->
            val mapped = 35 + (percent * 60 / 100)
            onProgress(message, mapped)
        }

        if (sections.contains(ResetOptions.CATEGORY_TEMPLATES)) {
            onProgress("Finalizing invoice templates…", 98)
            invoiceTemplateRepository.ensureDefaultTemplateExists()
        }

        onProgress("Restore complete", 100)
        result
    }

    private suspend fun clearSections(
        sections: Set<String>,
        onProgress: ResetProgressListener = { _, _ -> }
    ) {
        database.withTransaction {
            if (ResetOptions.CATEGORY_SALES in sections) {
                onProgress("Deleting sales records…", 50)
                database.billDao().deleteAll()
            }
            if (ResetOptions.CATEGORY_CUSTOMERS in sections) {
                onProgress("Deleting customers…", 58)
                database.customerDao().deleteAll()
            }
            if (ResetOptions.CATEGORY_ITEMS in sections) {
                onProgress("Deleting items…", 66)
                database.itemDao().deleteAll()
                database.stockMovementDao().deleteAll()
            }
            if (ResetOptions.CATEGORY_TEMPLATES in sections) {
                onProgress("Deleting invoice templates…", 74)
                database.invoiceTemplateVersionDao().deleteAll()
                database.invoiceTemplateDao().deleteAll()
                TemplateImageStore.clearAll(context)
                invoiceBuilderPreferences.clear()
            }
            if (ResetOptions.CATEGORY_SETTINGS in sections) {
                onProgress("Resetting shop settings & branding…", 82)
                settingsRepository.resetShopBrandingToDefaults(context)
            }
        }
        if (ResetOptions.CATEGORY_INVOICE_CONFIG in sections) {
            onProgress("Resetting invoice configuration…", 86)
            settingsRepository.resetInvoiceConfigToDefaults()
        }
        onProgress("Selected data cleared", 88)
    }
}
