package com.kex.vikrsaathi.data.model.template

enum class ElementKind {
    IMAGE,
    TEXT,
    LINE,
    RECT,
    TABLE,
    SPACER
}

enum class ElementBinding {
    STATIC,
    DYNAMIC
}

/**
 * Supported dynamic data keys resolved at render time from bill + shop settings.
 */
enum class DataBindingKey {
    SHOP_NAME,
    HEADER_IMAGE,
    SIGNATURE_IMAGE,
    SHOP_LOGO,
    BILL_NUMBER,
    BILL_DATE,
    CUSTOMER_NAME,
    CUSTOMER_ADDRESS,
    CUSTOMER_PHONE,
    BILL_TOTAL,
    BILL_TOTAL_WORDS,
    BILL_ITEMS
}

enum class ImageSource {
    HEADER_IMAGE,
    SIGNATURE_IMAGE,
    SHOP_LOGO
}

enum class TextAlign {
    LEFT,
    CENTER,
    RIGHT
}

enum class VerticalAlign {
    TOP,
    CENTER,
    BOTTOM
}

enum class FontFamily {
    DEFAULT,
    SERIF,
    SANS_SERIF,
    MONOSPACE
}

data class ElementBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class ElementStyle(
    val fontSize: Float = 12f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val textAlign: TextAlign = TextAlign.LEFT,
    val verticalAlign: VerticalAlign = VerticalAlign.TOP,
    val color: String = "#000000",
    val fontFamily: FontFamily = FontFamily.DEFAULT
)

data class TableColumn(
    val key: String,
    val label: String,
    val widthPercent: Float,
    val align: TextAlign = TextAlign.LEFT
)

enum class GuideOrientation {
    HORIZONTAL,
    VERTICAL
}

data class TemplateGuide(
    val id: String,
    val orientation: GuideOrientation,
    /** Page Y for horizontal guides, page X for vertical guides (points). */
    val positionPt: Float
)

data class TemplateElement(
    val id: String,
    val kind: ElementKind,
    val binding: ElementBinding,
    val bounds: ElementBounds,
    val zIndex: Int = 0,
    val visible: Boolean = true,
    val style: ElementStyle = ElementStyle(),
    /** Static text, binding key name, image source, table columns JSON, etc. */
    val content: Map<String, String> = emptyMap(),
    /** Elements sharing a groupId move, resize, and lock together in the builder. */
    val groupId: String? = null,
    /** Locked elements cannot be selected or edited until unlocked. */
    val locked: Boolean = false
)

data class InvoiceTemplate(
    val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false,
    val pageWidthPt: Int = PAGE_WIDTH_PT,
    val pageHeightPt: Int = PAGE_HEIGHT_PT,
    val marginLeft: Float = 40f,
    val marginTop: Float = 40f,
    val marginRight: Float = 40f,
    val marginBottom: Float = 40f,
    val elements: List<TemplateElement> = emptyList(),
    val guides: List<TemplateGuide> = emptyList(),
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val sortedElements: List<TemplateElement>
        get() = elements.filter { it.visible }.sortedBy { it.zIndex }

    companion object {
        const val PAGE_WIDTH_PT = 595
        const val PAGE_HEIGHT_PT = 842
    }
}
