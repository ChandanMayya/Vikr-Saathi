package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.TemplateElement

object ElementSelectionHelper {

    fun expandWithGroup(elements: List<TemplateElement>, elementId: String): Set<String> {
        val element = elements.find { it.id == elementId } ?: return setOf(elementId)
        val groupId = element.groupId ?: return setOf(elementId)
        return elements.filter { it.groupId == groupId }.map { it.id }.toSet()
    }

    fun expandWithGroups(elements: List<TemplateElement>, ids: Set<String>): Set<String> {
        return ids.flatMap { expandWithGroup(elements, it) }.toSet()
    }

    fun isGroupSelected(elements: List<TemplateElement>, ids: Set<String>): Boolean {
        return ids.any { id ->
            elements.find { it.id == id }?.groupId != null
        }
    }

    fun selectionHasLocked(elements: List<TemplateElement>, ids: Set<String>): Boolean {
        return elements.any { it.id in ids && it.locked }
    }

    fun allSelectedLocked(elements: List<TemplateElement>, ids: Set<String>): Boolean {
        if (ids.isEmpty()) return false
        return ids.all { id -> elements.find { it.id == id }?.locked == true }
    }
}
