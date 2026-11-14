package com.kex.vikrsaathi.ui.bill

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.model.BillVersionDetails
import com.kex.vikrsaathi.data.repository.BillNotFoundException
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.InsufficientStockException
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import com.kex.vikrsaathi.data.repository.StockShortfall
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.util.InventoryMode
import com.kex.vikrsaathi.util.PdfGenerator
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillVersionsViewModel(
    private val repository: BillRepository,
    private val settingsRepository: SettingsRepository,
    private val invoiceTemplateRepository: InvoiceTemplateRepository
) : ViewModel() {

    data class HistoryState(
        val billNumber: String = "",
        val versions: List<BillVersionDetails> = emptyList()
    )

    private val _history = MutableLiveData(HistoryState())
    val history: LiveData<HistoryState> = _history

    val currencySymbol: String
        get() = settingsRepository.currencySymbol

    val inventoryMode: InventoryMode
        get() = settingsRepository.inventoryMode

    private var billId: Long = -1L

    fun load(billId: Long) {
        this.billId = billId
        viewModelScope.launch {
            publish(billId)
        }
    }

    fun restoreVersion(
        versionNumber: Int,
        shortageAcknowledged: Boolean = false,
        onResult: (VersionActionOutcome) -> Unit
    ) {
        viewModelScope.launch {
            val outcome = runAction {
                repository.restoreVersion(billId, versionNumber, shortageAcknowledged)
                publish(billId)
                VersionActionOutcome.Applied
            }
            onResult(outcome)
        }
    }

    fun duplicateVersion(
        versionNumber: Int,
        shortageAcknowledged: Boolean = false,
        onResult: (VersionActionOutcome) -> Unit
    ) {
        viewModelScope.launch {
            val outcome = runAction {
                val newId = repository.duplicateVersion(billId, versionNumber, shortageAcknowledged)
                if (newId == null) VersionActionOutcome.Missing else VersionActionOutcome.Created(newId)
            }
            onResult(outcome)
        }
    }

    fun printVersion(context: Context, details: BillVersionDetails, onResult: (File?) -> Unit) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val template = invoiceTemplateRepository.getDefaultTemplate()
                    PdfGenerator.generateBillPdf(
                        context = appContext,
                        template = template,
                        bill = repository.printableBill(details),
                        shopName = settingsRepository.shopName,
                        currencySymbol = settingsRepository.currencySymbol,
                        headerImage = settingsRepository.getHeaderImage(),
                        signatureImage = settingsRepository.getSignatureImage(),
                        shopLogoImage = settingsRepository.getShopLogoImage()
                    )
                }.getOrNull()
            }
            onResult(file)
        }
    }

    private suspend fun publish(id: Long) {
        val versions = repository.getVersionHistory(id)
        val number = versions.firstOrNull()?.version?.billNumber
            ?: repository.getBillWithDetails(id)?.bill?.billNumber.orEmpty()
        _history.value = HistoryState(billNumber = number, versions = versions)
    }

    private suspend fun runAction(block: suspend () -> VersionActionOutcome): VersionActionOutcome {
        return try {
            block()
        } catch (error: InsufficientStockException) {
            VersionActionOutcome.Shortage(error.shortfalls)
        } catch (_: BillNotFoundException) {
            VersionActionOutcome.Missing
        } catch (error: Exception) {
            VersionActionOutcome.Failed(error)
        }
    }
}

sealed class VersionActionOutcome {
    data object Applied : VersionActionOutcome()
    data class Created(val billId: Long) : VersionActionOutcome()
    data object Missing : VersionActionOutcome()
    data class Shortage(val shortfalls: List<StockShortfall>) : VersionActionOutcome()
    data class Failed(val error: Throwable) : VersionActionOutcome()
}
