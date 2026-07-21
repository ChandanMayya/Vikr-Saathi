package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.GuideOrientation
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.data.model.template.TemplateGuide

/**
 * Swaps page width/height and rotates elements and guides 90° so existing layouts
 * stay usable when switching between portrait and landscape.
 */
object TemplateOrientationHelper {

    fun isLandscape(template: InvoiceTemplate): Boolean =
        template.pageWidthPt > template.pageHeightPt

    fun withOrientation(template: InvoiceTemplate, landscape: Boolean): InvoiceTemplate {
        if (isLandscape(template) == landscape) return template
        return if (landscape) toLandscape(template) else toPortrait(template)
    }

    private fun toLandscape(template: InvoiceTemplate): InvoiceTemplate {
        val pageWidth = template.pageWidthPt.toFloat()
        return template.copy(
            pageWidthPt = template.pageHeightPt,
            pageHeightPt = template.pageWidthPt,
            marginLeft = template.marginTop,
            marginTop = template.marginRight,
            marginRight = template.marginBottom,
            marginBottom = template.marginLeft,
            elements = template.elements.map { rotateElement90CounterClockwise(it, pageWidth) },
            guides = template.guides.map { rotateGuide90CounterClockwise(it, pageWidth) }
        )
    }

    private fun toPortrait(template: InvoiceTemplate): InvoiceTemplate {
        val pageHeight = template.pageHeightPt.toFloat()
        return template.copy(
            pageWidthPt = template.pageHeightPt,
            pageHeightPt = template.pageWidthPt,
            marginLeft = template.marginBottom,
            marginTop = template.marginLeft,
            marginRight = template.marginTop,
            marginBottom = template.marginRight,
            elements = template.elements.map { rotateElement90Clockwise(it, pageHeight) },
            guides = template.guides.map { rotateGuide90Clockwise(it, pageHeight) }
        )
    }

    private fun rotateElement90CounterClockwise(
        element: TemplateElement,
        pageWidth: Float
    ): TemplateElement {
        val bounds = element.bounds
        return element.copy(
            bounds = ElementBounds(
                x = bounds.y,
                y = pageWidth - bounds.x - bounds.width,
                width = bounds.height,
                height = bounds.width
            )
        )
    }

    private fun rotateElement90Clockwise(
        element: TemplateElement,
        pageHeight: Float
    ): TemplateElement {
        val bounds = element.bounds
        return element.copy(
            bounds = ElementBounds(
                x = pageHeight - bounds.y - bounds.height,
                y = bounds.x,
                width = bounds.height,
                height = bounds.width
            )
        )
    }

    private fun rotateGuide90CounterClockwise(
        guide: TemplateGuide,
        pageWidth: Float
    ): TemplateGuide = when (guide.orientation) {
        GuideOrientation.HORIZONTAL -> guide.copy(
            orientation = GuideOrientation.VERTICAL,
            positionPt = guide.positionPt
        )
        GuideOrientation.VERTICAL -> guide.copy(
            orientation = GuideOrientation.HORIZONTAL,
            positionPt = pageWidth - guide.positionPt
        )
    }

    private fun rotateGuide90Clockwise(
        guide: TemplateGuide,
        pageHeight: Float
    ): TemplateGuide = when (guide.orientation) {
        GuideOrientation.VERTICAL -> guide.copy(
            orientation = GuideOrientation.HORIZONTAL,
            positionPt = guide.positionPt
        )
        GuideOrientation.HORIZONTAL -> guide.copy(
            orientation = GuideOrientation.VERTICAL,
            positionPt = pageHeight - guide.positionPt
        )
    }
}
