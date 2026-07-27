package com.kex.vikrsaathi.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.model.template.TemplateJsonCodec
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.repository.DeleteTemplateResult
import com.kex.vikrsaathi.data.repository.InvoiceTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InvoiceTemplatesViewModel(
    private val repository: InvoiceTemplateRepository
) : ViewModel() {

    val templates: LiveData<List<InvoiceTemplate>> = repository.allTemplates

    fun setAsDefault(templateId: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.setAsDefault(templateId)
            onDone()
        }
    }

    fun createNewTemplate(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createBlankFromDefault(name)
            onCreated(id)
        }
    }

    fun deleteTemplate(
        templateId: Long,
        imageStoreContext: android.content.Context,
        onResult: (DeleteTemplateResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.deleteTemplate(templateId, imageStoreContext)
            onResult(result)
        }
    }

    fun importTemplateJson(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: return@withContext null
                    TemplateJsonCodec.fromJson(json)
                } catch (_: Exception) {
                    null
                }
            } ?: run {
                onResult(false)
                return@launch
            }

            repository.insert(
                imported.copy(
                    id = 0L,
                    isDefault = false,
                    version = 1,
                    updatedAt = System.currentTimeMillis()
                )
            )
            onResult(true)
        }
    }
}
