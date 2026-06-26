package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.TemplateElement

object ElementBoundsHelper {

    fun unionBounds(elements: List<TemplateElement>, ids: Set<String>): ElementBounds? {
        val bounds = elements.filter { ids.contains(it.id) }.map { it.bounds }
        if (bounds.isEmpty()) return null
        val left = bounds.minOf { it.x }
        val top = bounds.minOf { it.y }
        val right = bounds.maxOf { it.x + it.width }
        val bottom = bounds.maxOf { it.y + it.height }
        return ElementBounds(left, top, right - left, bottom - top)
    }

    fun moveByDelta(
        elements: List<TemplateElement>,
        ids: Set<String>,
        dx: Float,
        dy: Float,
        pageWidthPt: Int,
        pageHeightPt: Int
    ): List<TemplateElement> {
        return elements.map { element ->
            if (!ids.contains(element.id)) return@map element
            val bounds = element.bounds
            element.copy(
                bounds = bounds.copy(
                    x = (bounds.x + dx).coerceIn(0f, pageWidthPt - bounds.width),
                    y = (bounds.y + dy).coerceIn(0f, pageHeightPt - bounds.height)
                )
            )
        }
    }

    fun scaleSelection(
        elements: List<TemplateElement>,
        ids: Set<String>,
        startUnion: ElementBounds,
        newUnion: ElementBounds,
        pageWidthPt: Int,
        pageHeightPt: Int
    ): List<TemplateElement> {
        if (startUnion.width <= 0f || startUnion.height <= 0f) return elements
        return elements.map { element ->
            if (!ids.contains(element.id)) return@map element
            val bounds = element.bounds
            val relX = (bounds.x - startUnion.x) / startUnion.width
            val relY = (bounds.y - startUnion.y) / startUnion.height
            val relW = bounds.width / startUnion.width
            val relH = bounds.height / startUnion.height
            val newX = newUnion.x + relX * newUnion.width
            val newY = newUnion.y + relY * newUnion.height
            val newW = (relW * newUnion.width).coerceAtLeast(20f)
            val newH = (relH * newUnion.height).coerceAtLeast(12f)
            element.copy(
                bounds = ElementBounds(
                    x = newX.coerceIn(0f, pageWidthPt - newW),
                    y = newY.coerceIn(0f, pageHeightPt - newH),
                    width = newW.coerceAtMost(pageWidthPt - newX),
                    height = newH.coerceAtMost(pageHeightPt - newY)
                )
            )
        }
    }
}
