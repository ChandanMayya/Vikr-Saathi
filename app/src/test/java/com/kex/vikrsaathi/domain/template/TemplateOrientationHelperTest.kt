package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.ElementBinding
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.data.model.template.GuideOrientation
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.data.model.template.TemplateGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateOrientationHelperTest {

    @Test
    fun portraitToLandscape_swapsPageDimensions() {
        val portrait = InvoiceTemplate(
            name = "Test",
            pageWidthPt = 595,
            pageHeightPt = 842
        )
        val landscape = TemplateOrientationHelper.withOrientation(portrait, landscape = true)
        assertEquals(842, landscape.pageWidthPt)
        assertEquals(595, landscape.pageHeightPt)
        assertTrue(TemplateOrientationHelper.isLandscape(landscape))
    }

    @Test
    fun orientationRoundTrip_restoresElementBounds() {
        val element = TemplateElement(
            id = "e1",
            kind = ElementKind.RECT,
            binding = ElementBinding.STATIC,
            bounds = ElementBounds(x = 10f, y = 20f, width = 100f, height = 50f)
        )
        val portrait = InvoiceTemplate(
            name = "Test",
            pageWidthPt = 595,
            pageHeightPt = 842,
            elements = listOf(element)
        )
        val landscape = TemplateOrientationHelper.withOrientation(portrait, landscape = true)
        val backToPortrait = TemplateOrientationHelper.withOrientation(landscape, landscape = false)
        val restored = backToPortrait.elements.single().bounds
        assertEquals(element.bounds, restored)
    }

    @Test
    fun portraitToLandscape_rotatesGuides() {
        val guides = listOf(
            TemplateGuide("h1", GuideOrientation.HORIZONTAL, 100f),
            TemplateGuide("v1", GuideOrientation.VERTICAL, 200f)
        )
        val portrait = InvoiceTemplate(
            name = "Test",
            pageWidthPt = 595,
            pageHeightPt = 842,
            guides = guides
        )
        val landscape = TemplateOrientationHelper.withOrientation(portrait, landscape = true)
        val horizontal = landscape.guides.first { it.id == "h1" }
        val vertical = landscape.guides.first { it.id == "v1" }
        assertEquals(GuideOrientation.VERTICAL, horizontal.orientation)
        assertEquals(100f, horizontal.positionPt, 0.01f)
        assertEquals(GuideOrientation.HORIZONTAL, vertical.orientation)
        assertEquals(395f, vertical.positionPt, 0.01f)
    }

    @Test
    fun sameOrientation_isNoOp() {
        val portrait = InvoiceTemplate(name = "Test", pageWidthPt = 595, pageHeightPt = 842)
        assertFalse(TemplateOrientationHelper.isLandscape(portrait))
        assertEquals(portrait, TemplateOrientationHelper.withOrientation(portrait, landscape = false))
    }
}
