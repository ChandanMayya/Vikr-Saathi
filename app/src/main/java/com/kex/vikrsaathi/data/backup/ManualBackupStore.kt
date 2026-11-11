package com.kex.vikrsaathi.data.backup

import android.content.Context
import android.net.Uri
import com.kex.vikrsaathi.util.BackupSaveResult
import java.io.File

data class ManualBackupRecord(
    val exportedAt: Long,
    val fileName: String,
    val displayPath: String,
    val categories: List<String>,
    val sizeBytes: Long,
    val contentUri: String?,
    val legacyPath: String?
) {
    fun toSaveResult(): BackupSaveResult = BackupSaveResult(
        fileName = fileName,
        displayPath = displayPath,
        contentUri = contentUri?.let { Uri.parse(it) },
        legacyFile = legacyPath?.let { File(it) },
        sizeBytes = sizeBytes
    )
}

sealed class ManualBackupStatus {
    data object None : ManualBackupStatus()
    data class Available(val record: ManualBackupRecord) : ManualBackupStatus()
    data class Missing(val record: ManualBackupRecord) : ManualBackupStatus()
}

class ManualBackupStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): ManualBackupRecord? {
        if (!prefs.contains(KEY_EXPORTED_AT)) return null
        return ManualBackupRecord(
            exportedAt = prefs.getLong(KEY_EXPORTED_AT, 0L),
            fileName = prefs.getString(KEY_FILE_NAME, "").orEmpty(),
            displayPath = prefs.getString(KEY_PATH, "").orEmpty(),
            categories = prefs.getString(KEY_CATEGORIES, "").orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            sizeBytes = prefs.getLong(KEY_SIZE, 0L),
            contentUri = prefs.getString(KEY_URI, null),
            legacyPath = prefs.getString(KEY_LEGACY, null)
        )
    }

    fun save(record: ManualBackupRecord) {
        prefs.edit()
            .putLong(KEY_EXPORTED_AT, record.exportedAt)
            .putString(KEY_FILE_NAME, record.fileName)
            .putString(KEY_PATH, record.displayPath)
            .putString(KEY_CATEGORIES, record.categories.joinToString(","))
            .putLong(KEY_SIZE, record.sizeBytes)
            .putString(KEY_URI, record.contentUri)
            .putString(KEY_LEGACY, record.legacyPath)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS = "vikr_saathi_manual_backup"
        private const val KEY_EXPORTED_AT = "exported_at"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_PATH = "display_path"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_SIZE = "size_bytes"
        private const val KEY_URI = "content_uri"
        private const val KEY_LEGACY = "legacy_path"
    }
}

object ManualBackupFiles {
    fun status(context: Context, store: ManualBackupStore = ManualBackupStore(context)): ManualBackupStatus {
        val record = store.load() ?: return ManualBackupStatus.None
        return if (isAccessible(context, record)) {
            ManualBackupStatus.Available(record)
        } else {
            ManualBackupStatus.Missing(record)
        }
    }

    fun isAccessible(context: Context, record: ManualBackupRecord): Boolean {
        val uri = record.contentUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        if (uri != null) {
            return try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        }
        val path = record.legacyPath
        return !path.isNullOrBlank() && File(path).isFile
    }

    fun categories(options: BackupExportOptions): List<String> = buildList {
        if (options.includeSales) add("sales")
        if (options.includeCustomers || options.includeCustomerBalances) add("customers")
        if (options.includeItems) add("items")
        if (options.includeVendors) add("vendors")
        if (options.includeTemplates) add("templates")
        if (options.includeSettings || options.includeInvoiceConfig) add("settings")
    }
}
