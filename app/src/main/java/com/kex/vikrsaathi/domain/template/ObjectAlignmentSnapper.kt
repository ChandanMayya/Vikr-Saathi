package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.ElementBounds
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class AlignmentAxis {
    VERTICAL,
    HORIZONTAL
}

data class ObjectAlignmentLine(
    val axis: AlignmentAxis,
    /** X for vertical lines, Y for horizontal lines (page points). */
    val positionPt: Float,
    val spanStartPt: Float,
    val spanEndPt: Float,
    val distanceLabel: AlignmentDistanceLabel? = null
)

/** Gap between aligned objects, measured perpendicular to the alignment line. */
data class AlignmentDistanceLabel(
    val distancePt: Float,
    val gapStartPt: Float,
    val gapEndPt: Float
)

data class ObjectAlignmentSnapResult(
    val bounds: ElementBounds,
    val lines: List<ObjectAlignmentLine>
)

object ObjectAlignmentSnapper {

    const val SNAP_THRESHOLD_PT = 10f
    const val NEARBY_THRESHOLD_PT = 80f

    fun snapBounds(
        bounds: ElementBounds,
        references: List<ElementBounds>,
        enabled: Boolean,
        nearbyThreshold: Float = NEARBY_THRESHOLD_PT
    ): ObjectAlignmentSnapResult {
        if (!enabled || references.isEmpty()) {
            return ObjectAlignmentSnapResult(bounds, emptyList())
        }

        val candidates = references
            .filter { edgeToEdgeGap(bounds, it) <= nearbyThreshold }
            .sortedBy { edgeToEdgeGap(bounds, it) }
        if (candidates.isEmpty()) {
            return ObjectAlignmentSnapResult(bounds, emptyList())
        }

        val xSnap = findBestXSnap(bounds, candidates, nearbyThreshold)
        val ySnap = findBestYSnap(bounds, candidates, nearbyThreshold)

        val snappedBounds = bounds.copy(
            x = xSnap?.position ?: bounds.x,
            y = ySnap?.position ?: bounds.y
        )

        val lines = buildList {
            xSnap?.line?.let { add(it) }
            ySnap?.line?.let { add(it) }
        }

        return ObjectAlignmentSnapResult(snappedBounds, lines)
    }

    private data class AxisSnap(
        val position: Float,
        val line: ObjectAlignmentLine
    )

    private data class AxisPoint(
        val value: Float,
        val resolve: (Float) -> Float
    )

    private data class SnapCandidate(
        val alignDistance: Float,
        val adjacencyScore: Float,
        val newPosition: Float,
        val reference: ElementBounds,
        val guidePosition: Float
    )

    private fun findBestXSnap(
        bounds: ElementBounds,
        candidates: List<ElementBounds>,
        nearbyThreshold: Float
    ): AxisSnap? {
        val draggedX = xPositions(bounds)
        var best: SnapCandidate? = null

        candidates.forEach { reference ->
            if (!canSnapXAxis(bounds, reference, nearbyThreshold)) return@forEach
            val refX = xPositions(reference)
            draggedX.forEach { dragPoint ->
                refX.forEach { refPoint ->
                    val dist = abs(dragPoint.value - refPoint.value)
                    if (dist > SNAP_THRESHOLD_PT) return@forEach
                    val adjacency = verticalAdjacencyScore(bounds, reference)
                    val candidate = SnapCandidate(
                        alignDistance = dist,
                        adjacencyScore = adjacency,
                        newPosition = dragPoint.resolve(refPoint.value),
                        reference = reference,
                        guidePosition = refPoint.value
                    )
                    if (isBetterSnapCandidate(candidate, best)) {
                        best = candidate
                    }
                }
            }
        }

        val match = best ?: return null
        val snapped = bounds.copy(x = match.newPosition)
        return AxisSnap(
            position = match.newPosition,
            line = verticalLine(snapped, match.reference, match.guidePosition)
        )
    }

