package com.loctell.vikrsaathi.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.loctell.vikrsaathi.data.model.template.ElementBinding
import com.loctell.vikrsaathi.data.model.template.ElementKind
import com.loctell.vikrsaathi.data.model.template.InvoiceTemplate
import java.io.File
import java.io.FileOutputStream

object TemplateImageStore {

    private fun storageDir(context: Context): File =
        File(context.filesDir, "template_element_images").apply { mkdirs() }

    fun save(context: Context, templateId: Long, elementId: String, bitmap: Bitmap): String {
        val fileName = "${templateId}_${elementId}.png"
        val file = File(storageDir(context), fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return fileName
    }

    fun load(context: Context, imagePath: String): Bitmap? {
        if (imagePath.isBlank()) return null
        val file = File(storageDir(context), imagePath)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun loadForTemplate(context: Context, template: InvoiceTemplate): Map<String, Bitmap> {
        return template.elements
            .filter { it.kind == ElementKind.IMAGE && it.binding == ElementBinding.STATIC }
            .mapNotNull { element ->
                val path = element.content["imagePath"] ?: return@mapNotNull null
                load(context, path)?.let { element.id to it }
            }
            .toMap()
    }
}
