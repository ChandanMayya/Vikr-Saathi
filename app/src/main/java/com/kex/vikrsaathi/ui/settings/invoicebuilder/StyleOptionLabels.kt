package com.kex.vikrsaathi.ui.settings.invoicebuilder

import com.kex.vikrsaathi.data.model.template.FontFamily
import com.kex.vikrsaathi.data.model.template.ImageScaleMode
import com.kex.vikrsaathi.data.model.template.TextAlign
import com.kex.vikrsaathi.data.model.template.VerticalAlign

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

    fun imageScaleModes(): List<Option> = listOf(
        Option(ImageScaleMode.FIT.name, "Fit inside"),
        Option(ImageScaleMode.FIT_WIDTH.name, "Fit to width"),
        Option(ImageScaleMode.FIT_HEIGHT.name, "Fit to height"),
        Option(ImageScaleMode.FILL.name, "Fill (crop)"),
        Option(ImageScaleMode.STRETCH.name, "Stretch to box")
    )

    fun labelForImageScaleMode(name: String): String =
        imageScaleModes().find { it.value == name }?.label ?: name
}
