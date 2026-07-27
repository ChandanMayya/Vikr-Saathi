package com.kex.vikrsaathi.data.model.template

import org.json.JSONArray
import org.json.JSONObject

object TemplateJsonCodec {

    fun toJson(template: InvoiceTemplate): String {
        return JSONObject().apply {
            put("id", template.id)
            put("name", template.name)
            put("isDefault", template.isDefault)
            put("sheetType", template.sheetType)
            put("pageWidthPt", template.pageWidthPt)
            put("pageHeightPt", template.pageHeightPt)
            put("marginLeft", template.marginLeft.toDouble())
            put("marginTop", template.marginTop.toDouble())
            put("marginRight", template.marginRight.toDouble())
            put("marginBottom", template.marginBottom.toDouble())
            put("version", template.version)
            put("updatedAt", template.updatedAt)
            put("elements", JSONArray().apply {
                template.elements.forEach { put(elementToJson(it)) }
            })
            if (template.guides.isNotEmpty()) {
                put("guides", JSONArray().apply {
                    template.guides.forEach { put(guideToJson(it)) }
                })
            }
        }.toString()
    }

    fun fromJson(json: String, id: Long = 0, isDefault: Boolean = false, name: String = ""): InvoiceTemplate {
        val root = JSONObject(json)
        val elements = mutableListOf<TemplateElement>()
        val elementsArray = root.optJSONArray("elements") ?: JSONArray()
        for (i in 0 until elementsArray.length()) {
            elements.add(elementFromJson(elementsArray.getJSONObject(i)))
        }
        val guides = mutableListOf<TemplateGuide>()
        val guidesArray = root.optJSONArray("guides") ?: JSONArray()
        for (i in 0 until guidesArray.length()) {
            guides.add(guideFromJson(guidesArray.getJSONObject(i)))
        }
        val pageWidthPt = root.optInt("pageWidthPt", InvoiceTemplate.PAGE_WIDTH_PT)
        val pageHeightPt = root.optInt("pageHeightPt", InvoiceTemplate.PAGE_HEIGHT_PT)
        val sheetType = root.optString("sheetType", "").ifBlank {
            PaperSizeCatalog.match(pageWidthPt, pageHeightPt).name
        }
        return InvoiceTemplate(
            id = id.takeIf { it > 0 } ?: root.optLong("id", 0),
            name = name.ifEmpty { root.optString("name", "Template") },
            isDefault = isDefault || root.optBoolean("isDefault", false),
            sheetType = sheetType,
            pageWidthPt = pageWidthPt,
            pageHeightPt = pageHeightPt,
            marginLeft = root.optDouble("marginLeft", 40.0).toFloat(),
            marginTop = root.optDouble("marginTop", 40.0).toFloat(),
            marginRight = root.optDouble("marginRight", 40.0).toFloat(),
            marginBottom = root.optDouble("marginBottom", 40.0).toFloat(),
            elements = elements,
            guides = guides,
            version = root.optInt("version", 1),
            updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    fun tableColumnsToJson(columns: List<TableColumn>): String {
        return JSONArray().apply {
            columns.forEach { col ->
                put(JSONObject().apply {
                    put("key", col.key)
                    put("label", col.label)
                    put("widthPercent", col.widthPercent.toDouble())
                    put("align", col.align.name)
                })
            }
        }.toString()
    }

    fun tableColumnsFromJson(json: String): List<TableColumn> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    TableColumn(
                        key = obj.getString("key"),
                        label = obj.getString("label"),
                        widthPercent = obj.getDouble("widthPercent").toFloat(),
                        align = TextAlign.valueOf(obj.optString("align", "LEFT"))
                    )
                )
            }
        }
    }

    private fun guideToJson(guide: TemplateGuide): JSONObject {
        return JSONObject().apply {
            put("id", guide.id)
            put("orientation", guide.orientation.name)
            put("positionPt", guide.positionPt.toDouble())
        }
    }

    private fun guideFromJson(obj: JSONObject): TemplateGuide {
        return TemplateGuide(
            id = obj.getString("id"),
            orientation = GuideOrientation.valueOf(obj.getString("orientation")),
            positionPt = obj.getDouble("positionPt").toFloat()
        )
    }

    private fun elementToJson(element: TemplateElement): JSONObject {
        return JSONObject().apply {
            put("id", element.id)
            put("kind", element.kind.name)
            put("binding", element.binding.name)
            put("bounds", JSONObject().apply {
                put("x", element.bounds.x.toDouble())
                put("y", element.bounds.y.toDouble())
                put("width", element.bounds.width.toDouble())
                put("height", element.bounds.height.toDouble())
            })
            put("zIndex", element.zIndex)
            put("visible", element.visible)
            element.groupId?.let { put("groupId", it) }
            put("locked", element.locked)
            if (element.rotationDegrees != 0f) {
                put("rotationDegrees", element.rotationDegrees.toDouble())
            }
            put("style", JSONObject().apply {
                put("fontSize", element.style.fontSize.toDouble())
                put("bold", element.style.bold)
                put("italic", element.style.italic)
                put("underline", element.style.underline)
                put("textAlign", element.style.textAlign.name)
                put("verticalAlign", element.style.verticalAlign.name)
                put("color", element.style.color)
                put("fontFamily", element.style.fontFamily.name)
                put("imageScaleMode", element.style.imageScaleMode.name)
            })
            put("content", JSONObject(element.content))
        }
    }

    private fun elementFromJson(obj: JSONObject): TemplateElement {
        val boundsObj = obj.getJSONObject("bounds")
        val styleObj = obj.optJSONObject("style") ?: JSONObject()
        val contentObj = obj.optJSONObject("content") ?: JSONObject()
        val content = buildMap {
            contentObj.keys().forEach { key -> put(key, contentObj.getString(key)) }
        }
        return TemplateElement(
            id = obj.getString("id"),
            kind = ElementKind.valueOf(obj.getString("kind")),
            binding = ElementBinding.valueOf(obj.getString("binding")),
            bounds = ElementBounds(
                x = boundsObj.getDouble("x").toFloat(),
                y = boundsObj.getDouble("y").toFloat(),
                width = boundsObj.getDouble("width").toFloat(),
                height = boundsObj.getDouble("height").toFloat()
            ),
            zIndex = obj.optInt("zIndex", 0),
            visible = obj.optBoolean("visible", true),
            groupId = obj.optString("groupId").takeIf { it.isNotBlank() },
            locked = obj.optBoolean("locked", false),
            rotationDegrees = obj.optDouble("rotationDegrees", 0.0).toFloat(),
            style = ElementStyle(
                fontSize = styleObj.optDouble("fontSize", 12.0).toFloat(),
                bold = styleObj.optBoolean("bold", false),
                italic = styleObj.optBoolean("italic", false),
                underline = styleObj.optBoolean("underline", false),
                textAlign = TextAlign.valueOf(styleObj.optString("textAlign", "LEFT")),
                verticalAlign = VerticalAlign.valueOf(styleObj.optString("verticalAlign", "TOP")),
                color = styleObj.optString("color", "#000000"),
                fontFamily = FontFamily.valueOf(styleObj.optString("fontFamily", "DEFAULT")),
                imageScaleMode = ImageScaleMode.valueOf(
                    styleObj.optString("imageScaleMode", ImageScaleMode.FIT.name)
                )
            ),
            content = content
        )
    }
}
