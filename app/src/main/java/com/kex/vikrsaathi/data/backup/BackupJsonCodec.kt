package com.kex.vikrsaathi.data.backup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.InvoiceTemplateEntity
import com.kex.vikrsaathi.data.entity.InvoiceTemplateVersionEntity
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.data.model.template.TemplateJsonCodec
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.util.InventoryMode
import com.kex.vikrsaathi.util.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object BackupJsonCodec {

    const val SCHEMA_VERSION = BackupSchema.CURRENT
    val SUPPORTED_SCHEMA_VERSIONS = BackupSchema.SUPPORTED

    fun encodeBitmap(bitmap: Bitmap?): String? {
        if (bitmap == null) return null
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Exception) {
            null
        }
    }

    fun buildBackupJson(
        options: BackupExportOptions,
        settings: SettingsRepository,
        customers: List<Customer>,
        items: List<Item>,
        bills: List<BillWithDetails>,
        templates: List<InvoiceTemplateEntity>,
        templateVersions: List<InvoiceTemplateVersionEntity>,
        templateImages: List<TemplateImageBackup>,
        stockMovements: List<com.kex.vikrsaathi.data.entity.StockMovement> = emptyList(),
        versionsByBillId: Map<Long, List<com.kex.vikrsaathi.data.model.BillVersionDetails>> = emptyMap(),
        operators: List<com.kex.vikrsaathi.data.entity.OperatorProfile> = emptyList(),
        selectedOperatorId: Long = 0L,
        costLots: List<com.kex.vikrsaathi.data.entity.ItemCostLot> = emptyList(),
        batches: List<com.kex.vikrsaathi.data.entity.InventoryBatch> = emptyList(),
        batchMovements: List<com.kex.vikrsaathi.data.entity.InventoryBatchMovement> = emptyList(),
        allocations: List<com.kex.vikrsaathi.data.entity.BillBatchAllocation> = emptyList()
    ): String {
        val includes = buildList {
            if (options.includeCustomers) add("customers")
            if (options.includeItems) {
                add("items")
                if (stockMovements.isNotEmpty()) add("stock_movements")
                if (costLots.isNotEmpty()) add("item_cost_lots")
                if (batches.isNotEmpty()) add("inventory_batches")
            }
            if (options.includeSales) add("sales")
            if (options.includeSettings) {
                add("settings")
                if (operators.isNotEmpty()) add("operators")
            }
            if (options.includeInvoiceConfig) add("invoice_config")
            if (options.includeTemplates) add("templates")
        }

        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("includes", JSONArray(includes))

            if (options.includeSettings || options.includeInvoiceConfig) {
                put("settings", encodeSettings(settings))
            }
            if (options.includeSettings && operators.isNotEmpty()) {
                put("operators", JSONArray().apply {
                    operators.forEach { operator ->
                        put(JSONObject().apply {
                            put("legacyId", operator.id)
                            put("name", operator.name)
                            put("createdAt", operator.createdAt)
                        })
                    }
                })
                put("selectedOperatorLegacyId", selectedOperatorId)
            }
            if (options.includeCustomers) {
                put("customers", JSONArray().apply { customers.forEach { put(encodeCustomer(it)) } })
            }
            if (options.includeItems) {
                put("items", JSONArray().apply { items.forEach { put(encodeItem(it)) } })
                if (stockMovements.isNotEmpty()) {
                    put(
                        "stockMovements",
                        JSONArray().apply { stockMovements.forEach { put(encodeStockMovement(it)) } }
                    )
                }
                if (costLots.isNotEmpty()) {
                    put("itemCostLots", JSONArray().apply { costLots.forEach { put(encodeCostLot(it)) } })
                }
                if (batches.isNotEmpty()) {
                    put("inventoryBatches", JSONArray().apply { batches.forEach { put(encodeBatch(it)) } })
                    put("inventoryBatchMovements", JSONArray().apply { batchMovements.forEach { put(encodeBatchMovement(it)) } })
                    put("billBatchAllocations", JSONArray().apply { allocations.forEach { put(encodeAllocation(it)) } })
                }
            }
            if (options.includeSales) {
                put("bills", JSONArray().apply {
                    bills.forEach { put(encodeBill(it, versionsByBillId[it.bill.id].orEmpty())) }
                })
            }
            if (options.includeTemplates) {
                put("invoiceTemplates", JSONArray().apply {
                    templates.forEach { put(encodeTemplate(it)) }
                })
                put("templateVersions", JSONArray().apply {
                    templateVersions.forEach { put(encodeTemplateVersion(it)) }
                })
                put("templateImages", JSONArray().apply {
                    templateImages.forEach { put(encodeTemplateImage(it)) }
                })
            }
        }.toString(2)
    }

    fun parseManifest(json: String): BackupManifest {
        val root = JSONObject(json)
        val includes = root.optJSONArray("includes")?.let { array ->
            buildList {
                for (i in 0 until array.length()) add(array.getString(i))
            }
        }.orEmpty()
        val settings = root.optJSONObject("settings")
        val images = settings?.optJSONObject("images")
        return BackupManifest(
            schemaVersion = root.optInt("schemaVersion", 0),
            exportedAt = root.optLong("exportedAt", 0L),
            includes = includes,
            customerCount = root.optJSONArray("customers")?.length() ?: 0,
            itemCount = root.optJSONArray("items")?.length() ?: 0,
            billCount = root.optJSONArray("bills")?.length() ?: 0,
            templateCount = root.optJSONArray("invoiceTemplates")?.length() ?: 0,
            templateVersionCount = root.optJSONArray("templateVersions")?.length() ?: 0,
            hasHeaderImage = images?.optString("header")?.isNotBlank() == true,
            hasSignatureImage = images?.optString("signature")?.isNotBlank() == true,
            hasLogoImage = images?.optString("logo")?.isNotBlank() == true,
            templateImageCount = root.optJSONArray("templateImages")?.length() ?: 0
        )
    }

    fun parseCustomers(root: JSONObject): List<CustomerBackup> {
        val array = root.optJSONArray("customers") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(decodeCustomer(array.getJSONObject(i)))
            }
        }
    }

    fun parseItems(root: JSONObject): List<ItemBackup> {
        val array = root.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(decodeItem(array.getJSONObject(i)))
            }
        }
    }

    fun parseStockMovements(root: JSONObject): List<StockMovementBackup> {
        val array = root.optJSONArray("stockMovements") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(decodeStockMovement(array.getJSONObject(i)))
            }
        }
    }

    fun parseOperators(root: JSONObject): List<OperatorBackup> {
        val array = root.optJSONArray("operators") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    OperatorBackup(
                        legacyId = obj.optLong("legacyId"),
                        name = obj.optString("name"),
                        createdAt = obj.optLong("createdAt")
                    )
                )
            }
        }
    }

    fun parseCostLots(root: JSONObject): List<ItemCostLotBackup> {
        val array = root.optJSONArray("itemCostLots") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    ItemCostLotBackup(
                        itemLegacyId = obj.getLong("itemLegacyId"),
                        sourceType = obj.optString("sourceType"),
                        sourceId = obj.optLong("sourceId"),
                        qtyReceived = obj.optInt("qtyReceived"),
                        qtyRemaining = obj.optInt("qtyRemaining"),
                        unitCostPaise = obj.optLong("unitCostPaise"),
                        receivedAt = obj.optLong("receivedAt")
                    )
                )
            }
        }
    }

    fun parseBatches(root: JSONObject): List<InventoryBatchBackup> {
        val array = root.optJSONArray("inventoryBatches") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    InventoryBatchBackup(
                        legacyId = obj.getLong("legacyId"),
                        itemLegacyId = obj.getLong("itemLegacyId"),
                        lotNumber = obj.optString("lotNumber").takeIf { it.isNotBlank() && !obj.isNull("lotNumber") },
                        internalRef = obj.optString("internalRef", ""),
                        expiryEpochDay = if (obj.has("expiryEpochDay") && !obj.isNull("expiryEpochDay")) obj.getLong("expiryEpochDay") else null,
                        quantityOnHand = obj.optInt("quantityOnHand"),
                        createdAt = obj.optLong("createdAt"),
                        sourceType = obj.optString("sourceType"),
                        sourceId = if (obj.has("sourceId") && !obj.isNull("sourceId")) obj.getLong("sourceId") else null,
                        state = obj.optString("state")
                    )
                )
            }
        }
    }

    fun parseBatchMovements(root: JSONObject): List<InventoryBatchMovementBackup> {
        val array = root.optJSONArray("inventoryBatchMovements") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    InventoryBatchMovementBackup(
                        batchLegacyId = obj.getLong("batchLegacyId"),
                        delta = obj.optInt("delta"),
                        quantityAfter = obj.optInt("quantityAfter"),
                        type = obj.optString("type"),
                        referenceType = obj.optString("referenceType").takeIf { it.isNotBlank() },
                        referenceId = if (obj.has("referenceId") && !obj.isNull("referenceId")) obj.getLong("referenceId") else null,
                        note = obj.optString("note").takeIf { it.isNotBlank() },
                        createdAt = obj.optLong("createdAt")
                    )
                )
            }
        }
    }

    fun parseAllocations(root: JSONObject): List<BillBatchAllocationBackup> {
        val array = root.optJSONArray("billBatchAllocations") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    BillBatchAllocationBackup(
                        billLegacyId = obj.getLong("billLegacyId"),
                        batchLegacyId = obj.getLong("batchLegacyId"),
                        quantity = obj.optInt("quantity"),
                        lotNumber = obj.optString("lotNumber").takeIf { obj.has("lotNumber") && it.isNotBlank() },
                        internalRef = obj.optString("internalRef").takeIf { obj.has("internalRef") && it.isNotBlank() },
                        expiryEpochDay = if (obj.has("expiryEpochDay") && !obj.isNull("expiryEpochDay")) {
                            obj.getLong("expiryEpochDay")
                        } else {
                            null
                        }
                    )
                )
            }
        }
    }

    private fun encodeBatch(batch: com.kex.vikrsaathi.data.entity.InventoryBatch) = JSONObject().apply {
        put("legacyId", batch.id)
        put("itemLegacyId", batch.itemId)
        batch.lotNumber?.let { put("lotNumber", it) }
        put("internalRef", batch.internalRef)
        batch.expiryEpochDay?.let { put("expiryEpochDay", it) }
        put("quantityOnHand", batch.quantityOnHand)
        put("createdAt", batch.createdAt)
        put("sourceType", batch.sourceType)
        batch.sourceId?.let { put("sourceId", it) }
        put("state", batch.state)
    }

    private fun encodeBatchMovement(movement: com.kex.vikrsaathi.data.entity.InventoryBatchMovement) = JSONObject().apply {
        put("batchLegacyId", movement.batchId)
        put("delta", movement.delta)
        put("quantityAfter", movement.quantityAfter)
        put("type", movement.type)
        movement.referenceType?.let { put("referenceType", it) }
        movement.referenceId?.let { put("referenceId", it) }
        movement.note?.let { put("note", it) }
        put("createdAt", movement.createdAt)
    }

    private fun encodeAllocation(row: com.kex.vikrsaathi.data.entity.BillBatchAllocation) = JSONObject().apply {
        put("billLegacyId", row.billId)
        put("batchLegacyId", row.batchId)
        put("quantity", row.quantity)
        row.lotNumber?.let { put("lotNumber", it) }
        row.internalRef?.let { put("internalRef", it) }
        row.expiryEpochDay?.let { put("expiryEpochDay", it) }
    }

    fun selectedOperatorLegacyId(root: JSONObject): Long =
        if (root.has("selectedOperatorLegacyId")) root.optLong("selectedOperatorLegacyId") else 0L

    fun parseBills(root: JSONObject): List<BillBackup> {
        val array = root.optJSONArray("bills") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(decodeBill(array.getJSONObject(i)))
            }
        }
    }

    fun parseTemplates(root: JSONObject): List<TemplateBackup> {
        val array = root.optJSONArray("invoiceTemplates") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(decodeTemplate(array.getJSONObject(i)))
            }
        }
    }

    fun parseTemplateVersions(root: JSONObject): List<TemplateVersionBackup> {
        val array = root.optJSONArray("templateVersions") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(decodeTemplateVersion(array.getJSONObject(i)))
            }
        }
    }

    fun parseTemplateImages(root: JSONObject): List<TemplateImageBackup> {
        val array = root.optJSONArray("templateImages") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(decodeTemplateImage(array.getJSONObject(i)))
            }
        }
    }

    fun parseSettings(root: JSONObject): SettingsBackup? {
        val obj = root.optJSONObject("settings") ?: return null
        return decodeSettings(obj)
    }

    private fun encodeSettings(settings: SettingsRepository): JSONObject {
        return JSONObject().apply {
            put("shopName", settings.shopName)
            put("currencySymbol", settings.currencySymbol)
            put("defaultDiscount", settings.defaultDiscount)
            put("themeMode", settings.themeMode.name)
            put("inventoryMode", settings.inventoryMode.name)
            put("lowStockThreshold", settings.lowStockThreshold)
            put("customerBalanceEnabled", settings.customerBalanceEnabled)
            put("vendorManagementEnabled", settings.vendorManagementEnabled)
            put("orderManagementEnabled", settings.orderManagementEnabled)
            put("batchTrackingEnabled", settings.batchTrackingEnabled)
            put("warrantyEnabled", settings.warrantyEnabled)
            put("expiryAlertDays", settings.expiryAlertDays)
            put("notificationPreferences", settings.notificationPreferencesJson())
            put("invoicePrefix", settings.invoicePrefix)
            put("invoiceSuffix", settings.invoiceSuffix)
            put("invoiceSeparator", settings.invoiceSeparator)
            put("invoiceCounter", settings.invoiceCounter)
            put("invoiceCounterMinDigits", settings.invoiceCounterMinDigits)
            put("requestPrefix", settings.requestPrefix)
            put("requestSuffix", settings.requestSuffix)
            put("requestSeparator", settings.requestSeparator)
            put("requestCounter", settings.requestCounter)
            put("quotePrefix", settings.quotePrefix)
            put("quoteSuffix", settings.quoteSuffix)
            put("quoteSeparator", settings.quoteSeparator)
            put("images", JSONObject().apply {
                encodeBitmap(settings.getHeaderImage())?.let { put("header", it) }
                encodeBitmap(settings.getSignatureImage())?.let { put("signature", it) }
                encodeBitmap(settings.getShopLogoImage())?.let { put("logo", it) }
            })
        }
    }

    private fun decodeSettings(obj: JSONObject): SettingsBackup {
        val images = obj.optJSONObject("images")
        return SettingsBackup(
            shopName = obj.optString("shopName", ""),
            currencySymbol = obj.optString("currencySymbol", "₹"),
            defaultDiscount = obj.optDouble("defaultDiscount", 0.0),
            themeMode = ThemeMode.fromStored(obj.optString("themeMode", ThemeMode.SYSTEM.name)),
            inventoryMode = InventoryMode.fromStored(
                obj.optString("inventoryMode", InventoryMode.WARN.name)
            ),
            lowStockThreshold = obj.optInt("lowStockThreshold", 5),
            customerBalanceEnabled = if (obj.has("customerBalanceEnabled")) {
                obj.getBoolean("customerBalanceEnabled")
            } else {
                null
            },
            vendorManagementEnabled = if (obj.has("vendorManagementEnabled")) {
                obj.getBoolean("vendorManagementEnabled")
            } else {
                null
            },
            orderManagementEnabled = if (obj.has("orderManagementEnabled")) {
                obj.getBoolean("orderManagementEnabled")
            } else {
                null
            },
            invoicePrefix = obj.optString("invoicePrefix", ""),
            invoiceSuffix = obj.optString("invoiceSuffix", ""),
            invoiceSeparator = obj.optString("invoiceSeparator", "/"),
            invoiceCounter = obj.optInt("invoiceCounter", 1),
            invoiceCounterMinDigits = obj.optInt("invoiceCounterMinDigits", 2),
            requestPrefix = obj.optString("requestPrefix", "PR"),
            requestSuffix = obj.optString("requestSuffix", ""),
            requestSeparator = obj.optString("requestSeparator", "-"),
            requestCounter = obj.optInt("requestCounter", 1),
            quotePrefix = obj.optString("quotePrefix", "Q"),
            quoteSuffix = obj.optString("quoteSuffix", ""),
            quoteSeparator = obj.optString("quoteSeparator", "-"),
            headerImageBase64 = images?.optString("header"),
            signatureImageBase64 = images?.optString("signature"),
            logoImageBase64 = images?.optString("logo"),
            batchTrackingEnabled = if (obj.has("batchTrackingEnabled")) obj.getBoolean("batchTrackingEnabled") else null,
            warrantyEnabled = if (obj.has("warrantyEnabled")) obj.getBoolean("warrantyEnabled") else null,
            expiryAlertDays = if (obj.has("expiryAlertDays")) obj.optInt("expiryAlertDays") else null,
            notificationPreferencesJson = obj.optJSONObject("notificationPreferences")?.toString()
        )
    }

    private fun encodeCustomer(customer: Customer): JSONObject {
        return JSONObject().apply {
            put("legacyId", customer.id)
            put("name", customer.name)
            put("address1", customer.address1)
            put("address2", customer.address2)
            put("city", customer.city)
            put("state", customer.state)
            put("pincode", customer.pincode)
            put("phone", customer.phone)
            put("remarks", customer.remarks)
        }
    }

    private fun decodeCustomer(obj: JSONObject) = CustomerBackup(
        legacyId = obj.getLong("legacyId"),
        name = obj.getString("name"),
        address1 = obj.optString("address1", ""),
        address2 = obj.optString("address2", ""),
        city = obj.optString("city", ""),
        state = obj.optString("state", ""),
        pincode = obj.optString("pincode", ""),
        phone = obj.optString("phone", ""),
        remarks = obj.optString("remarks", "")
    )

    private fun encodeItem(item: Item): JSONObject {
        return JSONObject().apply {
            put("legacyId", item.id)
            put("name", item.name)
            item.barcode?.let { put("barcode", it) }
            put("mrp", item.mrp)
            put("discount", item.discount)
            item.sellingPrice?.let { put("sellingPrice", it) }
            put("remarks", item.remarks)
            put("stockQty", item.stockQty)
            item.preferredVendorId?.let { put("preferredVendorLegacyId", it) }
            put("baseUnit", item.baseUnit)
            put("purchaseUnit", item.purchaseUnit)
            put("unitsPerPurchase", item.unitsPerPurchase)
            put("trackBatches", item.trackBatches)
            put("warrantyApplies", item.warrantyApplies)
            put("warrantyPeriod", item.warrantyPeriod)
            put("warrantyUnit", item.warrantyUnit)
            put("warrantyProvider", item.warrantyProvider)
            put("warrantyProviderName", item.warrantyProviderName)
            put("warrantyTerms", item.warrantyTerms)
        }
    }

    private fun decodeItem(obj: JSONObject) = ItemBackup(
        legacyId = obj.getLong("legacyId"),
        name = obj.getString("name"),
        barcode = obj.optString("barcode").takeIf { it.isNotBlank() },
        mrp = obj.getDouble("mrp"),
        discount = obj.getDouble("discount"),
        sellingPrice = obj.optDouble("sellingPrice").takeIf { !obj.isNull("sellingPrice") },
        remarks = obj.optString("remarks", ""),
        stockQty = obj.optInt("stockQty", 0),
        preferredVendorLegacyId = if (obj.has("preferredVendorLegacyId") && !obj.isNull("preferredVendorLegacyId")) {
            obj.getLong("preferredVendorLegacyId")
        } else {
            null
        },
        baseUnit = obj.optString("baseUnit", ""),
        purchaseUnit = obj.optString("purchaseUnit", ""),
        unitsPerPurchase = obj.optInt("unitsPerPurchase", 1),
        trackBatches = obj.optBoolean("trackBatches", false),
        warrantyApplies = obj.optBoolean("warrantyApplies", false),
        warrantyPeriod = obj.optInt("warrantyPeriod", 0),
        warrantyUnit = obj.optString("warrantyUnit", ""),
        warrantyProvider = obj.optString("warrantyProvider", ""),
        warrantyProviderName = obj.optString("warrantyProviderName", ""),
        warrantyTerms = obj.optString("warrantyTerms", "")
    )

    private fun encodeStockMovement(
        movement: com.kex.vikrsaathi.data.entity.StockMovement
    ): JSONObject {
        return JSONObject().apply {
            put("itemLegacyId", movement.itemId)
            put("delta", movement.delta)
            put("quantityAfter", movement.quantityAfter)
            put("type", movement.type)
            movement.referenceType?.let { put("referenceType", it) }
            movement.referenceId?.let { put("referenceId", it) }
            movement.note?.let { put("note", it) }
            put("createdAt", movement.createdAt)
        }
    }

    private fun decodeStockMovement(obj: JSONObject) = StockMovementBackup(
        itemLegacyId = obj.getLong("itemLegacyId"),
        delta = obj.getInt("delta"),
        quantityAfter = obj.getInt("quantityAfter"),
        type = obj.getString("type"),
        referenceType = obj.optString("referenceType").takeIf { it.isNotBlank() },
        referenceId = if (obj.has("referenceId") && !obj.isNull("referenceId")) {
            obj.getLong("referenceId")
        } else {
            null
        },
        note = obj.optString("note").takeIf { it.isNotBlank() },
        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
    )

    private fun encodeBill(
        details: BillWithDetails,
        versions: List<com.kex.vikrsaathi.data.model.BillVersionDetails>
    ): JSONObject {
        return JSONObject().apply {
            put("legacyId", details.bill.id)
            put("billNumber", details.bill.billNumber)
            put("invoiceCounter", details.bill.invoiceCounter)
            details.bill.customerId?.let { put("customerLegacyId", it) }
            put("total", details.bill.total)
            put("roundOff", details.bill.roundOff)
            put("date", details.bill.date)
            put("paymentStatus", details.bill.paymentStatus)
            put("paymentLabel", details.bill.paymentLabel)
            put("payments", JSONArray().apply {
                details.payments.forEach { payment ->
                    put(JSONObject().apply {
                        put("method", payment.method)
                        put("amountPaise", payment.amountPaise)
                    })
                }
            })
            put("items", JSONArray().apply {
                details.items.forEach { item -> put(encodeBillItem(item)) }
            })
            if (versions.isNotEmpty()) {
                put("versions", JSONArray().apply { versions.forEach { put(encodeVersion(it)) } })
            }
        }
    }

    private fun encodeVersion(details: com.kex.vikrsaathi.data.model.BillVersionDetails): JSONObject {
        val version = details.version
        return JSONObject().apply {
            put("versionNumber", version.versionNumber)
            put("createdAt", version.createdAt)
            put("saleDate", version.saleDate)
            put("actorLabel", version.actorLabel)
            version.customerId?.let { put("customerLegacyId", it) }
            put("customerName", version.customerName)
            put("customerPhone", version.customerPhone)
            put("customerAddress", version.customerAddress)
            put("total", version.total)
            put("roundOff", version.roundOff)
            put("paymentStatus", version.paymentStatus)
            put("paymentLabel", version.paymentLabel)
            put("changeSummary", version.changeSummary)
            put("items", JSONArray().apply {
                details.items.forEach { item ->
                    put(JSONObject().apply {
                        item.itemId?.let { put("itemLegacyId", it) }
                        put("itemName", item.itemName)
                        put("quantity", item.quantity)
                        put("mrp", item.mrp)
                        put("discount", item.discount)
                        put("finalPrice", item.finalPrice)
                        put("roundOff", item.roundOff)
                        item.unitCostPaise?.let { put("unitCostPaise", it) }
                        putWarranty(this, item.warrantyPeriod, item.warrantyUnit, item.warrantyProvider, item.warrantyProviderName, item.warrantyTerms, item.warrantyStartEpochDay, item.warrantyEndEpochDay)
                    })
                }
            })
            put("payments", JSONArray().apply {
                details.payments.forEach { payment ->
                    put(JSONObject().apply {
                        put("method", payment.method)
                        put("amountPaise", payment.amountPaise)
                    })
                }
            })
        }
    }

    private fun encodeCostLot(lot: com.kex.vikrsaathi.data.entity.ItemCostLot): JSONObject {
        return JSONObject().apply {
            put("itemLegacyId", lot.itemId)
            put("sourceType", lot.sourceType)
            put("sourceId", lot.sourceId)
            put("qtyReceived", lot.qtyReceived)
            put("qtyRemaining", lot.qtyRemaining)
            put("unitCostPaise", lot.unitCostPaise)
            put("receivedAt", lot.receivedAt)
        }
    }

    private fun encodeBillItem(item: BillItem): JSONObject {
        return JSONObject().apply {
            put("itemLegacyId", item.itemId)
            put("itemName", item.itemName)
            put("quantity", item.quantity)
            put("mrp", item.mrp)
            put("discount", item.discount)
            put("finalPrice", item.finalPrice)
            put("roundOff", item.roundOff)
            item.unitCostPaise?.let { put("unitCostPaise", it) }
            putWarranty(this, item.warrantyPeriod, item.warrantyUnit, item.warrantyProvider, item.warrantyProviderName, item.warrantyTerms, item.warrantyStartEpochDay, item.warrantyEndEpochDay)
        }
    }

    private fun putWarranty(
        obj: JSONObject,
        period: Int?,
        unit: String?,
        provider: String?,
        providerName: String?,
        terms: String?,
        startEpochDay: Long?,
        endEpochDay: Long?
    ) {
        if (period == null) return
        obj.put("warrantyPeriod", period)
        obj.put("warrantyUnit", unit)
        obj.put("warrantyProvider", provider)
        obj.put("warrantyProviderName", providerName)
        obj.put("warrantyTerms", terms)
        startEpochDay?.let { obj.put("warrantyStartEpochDay", it) }
        endEpochDay?.let { obj.put("warrantyEndEpochDay", it) }
    }

    private fun decodeWarranty(obj: JSONObject): WarrantyPolicy.Snapshot? {
        if (!obj.has("warrantyPeriod")) return null
        return WarrantyPolicy.fromStored(
            period = obj.optInt("warrantyPeriod"),
            unit = obj.optString("warrantyUnit"),
            provider = obj.optString("warrantyProvider"),
            providerName = obj.optString("warrantyProviderName"),
            terms = obj.optString("warrantyTerms"),
            startEpochDay = obj.optLong("warrantyStartEpochDay").takeIf { obj.has("warrantyStartEpochDay") },
            endEpochDay = obj.optLong("warrantyEndEpochDay").takeIf { obj.has("warrantyEndEpochDay") }
        )
    }

    private fun decodeBill(obj: JSONObject): BillBackup {
        val items = mutableListOf<BillLineItemBackup>()
        val itemsArray = obj.optJSONArray("items") ?: JSONArray()
        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.getJSONObject(i)
            items.add(
                BillLineItemBackup(
                    itemLegacyId = itemObj.optLong("itemLegacyId").takeIf { itemObj.has("itemLegacyId") },
                    itemName = itemObj.getString("itemName"),
                    quantity = itemObj.getInt("quantity"),
                    mrp = itemObj.getDouble("mrp"),
                    discount = itemObj.getDouble("discount"),
                    finalPrice = itemObj.getDouble("finalPrice"),
                    roundOff = itemObj.optDouble("roundOff", 0.0),
                    unitCostPaise = if (itemObj.has("unitCostPaise") && !itemObj.isNull("unitCostPaise")) {
                        itemObj.getLong("unitCostPaise")
                    } else {
                        null
                    },
                    warranty = decodeWarranty(itemObj)
                )
            )
        }
        return BillBackup(
            legacyId = obj.getLong("legacyId"),
            billNumber = obj.getString("billNumber"),
            invoiceCounter = obj.optInt("invoiceCounter", 0),
            customerLegacyId = obj.optLong("customerLegacyId").takeIf { obj.has("customerLegacyId") },
            total = obj.getDouble("total"),
            roundOff = obj.optDouble("roundOff", 0.0),
            date = obj.getLong("date"),
            items = items,
            paymentStatus = obj.optString("paymentStatus", com.kex.vikrsaathi.domain.finance.BillPaymentStatus.LEGACY),
            paymentLabel = obj.optString("paymentLabel", com.kex.vikrsaathi.domain.finance.BillPaymentStatus.LABEL_LEGACY),
            payments = decodePortions(obj.optJSONArray("payments")),
            versions = decodeVersions(obj.optJSONArray("versions"))
        )
    }

    private fun decodeVersions(array: JSONArray?): List<BillVersionBackup> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val items = mutableListOf<BillLineItemBackup>()
                val itemArray = obj.optJSONArray("items") ?: JSONArray()
                for (n in 0 until itemArray.length()) {
                    val itemObj = itemArray.getJSONObject(n)
                    items += BillLineItemBackup(
                        itemLegacyId = itemObj.optLong("itemLegacyId").takeIf { itemObj.has("itemLegacyId") },
                        itemName = itemObj.optString("itemName"),
                        quantity = itemObj.optInt("quantity"),
                        mrp = itemObj.optDouble("mrp"),
                        discount = itemObj.optDouble("discount"),
                        finalPrice = itemObj.optDouble("finalPrice"),
                        roundOff = itemObj.optDouble("roundOff", 0.0),
                        unitCostPaise = if (itemObj.has("unitCostPaise") && !itemObj.isNull("unitCostPaise")) {
                            itemObj.getLong("unitCostPaise")
                        } else {
                            null
                        },
                        warranty = decodeWarranty(itemObj)
                    )
                }
                add(
                    BillVersionBackup(
                        versionNumber = obj.optInt("versionNumber"),
                        createdAt = obj.optLong("createdAt"),
                        saleDate = obj.optLong("saleDate"),
                        actorLabel = obj.optString("actorLabel", com.kex.vikrsaathi.domain.bill.OperatorLabels.THIS_DEVICE),
                        customerLegacyId = obj.optLong("customerLegacyId").takeIf { obj.has("customerLegacyId") },
                        customerName = obj.optString("customerName"),
                        customerPhone = obj.optString("customerPhone"),
                        customerAddress = obj.optString("customerAddress"),
                        total = obj.optDouble("total"),
                        roundOff = obj.optDouble("roundOff"),
                        paymentStatus = obj.optString("paymentStatus"),
                        paymentLabel = obj.optString("paymentLabel"),
                        changeSummary = obj.optString("changeSummary"),
                        items = items,
                        payments = decodePortions(obj.optJSONArray("payments"))
                    )
                )
            }
        }
    }

    private fun decodePortions(array: org.json.JSONArray?): List<com.kex.vikrsaathi.domain.finance.PaymentPortion> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    com.kex.vikrsaathi.domain.finance.PaymentPortion(
                        method = obj.getString("method"),
                        amountPaise = obj.getLong("amountPaise")
                    )
                )
            }
        }
    }

    private fun encodeTemplate(entity: InvoiceTemplateEntity): JSONObject {
        return JSONObject().apply {
            put("legacyId", entity.id)
            put("name", entity.name)
            put("isDefault", entity.isDefault)
            put("templateJson", entity.elementsJson)
        }
    }

    private fun decodeTemplate(obj: JSONObject) = TemplateBackup(
        legacyId = obj.getLong("legacyId"),
        name = obj.getString("name"),
        isDefault = obj.optBoolean("isDefault", false),
        templateJson = obj.getString("templateJson")
    )

    private fun encodeTemplateVersion(entity: InvoiceTemplateVersionEntity): JSONObject {
        return JSONObject().apply {
            put("templateLegacyId", entity.templateId)
            put("versionNumber", entity.versionNumber)
            put("snapshotJson", entity.snapshotJson)
            put("savedAt", entity.savedAt)
        }
    }

    private fun decodeTemplateVersion(obj: JSONObject) = TemplateVersionBackup(
        templateLegacyId = obj.getLong("templateLegacyId"),
        versionNumber = obj.getInt("versionNumber"),
        snapshotJson = obj.getString("snapshotJson"),
        savedAt = obj.getLong("savedAt")
    )

    private fun encodeTemplateImage(image: TemplateImageBackup): JSONObject {
        return JSONObject().apply {
            put("templateLegacyId", image.templateLegacyId)
            put("elementId", image.elementId)
            put("base64", image.base64)
        }
    }

    private fun decodeTemplateImage(obj: JSONObject) = TemplateImageBackup(
        templateLegacyId = obj.getLong("templateLegacyId"),
        elementId = obj.getString("elementId"),
        base64 = obj.getString("base64")
    )
}

