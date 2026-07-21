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
import com.kex.vikrsaathi.util.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object BackupJsonCodec {

    const val SCHEMA_VERSION = 1

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
        templateImages: List<TemplateImageBackup>
    ): String {
        val includes = buildList {
            if (options.includeCustomers) add("customers")
            if (options.includeItems) add("items")
            if (options.includeSales) add("sales")
            if (options.includeSettings) add("settings")
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
            if (options.includeCustomers) {
                put("customers", JSONArray().apply { customers.forEach { put(encodeCustomer(it)) } })
            }
            if (options.includeItems) {
                put("items", JSONArray().apply { items.forEach { put(encodeItem(it)) } })
            }
            if (options.includeSales) {
                put("bills", JSONArray().apply { bills.forEach { put(encodeBill(it)) } })
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
            put("invoicePrefix", settings.invoicePrefix)
            put("invoiceSuffix", settings.invoiceSuffix)
            put("invoiceSeparator", settings.invoiceSeparator)
            put("invoiceCounter", settings.invoiceCounter)
            put("invoiceCounterMinDigits", settings.invoiceCounterMinDigits)
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
            invoicePrefix = obj.optString("invoicePrefix", ""),
            invoiceSuffix = obj.optString("invoiceSuffix", ""),
            invoiceSeparator = obj.optString("invoiceSeparator", "/"),
            invoiceCounter = obj.optInt("invoiceCounter", 1),
            invoiceCounterMinDigits = obj.optInt("invoiceCounterMinDigits", 2),
            headerImageBase64 = images?.optString("header"),
            signatureImageBase64 = images?.optString("signature"),
            logoImageBase64 = images?.optString("logo")
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
        }
    }

    private fun decodeItem(obj: JSONObject) = ItemBackup(
        legacyId = obj.getLong("legacyId"),
        name = obj.getString("name"),
        barcode = obj.optString("barcode").takeIf { it.isNotBlank() },
        mrp = obj.getDouble("mrp"),
        discount = obj.getDouble("discount"),
        sellingPrice = obj.optDouble("sellingPrice").takeIf { !obj.isNull("sellingPrice") },
        remarks = obj.optString("remarks", "")
    )

    private fun encodeBill(details: BillWithDetails): JSONObject {
        return JSONObject().apply {
            put("legacyId", details.bill.id)
            put("billNumber", details.bill.billNumber)
            put("invoiceCounter", details.bill.invoiceCounter)
            details.bill.customerId?.let { put("customerLegacyId", it) }
            put("total", details.bill.total)
            put("date", details.bill.date)
            put("items", JSONArray().apply {
                details.items.forEach { item -> put(encodeBillItem(item)) }
            })
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
        }
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
                    finalPrice = itemObj.getDouble("finalPrice")
                )
            )
        }
        return BillBackup(
            legacyId = obj.getLong("legacyId"),
            billNumber = obj.getString("billNumber"),
            invoiceCounter = obj.optInt("invoiceCounter", 0),
            customerLegacyId = obj.optLong("customerLegacyId").takeIf { obj.has("customerLegacyId") },
            total = obj.getDouble("total"),
            date = obj.getLong("date"),
            items = items
        )
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
    val invoicePrefix: String,
    val invoiceSuffix: String,
    val invoiceSeparator: String,
    val invoiceCounter: Int,
    val invoiceCounterMinDigits: Int,
    val headerImageBase64: String?,
    val signatureImageBase64: String?,
    val logoImageBase64: String?
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
    val remarks: String
)

data class BillBackup(
    val legacyId: Long,
    val billNumber: String,
    val invoiceCounter: Int,
    val customerLegacyId: Long?,
    val total: Double,
    val date: Long,
    val items: List<BillLineItemBackup>
)

data class BillLineItemBackup(
    val itemLegacyId: Long?,
    val itemName: String,
    val quantity: Int,
    val mrp: Double,
    val discount: Double,
    val finalPrice: Double
) {
    fun toLineItem(mappedItemId: Long?): BillLineItem = BillLineItem(
        itemId = mappedItemId,
        name = itemName,
        mrp = mrp,
        discount = discount,
        quantity = quantity
    )
}

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
