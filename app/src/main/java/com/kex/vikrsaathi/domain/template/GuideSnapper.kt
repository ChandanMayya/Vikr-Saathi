package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.GuideOrientation
import com.kex.vikrsaathi.data.model.template.TemplateGuide
import kotlin.math.abs

object GuideSnapper {
    const val SNAP_THRESHOLD_PT = 10f

    /**
     * Snaps bounds to nearby guides. A vertical guide to the left of the object
     * aligns the left edge; to the right aligns the right edge. Horizontal guides
     * align the top or bottom edge based on whether the guide is above or below center.
     */
    fun snapBounds(
        bounds: ElementBounds,
        guides: List<TemplateGuide>,
        enabled: Boolean
    ): ElementBounds {
        if (!enabled || guides.isEmpty()) return bounds

        var x = bounds.x
        var y = bounds.y
        val width = bounds.width
        val height = bounds.height
        val centerX = x + width / 2f
        val centerY = y + height / 2f

        var bestXSnap: Float? = null
        var bestXDist = SNAP_THRESHOLD_PT
        var bestYSnap: Float? = null
        var bestYDist = SNAP_THRESHOLD_PT

        guides.forEach { guide ->
            when (guide.orientation) {
                GuideOrientation.VERTICAL -> {
                    val g = guide.positionPt
                    if (g >= centerX) {
                        val dist = abs(x + width - g)
                        if (dist <= SNAP_THRESHOLD_PT && dist < bestXDist) {
                            bestXDist = dist
                            bestXSnap = g - width
                        }
                    } else {
                        val dist = abs(x - g)
                        if (dist <= SNAP_THRESHOLD_PT && dist < bestXDist) {
                            bestXDist = dist
                            bestXSnap = g
                        }
                    }
                }
                GuideOrientation.HORIZONTAL -> {
                    val g = guide.positionPt
                    if (g >= centerY) {
                        val dist = abs(y + height - g)
                        if (dist <= SNAP_THRESHOLD_PT && dist < bestYDist) {
                            bestYDist = dist
                            bestYSnap = g - height
                        }
                    } else {
                        val dist = abs(y - g)
                        if (dist <= SNAP_THRESHOLD_PT && dist < bestYDist) {
                            bestYDist = dist
                            bestYSnap = g
                        }
                    }
                }
            }
        }

        if (bestXSnap != null) x = bestXSnap
        if (bestYSnap != null) y = bestYSnap
        return bounds.copy(x = x, y = y)
    }
}