data class SettingsBackup(
    val shopName: String,
    val currencySymbol: String,
    val defaultDiscount: Double,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val inventoryMode: InventoryMode = InventoryMode.WARN,
    val lowStockThreshold: Int = 5,
    val customerBalanceEnabled: Boolean? = null,
    val vendorManagementEnabled: Boolean? = null,
    val orderManagementEnabled: Boolean? = null,
    val invoicePrefix: String,
    val invoiceSuffix: String,
    val invoiceSeparator: String,
    val invoiceCounter: Int,
    val invoiceCounterMinDigits: Int,
    val requestPrefix: String = "PR",
    val requestSuffix: String = "",
    val requestSeparator: String = "-",
    val requestCounter: Int = 1,
    val quotePrefix: String = "Q",
    val quoteSuffix: String = "",
    val quoteSeparator: String = "-",
    val headerImageBase64: String?,
    val signatureImageBase64: String?,
    val logoImageBase64: String?,
    val batchTrackingEnabled: Boolean? = null,
    val warrantyEnabled: Boolean? = null,
    val expiryAlertDays: Int? = null,
    val notificationPreferencesJson: String? = null
)

data class CustomerBackup(
    val legacyId: Long,
    val name: String,
    val address1: String,
    val address2: String,
    val city: String,
    val state: String,
    val pincode: String,
    val phone: String,
    val remarks: String
)

