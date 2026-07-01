package com.kex.vikrsaathi.domain.template

object TableBorderSettings {

    const val CONTENT_KEY = "borderWidthDp"
    const val DEFAULT_DP = 1.5f
    const val MIN_DP = 0.25f
    const val MAX_DP = 8f

    fun parseBorderWidthDp(content: Map<String, String>): Float {
        val raw = content[CONTENT_KEY]?.toFloatOrNull() ?: DEFAULT_DP
        return raw.coerceIn(MIN_DP, MAX_DP)
    }

    fun formatBorderWidthDp(dp: Float): String =
        dp.coerceIn(MIN_DP, MAX_DP).toString()

    fun strokePt(content: Map<String, String>): Float =
        dpToPt(parseBorderWidthDp(content))

    fun dpToPt(dp: Float): Float = dp.coerceIn(MIN_DP, MAX_DP) * 72f / 160f
}
