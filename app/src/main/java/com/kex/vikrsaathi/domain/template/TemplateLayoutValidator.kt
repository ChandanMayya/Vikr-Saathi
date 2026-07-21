package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.model.template.ElementBounds
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.TemplateElement
import kotlin.math.roundToInt

object GridSnapper {
    const val GRID_SIZE = 8f

    fun snap(value: Float, enabled: Boolean): Float {
        if (!enabled) return value
        return (value / GRID_SIZE).roundToInt() * GRID_SIZE
    }

    fun snapBounds(bounds: ElementBounds, enabled: Boolean): ElementBounds {
        if (!enabled) return bounds
        return bounds.copy(
            x = snap(bounds.x, true),
            y = snap(bounds.y, true),
            width = snap(bounds.width, true).coerceAtLeast(GRID_SIZE),
            height = snap(bounds.height, true).coerceAtLeast(GRID_SIZE)
        )
    }
}

data class TemplateValidationIssue(
    val elementId: String?,
    val message: String
)

object TemplateLayoutValidator {

    fun validate(template: InvoiceTemplate): List<TemplateValidationIssue> {
        val issues = mutableListOf<TemplateValidationIssue>()
        val visible = template.elements.filter { it.visible }

        visible.forEach { element ->
            val b = ElementRotationHelper.axisAlignedBounds(element)
            if (b.x < 0 || b.y < 0) {
                issues.add(TemplateValidationIssue(element.id, "Element is outside page (negative position)"))
            }
            if (b.x + b.width > template.pageWidthPt) {
                issues.add(TemplateValidationIssue(element.id, "Element extends beyond page width"))
            }
            if (b.y + b.height > template.pageHeightPt) {
                issues.add(TemplateValidationIssue(element.id, "Element extends beyond page height"))
            }
            if (element.bounds.width < 8f || element.bounds.height < 4f) {
                issues.add(TemplateValidationIssue(element.id, "Element is too small"))
            }
        }

        for (i in visible.indices) {
            for (j in i + 1 until visible.size) {
                val a = visible[i]
                val b = visible[j]
                if (boundsOverlap(
                        ElementRotationHelper.axisAlignedBounds(a),
                        ElementRotationHelper.axisAlignedBounds(b)
                    )) {
                    issues.add(
                        TemplateValidationIssue(
                            a.id,
                            "Overlaps with ${b.kind.name.lowercase()} (${b.id.take(8)})"
                        )
                    )
                }
            }
        }
        return issues
    }

    private fun boundsOverlap(a: ElementBounds, b: ElementBounds): Boolean {
        return a.x < b.x + b.width &&
            a.x + a.width > b.x &&
            a.y < b.y + b.height &&
            a.y + a.height > b.y
    }
}
