package com.kex.vikrsaathi.domain.template

import android.graphics.Bitmap
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.data.model.template.DataBindingKey
import com.kex.vikrsaathi.util.NumberToWords
import com.kex.vikrsaathi.util.PriceCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TemplateRenderContext(
    val bill: BillWithDetails,
    val shopName: String,
    val currencySymbol: String,
    val headerImage: Bitmap?,
    val signatureImage: Bitmap?,
    val shopLogoImage: Bitmap? = null,
    val staticImages: Map<String, Bitmap> = emptyMap(),
    val imageRenderScale: Float = 1f
)

data class TableRowData(
    val values: Map<String, String>
)

class TemplateBindingResolver {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    fun resolveText(key: DataBindingKey, context: TemplateRenderContext): String {
        return when (key) {
            DataBindingKey.SHOP_NAME -> context.shopName
            DataBindingKey.BILL_NUMBER -> context.bill.bill.billNumber
            DataBindingKey.BILL_DATE -> dateFormat.format(Date(context.bill.bill.date))
            DataBindingKey.CUSTOMER_NAME -> context.bill.customer?.name ?: "-"
            DataBindingKey.CUSTOMER_ADDRESS -> context.bill.customer?.formattedAddress()?.ifBlank { "-" } ?: "-"
            DataBindingKey.CUSTOMER_PHONE -> context.bill.customer?.phone?.ifBlank { "-" } ?: "-"
            DataBindingKey.BILL_TOTAL -> PriceCalculator.formatAmount(context.bill.bill.total, context.currencySymbol)
            DataBindingKey.BILL_TOTAL_WORDS -> NumberToWords.convert(context.bill.bill.total)
            DataBindingKey.HEADER_IMAGE, DataBindingKey.SIGNATURE_IMAGE, DataBindingKey.SHOP_LOGO,
            DataBindingKey.BILL_ITEMS -> ""
        }
    }

    fun resolveImage(key: DataBindingKey, context: TemplateRenderContext): Bitmap? {
        return when (key) {
            DataBindingKey.HEADER_IMAGE -> context.headerImage
            DataBindingKey.SIGNATURE_IMAGE -> context.signatureImage
            DataBindingKey.SHOP_LOGO -> context.shopLogoImage
            else -> null
        }
    }

    fun resolveTableRows(context: TemplateRenderContext): List<TableRowData> {
        return context.bill.items.mapIndexed { index, item ->
            val line = BillLineItem(
                itemId = item.itemId,
                name = item.itemName,
                mrp = item.mrp,
                discount = item.discount,
                quantity = item.quantity
            )
            val name = line.name
            TableRowData(
                mapOf(
                    "sl" to (index + 1).toString(),
                    "name" to name,
                    "quantity" to line.quantity.toString(),
                    "mrp" to PriceCalculator.formatAmount(line.mrp, context.currencySymbol),
                    "discount" to String.format(Locale.getDefault(), "%.1f", line.discount),
                    "lineTotal" to PriceCalculator.formatAmount(line.lineTotal, context.currencySymbol)
                )
            )
        }
    }

    fun parseBindingKey(raw: String?): DataBindingKey? {
        if (raw.isNullOrBlank()) return null
        return runCatching { DataBindingKey.valueOf(raw) }.getOrNull()
    }
}
