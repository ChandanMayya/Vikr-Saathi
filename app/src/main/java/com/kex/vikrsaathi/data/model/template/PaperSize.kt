package com.kex.vikrsaathi.data.model.template

import kotlin.math.abs
import kotlin.math.roundToInt

enum class PaperSizeId {
    A1,
    A2,
    A3,
    A4,
    A5,
    HALF_A5,
    LETTER,
    LEGAL,
    TABLOID,
    HALF_LETTER,
    JUNIOR_LEGAL,
    CUSTOM;

    companion object {
        fun fromStored(value: String?): PaperSizeId =
            entries.find { it.name == value } ?: CUSTOM
    }
}

/**
 * Named paper sizes in PDF points (72 DPI), stored as portrait width × height.
 * Junior Legal uses 5×8 in portrait (360×576).
 */
data class PaperSizeSpec(
    val id: PaperSizeId,
    val labelRes: Int,
    val widthPtPortrait: Int,
    val heightPtPortrait: Int
) {
    fun ptsForOrientation(landscape: Boolean): Pair<Int, Int> =
        if (landscape) heightPtPortrait to widthPtPortrait
        else widthPtPortrait to heightPtPortrait
}

object PaperSizeCatalog {

    /** 1 mm in PDF points (72 dpi). */
    const val PT_PER_MM = 72.0 / 25.4
    const val MIN_SIZE_PT = 50
    const val MAX_SIZE_PT = 2000

    val presets: List<PaperSizeSpec> = listOf(
        PaperSizeSpec(PaperSizeId.A1, com.kex.vikrsaathi.R.string.paper_size_a1, 1684, 2384),
        PaperSizeSpec(PaperSizeId.A2, com.kex.vikrsaathi.R.string.paper_size_a2, 1191, 1684),
        PaperSizeSpec(PaperSizeId.A3, com.kex.vikrsaathi.R.string.paper_size_a3, 842, 1191),
        PaperSizeSpec(PaperSizeId.A4, com.kex.vikrsaathi.R.string.paper_size_a4, 595, 842),
        PaperSizeSpec(PaperSizeId.A5, com.kex.vikrsaathi.R.string.paper_size_a5, 420, 595),
        // Half of A5 (tear across mid-height of A5 portrait) — 105×148 mm portrait
        PaperSizeSpec(PaperSizeId.HALF_A5, com.kex.vikrsaathi.R.string.paper_size_half_a5, 298, 420),
        PaperSizeSpec(PaperSizeId.LETTER, com.kex.vikrsaathi.R.string.paper_size_letter, 612, 792),
        PaperSizeSpec(PaperSizeId.LEGAL, com.kex.vikrsaathi.R.string.paper_size_legal, 612, 1008),
        PaperSizeSpec(PaperSizeId.TABLOID, com.kex.vikrsaathi.R.string.paper_size_tabloid, 792, 1224),
        PaperSizeSpec(PaperSizeId.HALF_LETTER, com.kex.vikrsaathi.R.string.paper_size_half_letter, 396, 612),
        PaperSizeSpec(PaperSizeId.JUNIOR_LEGAL, com.kex.vikrsaathi.R.string.paper_size_junior_legal, 360, 576)
    )

    fun specFor(id: PaperSizeId): PaperSizeSpec? =
        presets.find { it.id == id }

    fun selectableIds(): List<PaperSizeId> =
        presets.map { it.id } + PaperSizeId.CUSTOM

    /**
     * Match page dimensions to a named preset (order-independent within a few points).
     */
    fun match(widthPt: Int, heightPt: Int, tolerancePt: Int = 2): PaperSizeId {
        val a = minOf(widthPt, heightPt)
        val b = maxOf(widthPt, heightPt)
        for (spec in presets) {
            val pa = minOf(spec.widthPtPortrait, spec.heightPtPortrait)
            val pb = maxOf(spec.widthPtPortrait, spec.heightPtPortrait)
            if (abs(a - pa) <= tolerancePt && abs(b - pb) <= tolerancePt) {
                return spec.id
            }
        }
        return PaperSizeId.CUSTOM
    }

    fun mmToPt(mm: Double): Int =
        (mm * PT_PER_MM).roundToInt().coerceIn(MIN_SIZE_PT, MAX_SIZE_PT)

    fun ptToMm(pt: Int): Double = pt / PT_PER_MM

    fun clampSizePt(value: Int): Int = value.coerceIn(MIN_SIZE_PT, MAX_SIZE_PT)
}
