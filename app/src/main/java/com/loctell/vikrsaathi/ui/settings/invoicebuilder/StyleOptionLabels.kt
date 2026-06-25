package com.loctell.vikrsaathi.ui.settings.invoicebuilder

import com.loctell.vikrsaathi.data.model.template.FontFamily
import com.loctell.vikrsaathi.data.model.template.TextAlign
import com.loctell.vikrsaathi.data.model.template.VerticalAlign

object StyleOptionLabels {

    data class Option(val value: String, val label: String)

    fun horizontalAligns(): List<Option> = listOf(
        Option(TextAlign.LEFT.name, "Left"),
        Option(TextAlign.CENTER.name, "Center"),
        Option(TextAlign.RIGHT.name, "Right")
    )

    fun verticalAligns(): List<Option> = listOf(
        Option(VerticalAlign.TOP.name, "Top"),
        Option(VerticalAlign.CENTER.name, "Center"),
        Option(VerticalAlign.BOTTOM.name, "Bottom")
    )

    fun fontFamilies(): List<Option> = listOf(
        Option(FontFamily.DEFAULT.name, "Default"),
        Option(FontFamily.SANS_SERIF.name, "Sans serif"),
        Option(FontFamily.SERIF.name, "Serif"),
        Option(FontFamily.MONOSPACE.name, "Monospace")
    )

    fun labelForHorizontal(name: String): String =
        horizontalAligns().find { it.value == name }?.label ?: name

    fun labelForVertical(name: String): String =
        verticalAligns().find { it.value == name }?.label ?: name

    fun labelForFontFamily(name: String): String =
        fontFamilies().find { it.value == name }?.label ?: name
}
