package com.loctell.vikrsaathi.domain.template

import android.content.Context
import android.graphics.Bitmap
import com.loctell.vikrsaathi.data.model.template.InvoiceTemplate
import com.loctell.vikrsaathi.util.TemplateImageStore

object TemplateContextFactory {

    fun create(
        context: Context,
        template: InvoiceTemplate,
        bill: com.loctell.vikrsaathi.data.model.BillWithDetails,
        shopName: String,
        currencySymbol: String,
        headerImage: Bitmap?,
        signatureImage: Bitmap?,
        shopLogoImage: Bitmap?
    ): TemplateRenderContext {
        return TemplateRenderContext(
            bill = bill,
            shopName = shopName,
            currencySymbol = currencySymbol,
            headerImage = headerImage,
            signatureImage = signatureImage,
            shopLogoImage = shopLogoImage,
            staticImages = TemplateImageStore.loadForTemplate(context, template)
        )
    }
}
