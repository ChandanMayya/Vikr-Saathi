package com.kex.vikrsaathi.ui.bill

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.domain.template.TemplateContextFactory
import com.kex.vikrsaathi.domain.template.TemplateRenderContext
import com.kex.vikrsaathi.util.PdfGenerator
import java.io.File
import kotlinx.coroutines.launch

data class BillPreviewUiState(
    val billNumber: String,
    val customerName: String,
    val template: InvoiceTemplate,
    val renderContext: TemplateRenderContext
)

class BillPreviewViewModel(
    private val billRepository: BillRepository,
    private val settingsRepository: SettingsRepository,
    private val invoiceTemplateRepository: InvoiceTemplateRepository
) : ViewModel() {

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private val _preview = MutableLiveData<BillPreviewUiState?>()
    val preview: LiveData<BillPreviewUiState?> = _preview

    private var billId: Long = -1L

    fun load(context: Context, billId: Long) {
        if (billId <= 0) {
            _loading.value = false
            _preview.value = null
            return
        }
        this.billId = billId
        viewModelScope.launch {
            _loading.value = true
            val bill = billRepository.getBillWithDetails(billId)
            if (bill == null) {
                _preview.value = null
                _loading.value = false
                return@launch
            }
            val template = invoiceTemplateRepository.getDefaultTemplate()
            val renderContext = TemplateContextFactory.create(
                context = context,
                template = template,
                bill = bill,
                shopName = settingsRepository.shopName,
                currencySymbol = settingsRepository.currencySymbol,
                headerImage = settingsRepository.getHeaderImage(),
                signatureImage = settingsRepository.getSignatureImage(),
                shopLogoImage = settingsRepository.getShopLogoImage()
            )
            _preview.value = BillPreviewUiState(
                billNumber = bill.bill.billNumber,
                customerName = bill.customer?.name ?: "Walk-in customer",
                template = template,
                renderContext = renderContext
            )
            _loading.value = false
        }
    }

    fun exportPdf(context: Context, onResult: (File?) -> Unit) {
        if (billId <= 0) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val bill = billRepository.getBillWithDetails(billId) ?: run {
                onResult(null)
                return@launch
            }
            val template = invoiceTemplateRepository.getDefaultTemplate()
            val file = PdfGenerator.generateBillPdf(
                context = context,
                template = template,
                bill = bill,
                shopName = settingsRepository.shopName,
                currencySymbol = settingsRepository.currencySymbol,
                headerImage = settingsRepository.getHeaderImage(),
                signatureImage = settingsRepository.getSignatureImage(),
                shopLogoImage = settingsRepository.getShopLogoImage()
            )
            onResult(file)
        }
    }
}
