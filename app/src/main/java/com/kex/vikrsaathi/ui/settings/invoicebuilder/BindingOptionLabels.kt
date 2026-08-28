package com.kex.vikrsaathi.ui.settings.invoicebuilder

import com.kex.vikrsaathi.data.model.template.DataBindingKey
import com.kex.vikrsaathi.data.model.template.ElementBinding
import com.kex.vikrsaathi.data.model.template.ElementKind

object BindingOptionLabels {

    data class Option(val value: String, val label: String)

    fun bindingTypes(): List<Option> = listOf(
        Option(ElementBinding.STATIC.name, "Fixed (you choose)"),
        Option(ElementBinding.DYNAMIC.name, "From bill / settings")
    )

    fun textDataFields(): List<Option> = listOf(
        Option(DataBindingKey.SHOP_NAME.name, "Shop name"),
        Option(DataBindingKey.BILL_NUMBER.name, "Bill number"),
        Option(DataBindingKey.BILL_DATE.name, "Bill date"),
        Option(DataBindingKey.CUSTOMER_NAME.name, "Customer name"),
        Option(DataBindingKey.CUSTOMER_ADDRESS.name, "Customer address"),
        Option(DataBindingKey.CUSTOMER_PHONE.name, "Customer phone"),
        Option(DataBindingKey.BILL_SUBTOTAL.name, "Bill subtotal"),
        Option(DataBindingKey.BILL_LINE_ROUND_OFF.name, "Line round off total"),
        Option(DataBindingKey.BILL_ROUND_OFF.name, "Bill round off"),
        Option(DataBindingKey.BILL_TOTAL.name, "Bill total"),
        Option(DataBindingKey.BILL_TOTAL_WORDS.name, "Total in words")
    )

    fun imageDataFields(): List<Option> = listOf(
        Option(DataBindingKey.HEADER_IMAGE.name, "Header image (Settings)"),
        Option(DataBindingKey.SIGNATURE_IMAGE.name, "Signature image (Settings)"),
        Option(DataBindingKey.SHOP_LOGO.name, "Shop logo (Settings)")
    )

    fun labelForBindingType(name: String): String =
        bindingTypes().find { it.value == name }?.label ?: name

    fun labelForDataField(name: String, kind: ElementKind): String {
        val options = if (kind == ElementKind.IMAGE) imageDataFields() else textDataFields()
        return options.find { it.value == name }?.label ?: name
    }

    fun dataFieldValues(kind: ElementKind): List<String> =
        (if (kind == ElementKind.IMAGE) imageDataFields() else textDataFields()).map { it.value }

    fun dataFieldLabels(kind: ElementKind): List<String> =
        (if (kind == ElementKind.IMAGE) imageDataFields() else textDataFields()).map { it.label }
}
