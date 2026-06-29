package com.kex.vikrsaathi.domain.template

import android.content.Context
import android.graphics.Bitmap
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.util.TemplateImageStore
import com.kex.vikrsaathi.util.PdfRenderQuality

object TemplateContextFactory {

    fun create(
        context: Context,
        template: InvoiceTemplate,
        bill: com.kex.vikrsaathi.data.model.BillWithDetails,
        shopName: String,
        currencySymbol: String,
        headerImage: Bitmap?,
        signatureImage: Bitmap?,
        shopLogoImage: Bitmap?,
        imageRenderScale: Float = 1f
    ): TemplateRenderContext {
        return TemplateRenderContext(
            bill = bill,
            shopName = shopName,
            currencySymbol = currencySymbol,
            headerImage = headerImage,
            signatureImage = signatureImage,
            shopLogoImage = shopLogoImage,
            staticImages = TemplateImageStore.loadForTemplate(context, template),
            imageRenderScale = imageRenderScale
        )
    }

    fun createForPdf(
        context: Context,
        template: InvoiceTemplate,
        bill: com.kex.vikrsaathi.data.model.BillWithDetails,
        shopName: String,
        currencySymbol: String,
        headerImage: Bitmap?,
        signatureImage: Bitmap?,
        shopLogoImage: Bitmap?
    ): TemplateRenderContext = create(
        context = context,
        template = template,
        bill = bill,
        shopName = shopName,
        currencySymbol = currencySymbol,
        headerImage = headerImage,
        signatureImage = signatureImage,
        shopLogoImage = shopLogoImage,
        imageRenderScale = PdfRenderQuality.IMAGE_SCALE
    )
}
