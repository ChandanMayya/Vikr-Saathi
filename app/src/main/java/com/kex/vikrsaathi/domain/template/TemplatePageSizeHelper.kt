package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.GuideOrientation
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.PaperSizeCatalog
import com.kex.vikrsaathi.data.model.template.PaperSizeId
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.data.model.template.TemplateGuide

/**
 * Applies a new page size to a template, optionally scaling content or clamping it.
 */
object TemplatePageSizeHelper {

    fun hasLayoutContent(template: InvoiceTemplate): Boolean =
        template.elements.isNotEmpty() || template.guides.isNotEmpty()

    /**
     * Sets sheet type and page dimensions. Portrait base size is oriented to match
     * the template's current landscape/portrait state. When [scaleContent] is true,
     * elements, guides, and margins are scaled by new/old width and height ratios;
     * otherwise content is clamped to the new page.
     */
    fun withPageSize(
        template: InvoiceTemplate,
        sheetType: PaperSizeId,
        portraitWidthPt: Int,
        portraitHeightPt: Int,
        scaleContent: Boolean
    ): InvoiceTemplate {
        val landscape = TemplateOrientationHelper.isLandscape(template)
        val targetWidth = if (landscape) portraitHeightPt else portraitWidthPt
        val targetHeight = if (landscape) portraitWidthPt else portraitHeightPt
        return applyDimensions(
            template = template,
            sheetType = sheetType.name,
            newWidthPt = PaperSizeCatalog.clampSizePt(targetWidth),
            newHeightPt = PaperSizeCatalog.clampSizePt(targetHeight),
            scaleContent = scaleContent
        )
    }

    fun applyDimensions(
        template: InvoiceTemplate,
        sheetType: String,
        newWidthPt: Int,
        newHeightPt: Int,
        scaleContent: Boolean
    ): InvoiceTemplate {
        val oldW = template.pageWidthPt.toFloat().coerceAtLeast(1f)
        val oldH = template.pageHeightPt.toFloat().coerceAtLeast(1f)
        val newW = PaperSizeCatalog.clampSizePt(newWidthPt)
        val newH = PaperSizeCatalog.clampSizePt(newHeightPt)
        if (template.pageWidthPt == newW &&
            template.pageHeightPt == newH &&
            template.sheetType == sheetType
        ) {
            return template
        }

        if (!scaleContent || !hasLayoutContent(template)) {
            return template.copy(
                sheetType = sheetType,
                pageWidthPt = newW,
                pageHeightPt = newH,
                elements = template.elements.map { clampElement(it, newW, newH) },
                guides = template.guides.map { clampGuide(it, newW, newH) }
            )
        }

        val sx = newW / oldW
        val sy = newH / oldH
        return template.copy(
            sheetType = sheetType,
            pageWidthPt = newW,
            pageHeightPt = newH,
            marginLeft = template.marginLeft * sx,
            marginTop = template.marginTop * sy,
            marginRight = template.marginRight * sx,
            marginBottom = template.marginBottom * sy,
            elements = template.elements.map { scaleElement(it, sx, sy, newW, newH) },
            guides = template.guides.map { scaleGuide(it, sx, sy, newW, newH) }
        )
    }

    private fun scaleElement(
        element: TemplateElement,
        sx: Float,
        sy: Float,
        pageW: Int,
        pageH: Int
    ): TemplateElement {
        val b = element.bounds
        val width = (b.width * sx).coerceAtLeast(20f)
        val height = (b.height * sy).coerceAtLeast(12f)
        return element.copy(
            bounds = clampBounds(
                ElementBounds(
                    x = b.x * sx,
                    y = b.y * sy,
                    width = width,
                    height = height
                ),
                pageW,
                pageH
            )
        )
    }

    private fun clampElement(element: TemplateElement, pageW: Int, pageH: Int): TemplateElement =
        element.copy(bounds = clampBounds(element.bounds, pageW, pageH))

    private fun clampBounds(bounds: ElementBounds, pageW: Int, pageH: Int): ElementBounds {
        val width = bounds.width.coerceIn(20f, pageW.toFloat())
        val height = bounds.height.coerceIn(12f, pageH.toFloat())
        return ElementBounds(
            x = bounds.x.coerceIn(0f, (pageW - width).coerceAtLeast(0f)),
            y = bounds.y.coerceIn(0f, (pageH - height).coerceAtLeast(0f)),
            width = width,
            height = height
        )
    }

    private fun scaleGuide(
        guide: TemplateGuide,
        sx: Float,
        sy: Float,
        pageW: Int,
        pageH: Int
    ): TemplateGuide {
        val scale = if (guide.orientation == GuideOrientation.VERTICAL) sx else sy
        return clampGuide(guide.copy(positionPt = guide.positionPt * scale), pageW, pageH)
    }

    private fun clampGuide(guide: TemplateGuide, pageW: Int, pageH: Int): TemplateGuide {
        val max = when (guide.orientation) {
            GuideOrientation.VERTICAL -> pageW.toFloat()
            GuideOrientation.HORIZONTAL -> pageH.toFloat()
        }
        return guide.copy(positionPt = guide.positionPt.coerceIn(0f, max))
    }
}
