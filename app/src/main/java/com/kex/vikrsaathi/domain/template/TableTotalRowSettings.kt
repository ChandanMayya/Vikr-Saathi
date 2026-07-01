package com.kex.vikrsaathi.domain.template

object TableTotalRowSettings {

    const val SHOW_TOTAL_ROW_KEY = "showTotalRow"
    const val TOTAL_ROW_LABEL_KEY = "totalRowLabel"
    const val DEFAULT_LABEL = "Total"

    fun showTotalRow(content: Map<String, String>): Boolean =
        content[SHOW_TOTAL_ROW_KEY] != "false"

    fun totalRowLabel(content: Map<String, String>): String =
        content[TOTAL_ROW_LABEL_KEY]?.takeIf { it.isNotBlank() } ?: DEFAULT_LABEL
}
