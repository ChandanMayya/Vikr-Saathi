package com.kex.vikrsaathi.data.backup

import android.content.Context
import android.net.Uri
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.entity.InvoiceTemplateEntity
import com.kex.vikrsaathi.data.entity.InvoiceTemplateVersionEntity
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.model.template.ElementBinding
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.TemplateJsonCodec
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.CustomerRepository
import com.kex.vikrsaathi.data.repository.InventoryRepository
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.kex.vikrsaathi.data.repository.ItemRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.data.entity.StockMovement
import com.kex.vikrsaathi.util.BackupSaveResult
import com.kex.vikrsaathi.util.BackupStorageHelper
import com.kex.vikrsaathi.util.AppThemeManager
import com.kex.vikrsaathi.util.TemplateImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val customerRepository: CustomerRepository,
    private val itemRepository: ItemRepository,
    private val billRepository: BillRepository,
    private val invoiceTemplateRepository: InvoiceTemplateRepository,
    private val inventoryRepository: InventoryRepository
) {

    suspend fun exportBackup(
        options: BackupExportOptions,
        onProgress: BackupProgressListener
    ): BackupSaveResult = withContext(Dispatchers.IO) {
        require(options.hasAnySelected()) { "No export options selected" }
        val json = buildBackupJson(options, onProgress)
        onProgress("Saving to Vikr Saathi folder…", 95)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "vikr_saathi_backup_$timestamp.json"
        val result = BackupStorageHelper.saveBackupJson(context, fileName, json)
        onProgress("Backup complete", 100)
        result
    }

    suspend fun buildBackupJson(
        options: BackupExportOptions = BackupExportOptions(),
        onProgress: BackupProgressListener = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        collectBackupPayload(options, onProgress).let { payload ->
            BackupJsonCodec.buildBackupJson(
                options = options,
                settings = settingsRepository,
                customers = payload.customers,
                items = payload.items,
                bills = payload.bills,
                templates = payload.templates,
                templateVersions = payload.templateVersions,
                templateImages = payload.templateImages,
                stockMovements = payload.stockMovements
            )
        }
    }

    suspend fun importBackupSections(
        json: String,
        sections: Set<String>,
        onProgress: BackupProgressListener
    ): BackupImportResult = withContext(Dispatchers.IO) {
        validateSchema(json)
        val root = JSONObject(json)
        val originalIncludes = root.optJSONArray("includes")?.let { array ->
            buildSet {
                for (i in 0 until array.length()) add(array.getString(i))
            }
        }.orEmpty()
        val filteredIncludes = originalIncludes.intersect(sections)
        val filteredRoot = JSONObject(json)
        filteredRoot.put("includes", JSONArray(filteredIncludes.toList()))
        importBackup(filteredRoot.toString(), onProgress)
    }

    private data class BackupPayload(
        val customers: List<Customer>,
        val items: List<Item>,
        val bills: List<com.kex.vikrsaathi.data.model.BillWithDetails>,
        val templates: List<InvoiceTemplateEntity>,
        val templateVersions: List<InvoiceTemplateVersionEntity>,
        val templateImages: List<TemplateImageBackup>,
        val stockMovements: List<StockMovement>
    )

    private suspend fun collectBackupPayload(
        options: BackupExportOptions,
        onProgress: BackupProgressListener
    ): BackupPayload {
        onProgress("Preparing backup…", 5)

        val customers = if (options.includeCustomers) {
            onProgress("Reading customers…", 15)
            database.customerDao().getAllCustomersSync()
        } else {
            emptyList()
        }

        val items = if (options.includeItems) {
            onProgress("Reading items…", 25)
            database.itemDao().getAllItemsSync()
        } else {
            emptyList()
        }

        val stockMovements = if (options.includeItems) {
            onProgress("Reading stock movements…", 30)
            inventoryRepository.getAllMovementsSync()
        } else {
            emptyList()
        }

        val bills = if (options.includeSales) {
            onProgress("Reading sales records…", 40)
            database.billDao().getAllBillsWithDetailsSync()
        } else {
            emptyList()
        }

        val templates = if (options.includeTemplates) {
            onProgress("Reading invoice templates…", 55)
            database.invoiceTemplateDao().getAllTemplatesSync()
        } else {
            emptyList()
        }

        val templateVersions = if (options.includeTemplates) {
            onProgress("Reading template versions…", 62)
            database.invoiceTemplateVersionDao().getAllVersions()
        } else {
            emptyList()
        }

        val templateImages = if (options.includeTemplates) {
            onProgress("Encoding template images…", 70)
            collectTemplateImages(templates)
        } else {
            emptyList()
        }

        if (options.includeSettings || options.includeInvoiceConfig) {
            onProgress("Encoding shop settings and images…", 80)
        }

        onProgress("Building backup file…", 88)
        return BackupPayload(
            customers,
            items,
            bills,
            templates,
            templateVersions,
            templateImages,
            stockMovements
        )
    }

    suspend fun readManifest(uri: Uri): BackupManifest = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw IllegalArgumentException("Could not read backup file")
        validateSchema(json)
        BackupJsonCodec.parseManifest(json)
    }

    suspend fun readBackupJson(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw IllegalArgumentException("Could not read backup file")
    }

    suspend fun importBackup(
        json: String,
        onProgress: BackupProgressListener
    ): BackupImportResult = withContext(Dispatchers.IO) {
        validateSchema(json)
        val root = JSONObject(json)
        val includes = root.optJSONArray("includes")?.let { array ->
            buildSet {
                for (i in 0 until array.length()) add(array.getString(i))
            }
        }.orEmpty()

        val errors = mutableListOf<String>()
        var customersImported = 0
        var itemsImported = 0
        var itemsSkipped = 0
        var billsImported = 0
        var billsSkipped = 0
        var templatesImported = 0
        var settingsRestored = false

        val customerIdMap = mutableMapOf<Long, Long>()
        val itemIdMap = mutableMapOf<Long, Long>()
        val templateIdMap = mutableMapOf<Long, Long>()

        if ("settings" in includes) {
            onProgress("Restoring shop settings…", 10)
            BackupJsonCodec.parseSettings(root)?.let { backup ->
                if ("invoice_config" in includes) {
                    restoreShopSettings(backup)
                    settingsRestored = true
                } else {
                    restoreSettings(backup)
                    settingsRestored = true
                }
            }
        }

        if ("invoice_config" in includes) {
            onProgress("Restoring invoice configuration…", 12)
            BackupJsonCodec.parseSettings(root)?.let { backup ->
                restoreInvoiceConfig(backup)
            }
        }

        if ("customers" in includes) {
            onProgress("Importing customers…", 25)
            BackupJsonCodec.parseCustomers(root).forEach { backup ->
                try {
                    val id = customerRepository.insert(
                        Customer(
                            name = backup.name,
                            address1 = backup.address1,
                            address2 = backup.address2,
                            city = backup.city,
                            state = backup.state,
                            pincode = backup.pincode,
                            phone = backup.phone,
                            remarks = backup.remarks
                        )
                    )
                    customerIdMap[backup.legacyId] = id
                    customersImported++
                } catch (e: Exception) {
                    errors.add("Customer ${backup.name}: ${e.message}")
                }
            }
        }

        if ("items" in includes) {
            onProgress("Importing items…", 40)
            BackupJsonCodec.parseItems(root).forEach { backup ->
                try {
                    val barcode = backup.barcode
                    if (!barcode.isNullOrBlank()) {
                        val existing = itemRepository.getByBarcode(barcode)
                        if (existing != null) {
                            itemIdMap[backup.legacyId] = existing.id
                            itemsSkipped++
                            return@forEach
                        }
                    }
                    val id = itemRepository.insertWithStock(
                        Item(
                            name = backup.name,
                            barcode = backup.barcode,
                            mrp = backup.mrp,
                            discount = backup.discount,
                            sellingPrice = backup.sellingPrice,
                            remarks = backup.remarks,
                            stockQty = backup.stockQty
                        )
                    )
                    itemIdMap[backup.legacyId] = id
                    itemsImported++
                } catch (e: Exception) {
                    itemsSkipped++
                    errors.add("Item ${backup.name}: ${e.message}")
                }
            }

            onProgress("Importing stock movements…", 48)
            BackupJsonCodec.parseStockMovements(root).forEach { backup ->
                val mappedItemId = itemIdMap[backup.itemLegacyId] ?: return@forEach
                try {
                    inventoryRepository.importMovement(
                        StockMovement(
                            itemId = mappedItemId,
                            delta = backup.delta,
                            quantityAfter = backup.quantityAfter,
                            type = backup.type,
                            referenceType = backup.referenceType,
                            referenceId = backup.referenceId,
                            note = backup.note,
                            createdAt = backup.createdAt
                        )
                    )
                } catch (e: Exception) {
                    errors.add("Stock movement: ${e.message}")
                }
            }
        }

        if ("templates" in includes) {
            onProgress("Importing invoice templates…", 55)
            val images = BackupJsonCodec.parseTemplateImages(root)
            BackupJsonCodec.parseTemplates(root).forEach { backup ->
                try {
                    val parsed = TemplateJsonCodec.fromJson(
                        backup.templateJson,
                        id = 0,
                        isDefault = false,
                        name = backup.name
                    )
                    val newId = invoiceTemplateRepository.insert(
                        parsed.copy(id = 0, name = backup.name, isDefault = false)
                    )
                    templateIdMap[backup.legacyId] = newId
                    restoreTemplateImages(newId, parsed, images.filter { it.templateLegacyId == backup.legacyId })
                    if (backup.isDefault) {
                        invoiceTemplateRepository.setAsDefault(newId)
                    }
                    templatesImported++
                } catch (e: Exception) {
                    errors.add("Template ${backup.name}: ${e.message}")
                }
            }

            onProgress("Importing template versions…", 65)
            BackupJsonCodec.parseTemplateVersions(root).forEach { version ->
                val newTemplateId = templateIdMap[version.templateLegacyId] ?: return@forEach
                try {
                    database.invoiceTemplateVersionDao().insert(
                        InvoiceTemplateVersionEntity(
                            templateId = newTemplateId,
                            versionNumber = version.versionNumber,
                            snapshotJson = version.snapshotJson,
                            savedAt = version.savedAt
                        )
                    )
                } catch (e: Exception) {
                    errors.add("Template version ${version.versionNumber}: ${e.message}")
                }
            }
        }

        if ("sales" in includes) {
            onProgress("Importing sales…", 80)
            BackupJsonCodec.parseBills(root).forEach { backup ->
                try {
                    val customerId = backup.customerLegacyId?.let { customerIdMap[it] }
                    val lineItems = backup.items.map { line ->
                        val mappedItemId = line.itemLegacyId?.let { itemIdMap[it] }
                        line.toLineItem(mappedItemId)
                    }
                    val imported = billRepository.importBillFromBackup(
                        billNumber = backup.billNumber,
                        invoiceCounter = backup.invoiceCounter,
                        date = backup.date,
                        customerId = customerId,
                        lineItems = lineItems,
                        billRoundOff = backup.roundOff
                    )
                    if (imported == null) {
                        billsSkipped++
                    } else {
                        billsImported++
                    }
                } catch (e: Exception) {
                    billsSkipped++
                    errors.add("Bill ${backup.billNumber}: ${e.message}")
                }
            }
        }

        onProgress("Import complete", 100)
        BackupImportResult(
            customersImported = customersImported,
            itemsImported = itemsImported,
            itemsSkipped = itemsSkipped,
            billsImported = billsImported,
            billsSkipped = billsSkipped,
            templatesImported = templatesImported,
            settingsRestored = settingsRestored,
            errors = errors
        )
    }

    private fun validateSchema(json: String) {
        val root = JSONObject(json)
        val version = root.optInt("schemaVersion", 0)
        if (version !in BackupJsonCodec.SUPPORTED_SCHEMA_VERSIONS) {
            throw IllegalArgumentException("Unsupported backup version: $version")
        }
    }

    private fun restoreSettings(backup: SettingsBackup) {
        restoreShopSettings(backup)
        restoreInvoiceConfig(backup)
    }

    private fun restoreShopSettings(backup: SettingsBackup) {
        settingsRepository.shopName = backup.shopName.ifBlank { settingsRepository.shopName }
        settingsRepository.currencySymbol = backup.currencySymbol.ifBlank { settingsRepository.currencySymbol }
        settingsRepository.defaultDiscount = backup.defaultDiscount
        settingsRepository.themeMode = backup.themeMode
        settingsRepository.inventoryMode = backup.inventoryMode
        settingsRepository.lowStockThreshold = backup.lowStockThreshold
        AppThemeManager.apply(backup.themeMode)

        BackupJsonCodec.decodeBitmap(backup.headerImageBase64)?.let {
            settingsRepository.saveHeaderImage(it)
        }
        BackupJsonCodec.decodeBitmap(backup.signatureImageBase64)?.let {
            settingsRepository.saveSignatureImage(it)
        }
        BackupJsonCodec.decodeBitmap(backup.logoImageBase64)?.let {
            settingsRepository.saveShopLogoImage(it)
        }
    }

    private fun restoreInvoiceConfig(backup: SettingsBackup) {
        settingsRepository.invoicePrefix = backup.invoicePrefix
        settingsRepository.invoiceSuffix = backup.invoiceSuffix
        settingsRepository.invoiceSeparator = backup.invoiceSeparator.ifBlank { "/" }
        settingsRepository.invoiceCounter = backup.invoiceCounter.coerceAtLeast(1)
        settingsRepository.invoiceCounterMinDigits = backup.invoiceCounterMinDigits
    }

    private fun collectTemplateImages(
        templates: List<InvoiceTemplateEntity>
    ): List<TemplateImageBackup> {
        val result = mutableListOf<TemplateImageBackup>()
        templates.forEach { entity ->
            val template = TemplateJsonCodec.fromJson(
                entity.elementsJson,
                id = entity.id,
                isDefault = entity.isDefault,
                name = entity.name
            )
            val images = TemplateImageStore.loadForTemplate(context, template)
            images.forEach { (elementId, bitmap) ->
                BackupJsonCodec.encodeBitmap(bitmap)?.let { base64 ->
                    result.add(
                        TemplateImageBackup(
                            templateLegacyId = entity.id,
                            elementId = elementId,
                            base64 = base64
                        )
                    )
                }
            }
        }
        return result
    }

    private suspend fun restoreTemplateImages(
        newTemplateId: Long,
        template: InvoiceTemplate,
        images: List<TemplateImageBackup>
    ) {
        if (images.isEmpty()) return
        images.forEach { image ->
            val bitmap = BackupJsonCodec.decodeBitmap(image.base64) ?: return@forEach
            TemplateImageStore.save(context, newTemplateId, image.elementId, bitmap)
        }
        val updatedElements = template.elements.map { element ->
            if (element.kind == ElementKind.IMAGE &&
                element.binding == ElementBinding.STATIC &&
                images.any { it.elementId == element.id }
            ) {
                val content = element.content.toMutableMap()
                content["imagePath"] = "${newTemplateId}_${element.id}.png"
                element.copy(content = content)
            } else {
                element
            }
        }
        val updated = template.copy(id = newTemplateId, elements = updatedElements)
        database.invoiceTemplateDao().insert(updated.toEntity())
    }

    private fun InvoiceTemplate.toEntity(): InvoiceTemplateEntity {
        return InvoiceTemplateEntity(
            id = id,
            name = name,
            isDefault = isDefault,
            pageWidthPt = pageWidthPt,
            pageHeightPt = pageHeightPt,
            marginLeft = marginLeft,
            marginTop = marginTop,
            marginRight = marginRight,
            marginBottom = marginBottom,
            elementsJson = TemplateJsonCodec.toJson(this),
            version = version,
            updatedAt = updatedAt
        )
    }
}
