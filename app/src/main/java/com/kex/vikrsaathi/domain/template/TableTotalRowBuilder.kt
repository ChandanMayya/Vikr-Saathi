package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.data.model.template.TableColumn
import com.kex.vikrsaathi.util.PriceCalculator

object TableTotalRowBuilder {

    private val labelColumnKeys = setOf("name", "particulars", "description", "item")
    private val serialColumnKeys = setOf("sl", "sno", "sr", "#")
    private val quantityColumnKeys = setOf("quantity", "qty")
    private val discountPercentColumnKeys = setOf("discount", "disc")
    private val discountAmountColumnKeys = setOf("discountAmount", "discAmt", "discount_amount")
    private val amountColumnKeys = setOf("lineTotal", "amount", "total")

    fun build(
        context: TemplateRenderContext,
        columns: List<TableColumn>,
        label: String
    ): Map<String, String> {
        val lines = context.bill.items.map { item ->
            BillLineItem(
                itemId = item.itemId,
                name = item.itemName,
                mrp = item.mrp,
                discount = item.discount,
                quantity = item.quantity
            )
        }

        val values = mutableMapOf<String, String>()
        var labelPlaced = false

        columns.forEach { column ->
            when (column.key) {
                in labelColumnKeys -> {
                    if (!labelPlaced) {
                        values[column.key] = label
                        labelPlaced = true
                    } else {
                        values[column.key] = ""
                    }
                }
                in quantityColumnKeys -> {
                    values[column.key] = lines.sumOf { it.quantity }.toString()
                }
                in discountAmountColumnKeys -> {
                    val totalDiscount = lines.sumOf {
                        PriceCalculator.discountAmount(it.mrp, it.discount, it.quantity)
                    }
                    values[column.key] = PriceCalculator.formatAmount(
                        totalDiscount,
                        context.currencySymbol
                    )
                }
                in discountPercentColumnKeys -> values[column.key] = ""
                in amountColumnKeys -> {
                    val totalAmount = lines.sumOf { it.lineTotal }
                    values[column.key] = PriceCalculator.formatAmount(
                        totalAmount,
                        context.currencySymbol
                    )
                }
                in serialColumnKeys -> values[column.key] = ""
                else -> values[column.key] = ""
            }
        }

        if (!labelPlaced) {
            val labelKey = columns.firstOrNull { it.key !in serialColumnKeys }?.key
            if (labelKey != null) {
                values[labelKey] = label
            }
        }

        return values
    }
}
