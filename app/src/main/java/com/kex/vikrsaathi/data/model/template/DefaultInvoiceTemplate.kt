package com.kex.vikrsaathi.data.model.template

import com.kex.vikrsaathi.domain.template.TableBorderSettings

/**
 * Factory for the built-in template that mirrors the original hardcoded PDF layout.
 */
object DefaultInvoiceTemplate {

    private val defaultTableColumns = listOf(
        TableColumn("sl", "Sl", 7f, TextAlign.CENTER),
        TableColumn("name", "Particulars", 43f, TextAlign.LEFT),
        TableColumn("quantity", "Qty", 11f, TextAlign.CENTER),
        TableColumn("mrp", "MRP", 11f, TextAlign.CENTER),
        TableColumn("discount", "Disc%", 11f, TextAlign.CENTER),
        TableColumn("lineTotal", "Price", 17f, TextAlign.CENTER)
    )

    fun create(): InvoiceTemplate {
        val columnsJson = TemplateJsonCodec.tableColumnsToJson(defaultTableColumns)
        return InvoiceTemplate(
            name = "Default Retail",
            isDefault = true,
            elements = listOf(
                TemplateElement(
                    id = "header_image",
                    kind = ElementKind.IMAGE,
                    binding = ElementBinding.DYNAMIC,
                    bounds = ElementBounds(40f, 30f, 515f, 100f),
                    zIndex = 1,
                    style = ElementStyle(imageScaleMode = ImageScaleMode.FIT_WIDTH),
                    content = mapOf("bindingKey" to DataBindingKey.HEADER_IMAGE.name)
                ),
                TemplateElement(
                    id = "shop_name",
                    kind = ElementKind.TEXT,
                    binding = ElementBinding.DYNAMIC,
                    bounds = ElementBounds(40f, 138f, 515f, 28f),
                    zIndex = 2,
                    style = ElementStyle(fontSize = 20f, bold = true),
                    content = mapOf("bindingKey" to DataBindingKey.SHOP_NAME.name)
                ),
                TemplateElement(
                    id = "bill_number",
                    kind = ElementKind.TEXT,
                    binding = ElementBinding.DYNAMIC,
                    bounds = ElementBounds(40f, 172f, 260f, 20f),
                    zIndex = 3,
                    style = ElementStyle(fontSize = 12f),
                    content = mapOf(
                        "bindingKey" to DataBindingKey.BILL_NUMBER.name,
                        "prefix" to "Bill No: "
                    )
                ),
                TemplateElement(
                    id = "bill_date",
                    kind = ElementKind.TEXT,
                    binding = ElementBinding.DYNAMIC,
                    bounds = ElementBounds(315f, 172f, 240f, 20f),
                    zIndex = 4,
                    style = ElementStyle(fontSize = 12f, textAlign = TextAlign.RIGHT),
                    content = mapOf(
                        "bindingKey" to DataBindingKey.BILL_DATE.name,
                        "prefix" to "Date: "
                    )
                ),
                TemplateElement(
                    id = "divider_top",
                    kind = ElementKind.LINE,
                    binding = ElementBinding.STATIC,
                    bounds = ElementBounds(40f, 200f, 515f, 1f),
                    zIndex = 5
                ),
                TemplateElement(
                    id = "buyer_label",
                    kind = ElementKind.TEXT,
                    binding = ElementBinding.STATIC,
                    bounds = ElementBounds(40f, 212f, 200f, 22f),
                    zIndex = 6,
                    style = ElementStyle(fontSize = 14f, bold = true),
                    content = mapOf("text" to "Buyer Details")
                ),
                TemplateElement(
                    id = "customer_name",
                    kind = ElementKind.TEXT,
                    binding = ElementBinding.DYNAMIC,
                    bounds = ElementBounds(40f, 238f, 515f, 18f),
                    zIndex = 7,
                    content = mapOf(
                        "bindingKey" to DataBindingKey.CUSTOMER_NAME.name,
                        "prefix" to "Name: "
                    )
                ),
                TemplateElement(
                    id = "customer_address",
                    kind = ElementKind.TEXT,
                    binding = ElementBinding.DYNAMIC,
                    bounds = ElementBounds(40f, 258f, 515f, 18f),
                    zIndex = 8,
                    content = mapOf(
                        "bindingKey" to DataBindingKey.CUSTOMER_ADDRESS.name,
                        "prefix" to "Address: "
                    )
                ),
                TemplateElement(
                    id = "customer_phone",
                    kind = ElementKind.TEXT,
                    binding = ElementBinding.DYNAMIC,
                    bounds = ElementBounds(40f, 278f, 515f, 18f),
                    zIndex = 9,
                    content = mapOf(
                        "bindingKey" to DataBindingKey.CUSTOMER_PHONE.name,
                        "prefix" to "Phone: "
                    )
                ),
                TemplateElement(
                    id = "items_table",
                    kind = ElementKind.TABLE,
                    binding = ElementBinding.DYNAMIC,
                    bounds = ElementBounds(40f, 310f, 515f, 320f),
                    zIndex = 10,
                    content = mapOf(
                        "bindingKey" to DataBindingKey.BILL_ITEMS.name,
                        "columns" to columnsJson,
                        "showHeader" to "true",
                        TableBorderSettings.CONTENT_KEY to TableBorderSettings.formatBorderWidthDp(
                            TableBorderSettings.DEFAULT_DP
                        )
                    )
                ),
                TemplateElement(
                    id = "bill_total",
                    kind = ElementKind.TEXT,
                    binding = ElementBinding.DYNAMIC,
                    bounds = ElementBounds(40f, 648f, 300f, 24f),
                    zIndex = 11,
                    style = ElementStyle(fontSize = 14f, bold = true),
                    content = mapOf(
                        "bindingKey" to DataBindingKey.BILL_TOTAL.name,
                        "prefix" to "Total: "
                    )
                ),
                TemplateElement(
                    id = "bill_total_words",
                    kind = ElementKind.TEXT,
                    binding = ElementBinding.DYNAMIC,
                    bounds = ElementBounds(40f, 678f, 515f, 48f),
                    zIndex = 12,
                    style = ElementStyle(fontSize = 11f),
                    content = mapOf(
                        "bindingKey" to DataBindingKey.BILL_TOTAL_WORDS.name,
                        "prefix" to "Amount in Words: ",
                        "wrap" to "true"
                    )
                ),
                TemplateElement(
                    id = "signature_image",
                    kind = ElementKind.IMAGE,
                    binding = ElementBinding.DYNAMIC,
                    bounds = ElementBounds(40f, 736f, 150f, 60f),
                    zIndex = 13,
                    style = ElementStyle(imageScaleMode = ImageScaleMode.FIT_WIDTH),
                    content = mapOf("bindingKey" to DataBindingKey.SIGNATURE_IMAGE.name)
                ),
                TemplateElement(
                    id = "signature_label",
                    kind = ElementKind.TEXT,
                    binding = ElementBinding.STATIC,
                    bounds = ElementBounds(40f, 802f, 220f, 20f),
                    zIndex = 14,
                    style = ElementStyle(fontSize = 12f),
                    content = mapOf("text" to "Authorised Signature")
                )
            )
        )
    }
}
