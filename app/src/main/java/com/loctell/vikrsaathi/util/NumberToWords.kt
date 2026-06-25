package com.loctell.vikrsaathi.util

/**
 * Converts a numeric amount to Indian English words (Rupees and Paise).
 */
object NumberToWords {

    private val units = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    )
    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    fun convert(amount: Double): String {
        val rupees = amount.toLong()
        val paise = Math.round((amount - rupees) * 100).toInt()

        val rupeeWords = if (rupees == 0L) "Zero" else convertNumber(rupees)
        val result = StringBuilder("$rupeeWords Rupees")

        if (paise > 0) {
            result.append(" and ").append(convertNumber(paise.toLong())).append(" Paise")
        }
        result.append(" Only")
        return result.toString()
    }

    private fun convertNumber(number: Long): String {
        if (number < 20) return units[number.toInt()]
        if (number < 100) {
            return tens[(number / 10).toInt()] +
                if (number % 10 != 0L) " " + units[(number % 10).toInt()] else ""
        }
        if (number < 1000) {
            return units[(number / 100).toInt()] + " Hundred" +
                if (number % 100 != 0L) " " + convertNumber(number % 100) else ""
        }
        if (number < 100_000) {
            return convertNumber(number / 1000) + " Thousand" +
                if (number % 1000 != 0L) " " + convertNumber(number % 1000) else ""
        }
        if (number < 10_000_000) {
            return convertNumber(number / 100_000) + " Lakh" +
                if (number % 100_000 != 0L) " " + convertNumber(number % 100_000) else ""
        }
        return convertNumber(number / 10_000_000) + " Crore" +
            if (number % 10_000_000 != 0L) " " + convertNumber(number % 10_000_000) else ""
    }
}
