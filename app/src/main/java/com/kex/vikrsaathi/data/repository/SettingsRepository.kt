package com.kex.vikrsaathi.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import com.kex.vikrsaathi.util.InventoryMode
import com.kex.vikrsaathi.util.ThemeMode

/**
 * Persists shop-level settings using SharedPreferences and local image files.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val filesDir = File(context.filesDir, "settings_images").apply { mkdirs() }

    var shopName: String
        get() = prefs.getString(KEY_SHOP_NAME, DEFAULT_SHOP_NAME) ?: DEFAULT_SHOP_NAME
        set(value) = prefs.edit().putString(KEY_SHOP_NAME, value).apply()

    var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY, DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY
        set(value) = prefs.edit().putString(KEY_CURRENCY, value).apply()

    var defaultDiscount: Double
        get() = prefs.getFloat(KEY_DEFAULT_DISCOUNT, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_DEFAULT_DISCOUNT, value.toFloat()).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.fromStored(prefs.getString(KEY_THEME_MODE, null))
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var inventoryMode: InventoryMode
        get() = InventoryMode.fromStored(prefs.getString(KEY_INVENTORY_MODE, null))
        set(value) = prefs.edit().putString(KEY_INVENTORY_MODE, value.name).apply()

    var lowStockThreshold: Int
        get() = prefs.getInt(KEY_LOW_STOCK_THRESHOLD, DEFAULT_LOW_STOCK_THRESHOLD).coerceAtLeast(0)
        set(value) = prefs.edit().putInt(KEY_LOW_STOCK_THRESHOLD, value.coerceAtLeast(0)).apply()

    /** When false, payment and ledger screens are hidden. Stored rows are kept. */
    var customerBalanceEnabled: Boolean
        get() = prefs.getBoolean(KEY_CUSTOMER_BALANCE, true)
        set(value) = prefs.edit().putBoolean(KEY_CUSTOMER_BALANCE, value).apply()

    /** When false, vendor and purchase screens are hidden. Stored rows are kept. */
    var vendorManagementEnabled: Boolean
        get() = prefs.getBoolean(KEY_VENDOR_MANAGEMENT, true)
        set(value) = prefs.edit().putBoolean(KEY_VENDOR_MANAGEMENT, value).apply()

    /** When false, the dashboard Orders button is hidden. Purchase records are kept. */
    var orderManagementEnabled: Boolean
        get() = prefs.getBoolean(KEY_ORDER_MANAGEMENT, true)
        set(value) = prefs.edit().putBoolean(KEY_ORDER_MANAGEMENT, value).apply()

    var invoicePrefix: String
        get() = prefs.getString(KEY_INVOICE_PREFIX, "") ?: ""
        set(value) = prefs.edit().putString(KEY_INVOICE_PREFIX, value).apply()

    var invoiceSuffix: String
        get() = prefs.getString(KEY_INVOICE_SUFFIX, "") ?: ""
        set(value) = prefs.edit().putString(KEY_INVOICE_SUFFIX, value).apply()

    var invoiceSeparator: String
        get() = prefs.getString(KEY_INVOICE_SEPARATOR, "/") ?: "/"
        set(value) = prefs.edit().putString(KEY_INVOICE_SEPARATOR, value).apply()

    var invoiceCounter: Int
        get() = prefs.getInt(KEY_INVOICE_COUNTER, 1)
        set(value) = prefs.edit().putInt(KEY_INVOICE_COUNTER, value.coerceAtLeast(1)).apply()

    var invoiceCounterMinDigits: Int
        get() = prefs.getInt(KEY_INVOICE_COUNTER_DIGITS, 2).coerceIn(0, 6)
        set(value) = prefs.edit().putInt(KEY_INVOICE_COUNTER_DIGITS, value.coerceIn(0, 6)).apply()

    var requestPrefix: String
        get() = prefs.getString(KEY_REQUEST_PREFIX, "PR") ?: "PR"
        set(value) = prefs.edit().putString(KEY_REQUEST_PREFIX, value).apply()

    var requestSuffix: String
        get() = prefs.getString(KEY_REQUEST_SUFFIX, "") ?: ""
        set(value) = prefs.edit().putString(KEY_REQUEST_SUFFIX, value).apply()

    var requestSeparator: String
        get() = prefs.getString(KEY_REQUEST_SEPARATOR, "-") ?: "-"
        set(value) = prefs.edit().putString(KEY_REQUEST_SEPARATOR, value).apply()

    var requestCounter: Int
        get() = prefs.getInt(KEY_REQUEST_COUNTER, 1).coerceAtLeast(1)
        set(value) = prefs.edit().putInt(KEY_REQUEST_COUNTER, value.coerceAtLeast(1)).apply()

    var requestCounterMinDigits: Int
        get() = prefs.getInt(KEY_REQUEST_COUNTER_DIGITS, 3).coerceIn(0, 6)
        set(value) = prefs.edit().putInt(KEY_REQUEST_COUNTER_DIGITS, value.coerceIn(0, 6)).apply()

    var quotePrefix: String
        get() = prefs.getString(KEY_QUOTE_PREFIX, "Q") ?: "Q"
        set(value) = prefs.edit().putString(KEY_QUOTE_PREFIX, value).apply()

    var quoteSuffix: String
        get() = prefs.getString(KEY_QUOTE_SUFFIX, "") ?: ""
        set(value) = prefs.edit().putString(KEY_QUOTE_SUFFIX, value).apply()

    var quoteSeparator: String
        get() = prefs.getString(KEY_QUOTE_SEPARATOR, "-") ?: "-"
        set(value) = prefs.edit().putString(KEY_QUOTE_SEPARATOR, value).apply()

    fun getHeaderImage(): Bitmap? = loadImage(HEADER_FILE)

    fun getSignatureImage(): Bitmap? = loadImage(SIGNATURE_FILE)

    fun getShopLogoImage(): Bitmap? = loadImage(LOGO_FILE)

    fun saveHeaderImage(bitmap: Bitmap) = saveImage(bitmap, HEADER_FILE)

    fun saveSignatureImage(bitmap: Bitmap) = saveImage(bitmap, SIGNATURE_FILE)

    fun saveShopLogoImage(bitmap: Bitmap) = saveImage(bitmap, LOGO_FILE)

    fun resetToDefaults(context: Context) {
        resetShopBrandingToDefaults(context)
        resetInvoiceConfigToDefaults()
    }

    fun resetShopBrandingToDefaults(context: Context) {
        shopName = DEFAULT_SHOP_NAME
        currencySymbol = DEFAULT_CURRENCY
        defaultDiscount = 0.0
        inventoryMode = InventoryMode.WARN
        lowStockThreshold = DEFAULT_LOW_STOCK_THRESHOLD
        customerBalanceEnabled = true
        vendorManagementEnabled = true
        orderManagementEnabled = true
        File(filesDir, HEADER_FILE).delete()
        File(filesDir, SIGNATURE_FILE).delete()
        File(filesDir, LOGO_FILE).delete()
        copyAssetHeaderIfNeeded(context)
    }

    fun resetInvoiceConfigToDefaults() {
        invoicePrefix = ""
        invoiceSuffix = ""
        invoiceSeparator = "/"
        invoiceCounter = 1
        invoiceCounterMinDigits = 2
        requestPrefix = "PR"
        requestSuffix = ""
        requestSeparator = "-"
        requestCounter = 1
        requestCounterMinDigits = 3
        quotePrefix = "Q"
        quoteSuffix = ""
        quoteSeparator = "-"
    }

    fun copyAssetHeaderIfNeeded(context: Context) {
        if (File(filesDir, HEADER_FILE).exists()) return
        try {
            context.assets.open("default_header.png").use { input ->
                BitmapFactory.decodeStream(input)?.let { saveHeaderImage(it) }
            }
        } catch (_: Exception) {
            saveHeaderImage(createDefaultHeaderBitmap(context))
        }
    }

    private fun createDefaultHeaderBitmap(context: Context): Bitmap {
        val width = 800
        val height = 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F57C00"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            shopName,
            width / 2f,
            height / 2f + 16f,
            paint
        )
        return bitmap
    }

    private fun loadImage(fileName: String): Bitmap? {
        val file = File(filesDir, fileName)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun saveImage(bitmap: Bitmap, fileName: String) {
        FileOutputStream(File(filesDir, fileName)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    companion object {
        private const val PREFS_NAME = "vikr_saathi_settings"
        private const val KEY_SHOP_NAME = "shop_name"
        private const val KEY_CURRENCY = "currency_symbol"
        private const val KEY_DEFAULT_DISCOUNT = "default_discount"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_INVENTORY_MODE = "inventory_mode"
        private const val KEY_LOW_STOCK_THRESHOLD = "low_stock_threshold"
        private const val KEY_CUSTOMER_BALANCE = "customer_balance_enabled"
        private const val KEY_VENDOR_MANAGEMENT = "vendor_management_enabled"
        private const val KEY_ORDER_MANAGEMENT = "order_management_enabled"
        private const val KEY_INVOICE_PREFIX = "invoice_prefix"
        private const val KEY_INVOICE_SUFFIX = "invoice_suffix"
        private const val KEY_INVOICE_SEPARATOR = "invoice_separator"
        private const val KEY_INVOICE_COUNTER = "invoice_counter"
        private const val KEY_INVOICE_COUNTER_DIGITS = "invoice_counter_digits"
        private const val KEY_REQUEST_PREFIX = "request_prefix"
        private const val KEY_REQUEST_SUFFIX = "request_suffix"
        private const val KEY_REQUEST_SEPARATOR = "request_separator"
        private const val KEY_REQUEST_COUNTER = "request_counter"
        private const val KEY_REQUEST_COUNTER_DIGITS = "request_counter_digits"
        private const val KEY_QUOTE_PREFIX = "quote_prefix"
        private const val KEY_QUOTE_SUFFIX = "quote_suffix"
        private const val KEY_QUOTE_SEPARATOR = "quote_separator"
        private const val DEFAULT_SHOP_NAME = "Vikr Saathi Shop"
        private const val DEFAULT_CURRENCY = "₹"
        private const val DEFAULT_LOW_STOCK_THRESHOLD = 5
        private const val HEADER_FILE = "header.png"
        private const val SIGNATURE_FILE = "signature.png"
        private const val LOGO_FILE = "logo.png"
    }
}
