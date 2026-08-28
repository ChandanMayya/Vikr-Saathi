package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.TableColumn
import com.kex.vikrsaathi.util.PriceCalculator

object TableBillRoundOffRowBuilder {

    private const val LABEL = "Bill Round Off"

    private val labelColumnKeys = setOf("name", "particulars", "description", "item")
    private val roundOffColumnKeys = setOf("roundOff", "round_off", "lineRoundOff")

    fun build(context: TemplateRenderContext, columns: List<TableColumn>): Map<String, String> {
        val values = mutableMapOf<String, String>()
        var labelPlaced = false

        columns.forEach { column ->
            when (column.key) {
                in labelColumnKeys -> {
                    if (!labelPlaced) {
                        values[column.key] = LABEL
                        labelPlaced = true
                    } else {
                        values[column.key] = ""
                    }
                }
                in roundOffColumnKeys -> {
                    values[column.key] = PriceCalculator.formatSignedAmount(
                        context.bill.bill.roundOff,
                        context.currencySymbol
                    )
                }
                else -> values[column.key] = ""
            }
        }

        if (!labelPlaced) {
            val labelKey = columns.firstOrNull { it.key !in setOf("sl", "sno", "sr", "#") }?.key
            if (labelKey != null) {
                values[labelKey] = LABEL
            }
        }

        return values
    }
}