    private fun findBestYSnap(
        bounds: ElementBounds,
        candidates: List<ElementBounds>,
        nearbyThreshold: Float
    ): AxisSnap? {
        val draggedY = yPositions(bounds)
        var best: SnapCandidate? = null

        candidates.forEach { reference ->
            if (!canSnapYAxis(bounds, reference, nearbyThreshold)) return@forEach
            val refY = yPositions(reference)
            draggedY.forEach { dragPoint ->
                refY.forEach { refPoint ->
                    val dist = abs(dragPoint.value - refPoint.value)
                    if (dist > SNAP_THRESHOLD_PT) return@forEach
                    val adjacency = horizontalAdjacencyScore(bounds, reference)
                    val candidate = SnapCandidate(
                        alignDistance = dist,
                        adjacencyScore = adjacency,
                        newPosition = dragPoint.resolve(refPoint.value),
                        reference = reference,
                        guidePosition = refPoint.value
                    )
                    if (isBetterSnapCandidate(candidate, best)) {
                        best = candidate
                    }
                }
            }
        }

        val match = best ?: return null
        val snapped = bounds.copy(y = match.newPosition)
        return AxisSnap(
            position = match.newPosition,
            line = horizontalLine(snapped, match.reference, match.guidePosition)
        )
    }

    /** Prefer tighter alignment, then the most adjacent reference on the perpendicular axis. */
    private fun isBetterSnapCandidate(
        candidate: SnapCandidate,
        best: SnapCandidate?
    ): Boolean {
        if (best == null) return true
        if (candidate.alignDistance < best.alignDistance - 0.01f) return true
        if (candidate.alignDistance > best.alignDistance + 0.01f) return false
        return candidate.adjacencyScore < best.adjacencyScore
    }

    /**
     * Vertical alignment (same X): side-by-side in a row, or stacked in the same column.
     */
    private fun canSnapXAxis(
        dragged: ElementBounds,
        reference: ElementBounds,
        nearbyThreshold: Float
    ): Boolean {
        val hOverlap = horizontalOverlap(dragged, reference)
        val vOverlap = verticalOverlap(dragged, reference)
        val hGap = horizontalGap(dragged, reference)
        val vGap = verticalGap(dragged, reference)

        if (vOverlap > 0f && (hGap == null || hGap <= nearbyThreshold)) return true
        if (hOverlap > 0f && vGap != null && vGap <= nearbyThreshold) return true
        return false
    }

    /**
     * Horizontal alignment (same Y): only stacked above/below in the same column.
     * Does not snap to a wide object above when placing beside it in a different column.
     */
    private fun canSnapYAxis(
        dragged: ElementBounds,
        reference: ElementBounds,
        nearbyThreshold: Float
    ): Boolean {
        val hOverlap = horizontalOverlap(dragged, reference)
        val vGap = verticalGap(dragged, reference)
        return vGap != null && vGap <= nearbyThreshold && hOverlap > 0f
    }

    /** Lower score = more adjacent to the left/right (for X-align tie-break). */
    private fun horizontalAdjacencyScore(dragged: ElementBounds, reference: ElementBounds): Float {
        val overlap = horizontalOverlap(dragged, reference)
        if (overlap > 0f) return -overlap
        return horizontalGap(dragged, reference) ?: Float.MAX_VALUE
    }

    /** Lower score = more adjacent above/below (for Y-align tie-break). */
    private fun verticalAdjacencyScore(dragged: ElementBounds, reference: ElementBounds): Float {
        val overlap = verticalOverlap(dragged, reference)
        if (overlap > 0f) return -overlap
        return verticalGap(dragged, reference) ?: Float.MAX_VALUE
    }

    private fun edgeToEdgeGap(a: ElementBounds, b: ElementBounds): Float {
        val hGap = when {
            a.x + a.width < b.x -> b.x - (a.x + a.width)
            b.x + b.width < a.x -> a.x - (b.x + b.width)
            else -> 0f
        }
        val vGap = when {
            a.y + a.height < b.y -> b.y - (a.y + a.height)
            b.y + b.height < a.y -> a.y - (b.y + b.height)
            else -> 0f
        }
        return when {
            hGap > 0f && vGap > 0f -> max(hGap, vGap)
            else -> max(hGap, vGap)
        }
    }

    private fun horizontalOverlap(a: ElementBounds, b: ElementBounds): Float {
        val left = max(a.x, b.x)
        val right = min(a.x + a.width, b.x + b.width)
        return max(0f, right - left)
    }

