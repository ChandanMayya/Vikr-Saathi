package com.kex.vikrsaathi.util

/**
 * PDF page layout uses typographic points (72 DPI). Raster images must be embedded
 * at a higher pixel density or they look pixelated on print (~300 DPI).
 */
object PdfRenderQuality {
    const val IMAGE_SCALE = 4f
}
