package com.kex.vikrsaathi.ui.settings

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.model.template.DataBindingKey
import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val invoiceTemplateRepository: InvoiceTemplateRepository
) : ViewModel() {

    private val _shopName = MutableLiveData(repository.shopName)
    val shopName: LiveData<String> = _shopName

    private val _currencySymbol = MutableLiveData(repository.currencySymbol)
    val currencySymbol: LiveData<String> = _currencySymbol

    private val _defaultDiscount = MutableLiveData(repository.defaultDiscount)
    val defaultDiscount: LiveData<Double> = _defaultDiscount

    fun saveShopName(name: String) {
        repository.shopName = name
        _shopName.value = name
    }

    fun saveCurrency(symbol: String) {
        repository.currencySymbol = symbol
        _currencySymbol.value = symbol
    }

    fun saveDefaultDiscount(discount: Double) {
        repository.defaultDiscount = discount
        _defaultDiscount.value = discount
    }

    fun saveInvoiceConfig(
        prefix: String,
        suffix: String,
        separator: String,
        counter: Int
    ) {
        repository.invoicePrefix = prefix
        repository.invoiceSuffix = suffix
        repository.invoiceSeparator = separator
        repository.invoiceCounter = counter.coerceAtLeast(1)
    }

    fun invoicePrefix(): String = repository.invoicePrefix
    fun invoiceSuffix(): String = repository.invoiceSuffix
    fun invoiceSeparator(): String = repository.invoiceSeparator
    fun invoiceCounter(): Int = repository.invoiceCounter

    fun previewInvoiceNumber(): String {
        return com.kex.vikrsaathi.util.InvoiceNumberFormatter.preview(
            prefix = repository.invoicePrefix,
            counter = repository.invoiceCounter,
            suffix = repository.invoiceSuffix,
            separator = repository.invoiceSeparator,
            counterMinDigits = repository.invoiceCounterMinDigits
        )
    }

    fun saveHeaderImage(bitmap: Bitmap) = repository.saveHeaderImage(bitmap)

    fun saveSignatureImage(bitmap: Bitmap) = repository.saveSignatureImage(bitmap)

    fun saveShopLogoImage(bitmap: Bitmap) = repository.saveShopLogoImage(bitmap)

    fun getHeaderImage(): Bitmap? = repository.getHeaderImage()

    fun getSignatureImage(): Bitmap? = repository.getSignatureImage()

    fun getShopLogoImage(): Bitmap? = repository.getShopLogoImage()

    fun checkImageLayoutSync(
        bindingKey: DataBindingKey,
        bitmap: Bitmap,
        onResult: (templateCount: Int, sampleBounds: ElementBounds?) -> Unit
    ) {
        viewModelScope.launch {
            val count = invoiceTemplateRepository.countTemplatesNeedingImageBoundsSync(
                bindingKey = bindingKey,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
            val sample = if (count > 0) {
                invoiceTemplateRepository.sampleSuggestedBoundsForBinding(
                    bindingKey = bindingKey,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )
            } else {
                null
            }
            onResult(count, sample)
        }
    }

    fun syncImageLayoutForBinding(
        bindingKey: DataBindingKey,
        bitmap: Bitmap,
        onDone: (updatedElementCount: Int) -> Unit
    ) {
        viewModelScope.launch {
            val updated = invoiceTemplateRepository.syncImageBindingBounds(
                bindingKey = bindingKey,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
            onDone(updated)
        }
    }
}
