package com.loctell.vikrsaathi.util

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.loctell.vikrsaathi.data.model.BillWithDetails
import com.loctell.vikrsaathi.data.model.template.InvoiceTemplate
import com.loctell.vikrsaathi.domain.template.TemplateContextFactory
import com.loctell.vikrsaathi.domain.template.TemplatePdfRenderer
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {

    fun generateBillPdf(
        context: Context,
        template: InvoiceTemplate,
        bill: BillWithDetails,
        shopName: String,
        currencySymbol: String,
        headerImage: android.graphics.Bitmap?,
        signatureImage: android.graphics.Bitmap?,
        shopLogoImage: android.graphics.Bitmap? = null
    ): File {
        val renderContext = TemplateContextFactory.create(
            context = context,
            template = template,
            bill = bill,
            shopName = shopName,
            currencySymbol = currencySymbol,
            headerImage = headerImage,
            signatureImage = signatureImage,
            shopLogoImage = shopLogoImage
        )

        val document = PdfDocument()
        TemplatePdfRenderer.render(document, template, renderContext)

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, "Bill_${bill.bill.billNumber}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }
}
