package com.loctell.vikrsaathi.data.model.template

data class InvoiceTemplateVersion(
    val id: Long,
    val templateId: Long,
    val versionNumber: Int,
    val savedAt: Long,
    val elementCount: Int
)
