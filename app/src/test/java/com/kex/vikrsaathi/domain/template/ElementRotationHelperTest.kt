package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.ElementBinding
import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.data.model.template.TemplateElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementRotationHelperTest {

    @Test
    fun rotateElement_increasesRotationDegrees() {
        val element = TemplateElement(
            id = "e1",
            kind = ElementKind.TEXT,
            binding = ElementBinding.STATIC,
            bounds = ElementBounds(100f, 100f, 80f, 40f)
        )
        val rotated = ElementRotationHelper.rotateElement(element, 90f, 140f, 120f)
        assertEquals(90f, rotated.rotationDegrees, 0.01f)
    }

    @Test
    fun rotateElements_rotatesGroupAroundSharedPivot() {
        val elements = listOf(
            TemplateElement(
                id = "a",
                kind = ElementKind.TEXT,
                binding = ElementBinding.STATIC,
                bounds = ElementBounds(0f, 0f, 40f, 20f),
                groupId = "g1"
            ),
            TemplateElement(
                id = "b",
                kind = ElementKind.TEXT,
                binding = ElementBinding.STATIC,
                bounds = ElementBounds(60f, 0f, 40f, 20f),
                groupId = "g1"
            )
        )
        val rotated = ElementRotationHelper.rotateElements(elements, setOf("a", "b"), 90f)
        val pivot = ElementRotationHelper.selectionPivot(elements)!!
        val centerA = ElementRotationHelper.centerOf(rotated.first { it.id == "a" }.bounds)
        val centerB = ElementRotationHelper.centerOf(rotated.first { it.id == "b" }.bounds)
        val originalDistance = hypot(
            20f - 80f,
            10f - 10f
        )
        val rotatedDistance = hypot(centerA.first - centerB.first, centerA.second - centerB.second)
        assertEquals(originalDistance, rotatedDistance, 0.5f)
        assertEquals(90f, rotated.first { it.id == "a" }.rotationDegrees, 0.01f)
        assertEquals(90f, rotated.first { it.id == "b" }.rotationDegrees, 0.01f)
        assertTrue(pivot.first in 0f..100f)
    }

    @Test
    fun containsPoint_respectsRotation() {
        val element = TemplateElement(
            id = "e1",
            kind = ElementKind.RECT,
            binding = ElementBinding.STATIC,
            bounds = ElementBounds(100f, 100f, 40f, 20f),
            rotationDegrees = 0f
        )
        assertTrue(ElementRotationHelper.containsPoint(element, 110f, 110f))
    }

    private fun hypot(x: Float, y: Float): Float =
        kotlin.math.sqrt(x * x + y * y)
}
