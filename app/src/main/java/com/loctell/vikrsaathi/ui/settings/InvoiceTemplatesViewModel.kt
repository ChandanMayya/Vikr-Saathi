package com.loctell.vikrsaathi.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loctell.vikrsaathi.data.model.template.InvoiceTemplate
import com.loctell.vikrsaathi.data.repository.InvoiceTemplateRepository
import kotlinx.coroutines.launch

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
}
