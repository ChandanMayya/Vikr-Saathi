package com.kex.vikrsaathi.domain.template

import android.content.Context
import android.graphics.Bitmap
import com.kex.vikrsaathi.data.model.template.DataBindingKey
import com.kex.vikrsaathi.data.model.template.ElementBinding
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.util.TemplateImageStore

object TemplateImageBitmapResolver {

    fun resolve(
        context: Context,
        element: TemplateElement,
        settingsRepository: SettingsRepository,
        templateId: Long = 0L
    ): Bitmap? {
        if (element.kind != ElementKind.IMAGE) return null
        return when (element.binding) {
            ElementBinding.STATIC -> {
                element.content["imagePath"]?.let { path ->
                    TemplateImageStore.load(context, path)
                }
            }
            ElementBinding.DYNAMIC -> {
                val keyName = element.content["bindingKey"] ?: return null
                when (runCatching { DataBindingKey.valueOf(keyName) }.getOrNull()) {
                    DataBindingKey.HEADER_IMAGE -> settingsRepository.getHeaderImage()
                    DataBindingKey.SIGNATURE_IMAGE -> settingsRepository.getSignatureImage()
                    DataBindingKey.SHOP_LOGO -> settingsRepository.getShopLogoImage()
                    else -> null
                }
            }
        }
    }
}
