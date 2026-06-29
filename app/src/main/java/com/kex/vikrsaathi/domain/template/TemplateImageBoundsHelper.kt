package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.ElementBinding
import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.ElementKind
import com.kex.vikrsaathi.data.model.template.ImageScaleMode
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.TemplateElement
import kotlin.math.abs

object TemplateImageBoundsHelper {

    private const val TOLERANCE_PT = 2f

    /**
     * Returns resized bounds when the layout box aspect ratio does not match the image
     * for fit-style scale modes; otherwise null.
     */
    fun suggestedBoundsForImage(
        bounds: ElementBounds,
        imageWidth: Int,
        imageHeight: Int,
        scaleMode: ImageScaleMode
    ): ElementBounds? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        val aspect = imageWidth.toFloat() / imageHeight
        val suggested = when (scaleMode) {
            ImageScaleMode.FIT_WIDTH -> bounds.copy(height = bounds.width / aspect)
            ImageScaleMode.FIT_HEIGHT -> bounds.copy(width = bounds.height * aspect)
            ImageScaleMode.FIT -> {
                val ratio = minOf(bounds.width / imageWidth, bounds.height / imageHeight)
                bounds.copy(
                    width = imageWidth * ratio,
                    height = imageHeight * ratio
                )
            }
            else -> return null
        }
        if (!boundsDiffer(bounds, suggested)) return null
        return suggested
    }

    fun needsBoundsAdjustment(
        bounds: ElementBounds,
        imageWidth: Int,
        imageHeight: Int,
        scaleMode: ImageScaleMode
    ): Boolean = suggestedBoundsForImage(bounds, imageWidth, imageHeight, scaleMode) != null

    fun resizeElementAndShiftBelow(
        template: InvoiceTemplate,
        elementId: String,
        newBounds: ElementBounds
    ): InvoiceTemplate {
        val old = template.elements.find { it.id == elementId } ?: return template
        val oldBottom = old.bounds.y + old.bounds.height
        val deltaH = newBounds.height - old.bounds.height
        return template.copy(
            elements = template.elements.map { element ->
                when {
                    element.id == elementId -> element.copy(bounds = newBounds)
                    deltaH != 0f && element.bounds.y >= oldBottom - 0.5f ->
                        element.copy(bounds = element.bounds.copy(y = element.bounds.y + deltaH))
                    else -> element
                }
            }
        )
    }

    fun matchingImageElements(
        template: InvoiceTemplate,
        bindingKeyName: String
    ): List<TemplateElement> {
        return template.elements.filter { element ->
            element.kind == ElementKind.IMAGE &&
                element.binding == ElementBinding.DYNAMIC &&
                element.content["bindingKey"] == bindingKeyName
        }
    }

    fun applySuggestedBoundsToTemplate(
        template: InvoiceTemplate,
        bindingKeyName: String,
        imageWidth: Int,
        imageHeight: Int
    ): Pair<InvoiceTemplate, Int> {
        var updated = template
        var changeCount = 0
        matchingImageElements(template, bindingKeyName).forEach { element ->
            val suggested = suggestedBoundsForImage(
                bounds = element.bounds,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                scaleMode = element.style.imageScaleMode
            ) ?: return@forEach
            updated = resizeElementAndShiftBelow(updated, element.id, suggested)
            changeCount++
        }
        return updated to changeCount
    }

    private fun boundsDiffer(current: ElementBounds, suggested: ElementBounds): Boolean {
        return abs(suggested.width - current.width) > TOLERANCE_PT ||
            abs(suggested.height - current.height) > TOLERANCE_PT
    }
}