data class ItemBackup(
    val legacyId: Long,
    val name: String,
    val barcode: String?,
    val mrp: Double,
    val discount: Double,
    val sellingPrice: Double?,
    val remarks: String,
    val stockQty: Int = 0,
    val preferredVendorLegacyId: Long? = null,
    val baseUnit: String = "",
    val purchaseUnit: String = "",
    val unitsPerPurchase: Int = 1,
    val trackBatches: Boolean = false,
    val warrantyApplies: Boolean = false,
    val warrantyPeriod: Int = 0,
    val warrantyUnit: String = "",
    val warrantyProvider: String = "",
    val warrantyProviderName: String = "",
    val warrantyTerms: String = ""
)

data class StockMovementBackup(
    val itemLegacyId: Long,
    val delta: Int,
    val quantityAfter: Int,
    val type: String,
    val referenceType: String?,
    val referenceId: Long?,
    val note: String?,
    val createdAt: Long
)

data class BillBackup(
    val legacyId: Long,
    val billNumber: String,
    val invoiceCounter: Int,
    val customerLegacyId: Long?,
    val total: Double,
    val roundOff: Double = 0.0,
    val date: Long,
    val items: List<BillLineItemBackup>,
    val paymentStatus: String = com.kex.vikrsaathi.domain.finance.BillPaymentStatus.LEGACY,
    val paymentLabel: String = com.kex.vikrsaathi.domain.finance.BillPaymentStatus.LABEL_LEGACY,
    val payments: List<com.kex.vikrsaathi.domain.finance.PaymentPortion> = emptyList(),
    val versions: List<BillVersionBackup> = emptyList()
)

