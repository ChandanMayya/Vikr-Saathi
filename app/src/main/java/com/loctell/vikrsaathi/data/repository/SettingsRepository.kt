package com.loctell.vikrsaathi.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream

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

    fun getHeaderImage(): Bitmap? = loadImage(HEADER_FILE)

    fun getSignatureImage(): Bitmap? = loadImage(SIGNATURE_FILE)

    fun saveHeaderImage(bitmap: Bitmap) = saveImage(bitmap, HEADER_FILE)

    fun saveSignatureImage(bitmap: Bitmap) = saveImage(bitmap, SIGNATURE_FILE)

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
        return BitmapFactory.decodeFile(file.absolutePath)
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
        private const val DEFAULT_SHOP_NAME = "Vikr Saathi Shop"
        private const val DEFAULT_CURRENCY = "₹"
        private const val HEADER_FILE = "header.png"
        private const val SIGNATURE_FILE = "signature.png"
    }
}