    private fun verticalOverlap(a: ElementBounds, b: ElementBounds): Float {
        val top = max(a.y, b.y)
        val bottom = min(a.y + a.height, b.y + b.height)
        return max(0f, bottom - top)
    }

    private fun horizontalGap(a: ElementBounds, b: ElementBounds): Float? {
        return when {
            a.x + a.width <= b.x -> b.x - (a.x + a.width)
            b.x + b.width <= a.x -> a.x - (b.x + b.width)
            else -> null
        }
    }

    private fun verticalGap(a: ElementBounds, b: ElementBounds): Float? {
        return when {
            a.y + a.height <= b.y -> b.y - (a.y + a.height)
            b.y + b.height <= a.y -> a.y - (b.y + b.height)
            else -> null
        }
    }

    private fun xPositions(bounds: ElementBounds): List<AxisPoint> {
        val left = bounds.x
        val center = bounds.x + bounds.width / 2f
        val right = bounds.x + bounds.width
        val width = bounds.width
        return listOf(
            AxisPoint(left) { match -> match },
            AxisPoint(center) { match -> match - width / 2f },
            AxisPoint(right) { match -> match - width }
        )
    }

    private fun yPositions(bounds: ElementBounds): List<AxisPoint> {
        val top = bounds.y
        val center = bounds.y + bounds.height / 2f
        val bottom = bounds.y + bounds.height
        val height = bounds.height
        return listOf(
            AxisPoint(top) { match -> match },
            AxisPoint(center) { match -> match - height / 2f },
            AxisPoint(bottom) { match -> match - height }
        )
    }

    private fun verticalLine(
        dragged: ElementBounds,
        reference: ElementBounds,
        x: Float
    ): ObjectAlignmentLine {
        val top = min(dragged.y, reference.y) - 6f
        val bottom = max(dragged.y + dragged.height, reference.y + reference.height) + 6f
        return ObjectAlignmentLine(
            axis = AlignmentAxis.VERTICAL,
            positionPt = x,
            spanStartPt = top,
            spanEndPt = bottom,
            distanceLabel = verticalGapLabel(dragged, reference)
        )
    }

    private fun horizontalLine(
        dragged: ElementBounds,
        reference: ElementBounds,
        y: Float
    ): ObjectAlignmentLine {
        val left = min(dragged.x, reference.x) - 6f
        val right = max(dragged.x + dragged.width, reference.x + reference.width) + 6f
        return ObjectAlignmentLine(
            axis = AlignmentAxis.HORIZONTAL,
            positionPt = y,
            spanStartPt = left,
            spanEndPt = right,
            distanceLabel = horizontalGapLabel(dragged, reference)
        )
    }

    private fun verticalGapLabel(
        dragged: ElementBounds,
        reference: ElementBounds
    ): AlignmentDistanceLabel? {
        val draggedBottom = dragged.y + dragged.height
        val referenceBottom = reference.y + reference.height
        return when {
            dragged.y >= referenceBottom -> {
                val gap = dragged.y - referenceBottom
                if (gap < 1f) return null
                AlignmentDistanceLabel(gap, referenceBottom, dragged.y)
            }
            reference.y >= draggedBottom -> {
                val gap = reference.y - draggedBottom
                if (gap < 1f) return null
                AlignmentDistanceLabel(gap, draggedBottom, reference.y)
            }
            else -> null
        }
    }

    private fun horizontalGapLabel(
        dragged: ElementBounds,
        reference: ElementBounds
    ): AlignmentDistanceLabel? {
        val draggedRight = dragged.x + dragged.width
        val referenceRight = reference.x + reference.width
        return when {
            dragged.x >= referenceRight -> {
                val gap = dragged.x - referenceRight
                if (gap < 1f) return null
                AlignmentDistanceLabel(gap, referenceRight, dragged.x)
            }
            reference.x >= draggedRight -> {
                val gap = reference.x - draggedRight
                if (gap < 1f) return null
                AlignmentDistanceLabel(gap, draggedRight, reference.x)
            }
            else -> null
        }
    }
}
