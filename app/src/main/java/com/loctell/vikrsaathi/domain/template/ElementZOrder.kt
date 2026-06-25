package com.loctell.vikrsaathi.domain.template

import com.loctell.vikrsaathi.data.model.template.TemplateElement

object ElementZOrder {

    enum class Direction {
        FORWARD,
        BACKWARD,
        TO_FRONT,
        TO_BACK
    }

    fun reorder(elements: List<TemplateElement>, elementId: String, direction: Direction): List<TemplateElement> {
        val sorted = elements.sortedBy { it.zIndex }.toMutableList()
        val index = sorted.indexOfFirst { it.id == elementId }
        if (index < 0) return elements

        val targetIndex = when (direction) {
            Direction.FORWARD -> (index + 1).coerceAtMost(sorted.lastIndex)
            Direction.BACKWARD -> (index - 1).coerceAtLeast(0)
            Direction.TO_FRONT -> sorted.lastIndex
            Direction.TO_BACK -> 0
        }
        if (targetIndex == index) return elements

        val item = sorted.removeAt(index)
        sorted.add(targetIndex, item)
        return sorted.mapIndexed { z, element -> element.copy(zIndex = z) }
    }
}
