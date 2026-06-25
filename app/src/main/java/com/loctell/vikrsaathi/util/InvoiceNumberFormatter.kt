package com.loctell.vikrsaathi.util

object InvoiceNumberFormatter {

    fun format(
        prefix: String,
        counter: Int,
        suffix: String,
        separator: String,
        counterMinDigits: Int = 2
    ): String {
        val sep = separator.ifEmpty { "/" }
        val counterText = if (counterMinDigits > 0) {
            counter.toString().padStart(counterMinDigits, '0')
        } else {
            counter.toString()
        }
        val parts = mutableListOf<String>()
        if (prefix.isNotBlank()) parts.add(prefix.trim())
        parts.add(counterText)
        if (suffix.isNotBlank()) parts.add(suffix.trim())
        return parts.joinToString(sep)
    }

    fun preview(
        prefix: String,
        counter: Int,
        suffix: String,
        separator: String,
        counterMinDigits: Int = 2
    ): String = format(prefix, counter, suffix, separator, counterMinDigits)
}