data class BillLineItemBackup(
    val itemLegacyId: Long?,
    val itemName: String,
    val quantity: Int,
    val mrp: Double,
    val discount: Double,
    val finalPrice: Double,
    val roundOff: Double = 0.0,
    val unitCostPaise: Long? = null,
    val warranty: WarrantyPolicy.Snapshot? = null
) {
    fun toLineItem(mappedItemId: Long?): BillLineItem = BillLineItem(
        itemId = mappedItemId,
        name = itemName,
        mrp = mrp,
        discount = discount,
        quantity = quantity,
        roundOff = roundOff,
        unitCostPaise = unitCostPaise,
        warranty = warranty
    )
}

data class BillVersionBackup(
    val versionNumber: Int,
    val createdAt: Long,
    val saleDate: Long,
    val actorLabel: String,
    val customerLegacyId: Long?,
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val total: Double,
    val roundOff: Double,
    val paymentStatus: String,
    val paymentLabel: String,
    val changeSummary: String,
    val items: List<BillLineItemBackup>,
    val payments: List<com.kex.vikrsaathi.domain.finance.PaymentPortion>
)

data class OperatorBackup(
    val legacyId: Long,
    val name: String,
    val createdAt: Long
)

data class InventoryBatchBackup(
    val legacyId: Long,
    val itemLegacyId: Long,
    val lotNumber: String?,
    val internalRef: String,
    val expiryEpochDay: Long?,
    val quantityOnHand: Int,
    val createdAt: Long,
    val sourceType: String,
    val sourceId: Long?,
    val state: String
)

data class InventoryBatchMovementBackup(
    val batchLegacyId: Long,
    val delta: Int,
    val quantityAfter: Int,
    val type: String,
    val referenceType: String?,
    val referenceId: Long?,
    val note: String?,
    val createdAt: Long
)

data class BillBatchAllocationBackup(
    val billLegacyId: Long,
    val batchLegacyId: Long,
    val quantity: Int,
    val lotNumber: String? = null,
    val internalRef: String? = null,
    val expiryEpochDay: Long? = null
)

data class ItemCostLotBackup(
    val itemLegacyId: Long,
    val sourceType: String,
    val sourceId: Long,
    val qtyReceived: Int,
    val qtyRemaining: Int,
    val unitCostPaise: Long,
    val receivedAt: Long
)

data class TemplateBackup(
    val legacyId: Long,
    val name: String,
    val isDefault: Boolean,
    val templateJson: String
)

data class TemplateVersionBackup(
    val templateLegacyId: Long,
    val versionNumber: Int,
    val snapshotJson: String,
    val savedAt: Long
)

data class TemplateImageBackup(
    val templateLegacyId: Long,
    val elementId: String,
    val base64: String
)
