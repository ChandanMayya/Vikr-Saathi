package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.TemplateElement
import kotlin.math.cos
import kotlin.math.sin

object ElementRotationHelper {

    fun normalizeDegrees(degrees: Float): Float {
        var value = degrees % 360f
        if (value < 0f) value += 360f
        return value
    }

    fun centerOf(bounds: ElementBounds): Pair<Float, Float> =
        bounds.x + bounds.width / 2f to bounds.y + bounds.height / 2f

    fun rotatePoint(
        x: Float,
        y: Float,
        pivotX: Float,
        pivotY: Float,
        degrees: Float
    ): Pair<Float, Float> {
        if (degrees == 0f) return x to y
        val radians = Math.toRadians(degrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val dx = x - pivotX
        val dy = y - pivotY
        return pivotX + dx * cos - dy * sin to pivotY + dx * sin + dy * cos
    }

    fun angleDegrees(pivotX: Float, pivotY: Float, x: Float, y: Float): Float {
        val radians = kotlin.math.atan2((y - pivotY).toDouble(), (x - pivotX).toDouble())
        return Math.toDegrees(radians).toFloat()
    }

    fun selectionPivot(elements: List<TemplateElement>): Pair<Float, Float>? {
        if (elements.isEmpty()) return null
        val ids = elements.map { it.id }.toSet()
        val union = ElementBoundsHelper.unionBounds(elements, ids) ?: return null
        return centerOf(union)
    }

    fun rotateElement(
        element: TemplateElement,
        deltaDegrees: Float,
        pivotX: Float,
        pivotY: Float
    ): TemplateElement {
        if (deltaDegrees == 0f) return element
        val bounds = element.bounds
        val (centerX, centerY) = centerOf(bounds)
        val (newCenterX, newCenterY) = rotatePoint(centerX, centerY, pivotX, pivotY, deltaDegrees)
        return element.copy(
            bounds = bounds.copy(
                x = newCenterX - bounds.width / 2f,
                y = newCenterY - bounds.height / 2f
            ),
            rotationDegrees = normalizeDegrees(element.rotationDegrees + deltaDegrees)
        )
    }

    fun rotateElements(
        elements: List<TemplateElement>,
        ids: Set<String>,
        deltaDegrees: Float
    ): List<TemplateElement> {
        if (ids.isEmpty() || deltaDegrees == 0f) return elements
        val targets = elements.filter { it.id in ids }
        val pivot = selectionPivot(targets) ?: return elements
        return elements.map { element ->
            if (element.id in ids) {
                rotateElement(element, deltaDegrees, pivot.first, pivot.second)
            } else {
                element
            }
        }
    }

    fun rotateElementsFromSnapshot(
        snapshotElements: List<TemplateElement>,
        ids: Set<String>,
        deltaDegrees: Float
    ): List<TemplateElement> = rotateElements(snapshotElements, ids, deltaDegrees)

    fun axisAlignedBounds(element: TemplateElement): ElementBounds {
        val rotation = element.rotationDegrees
        val bounds = element.bounds
        if (rotation == 0f) return bounds
        val (centerX, centerY) = centerOf(bounds)
        val corners = listOf(
            bounds.x to bounds.y,
            bounds.x + bounds.width to bounds.y,
            bounds.x + bounds.width to bounds.y + bounds.height,
            bounds.x to bounds.y + bounds.height
        ).map { (x, y) -> rotatePoint(x, y, centerX, centerY, rotation) }
        val xs = corners.map { it.first }
        val ys = corners.map { it.second }
        val minX = xs.min()
        val minY = ys.min()
        return ElementBounds(
            x = minX,
            y = minY,
            width = xs.max() - minX,
            height = ys.max() - minY
        )
    }

    fun containsPoint(element: TemplateElement, pageX: Float, pageY: Float): Boolean {
        val bounds = element.bounds
        val (centerX, centerY) = centerOf(bounds)
        val (localX, localY) = rotatePoint(pageX, pageY, centerX, centerY, -element.rotationDegrees)
        return localX >= bounds.x &&
            localX <= bounds.x + bounds.width &&
            localY >= bounds.y &&
            localY <= bounds.y + bounds.height
    }
}
