package com.kex.vikrsaathi.data.draft

import com.kex.vikrsaathi.data.model.BillLineItem
import org.json.JSONArray
import org.json.JSONObject

object BillDraftCodec {

    fun encodeLineItems(items: List<BillLineItem>): String {
        val array = JSONArray()
        items.forEach { line ->
            array.put(
                JSONObject().apply {
                    put("itemId", line.itemId)
                    put("name", line.name)
                    put("mrp", line.mrp)
                    put("discount", line.discount)
                    put("quantity", line.quantity)
                    put("roundOff", line.roundOff)
                }
            )
        }
        return array.toString()
    }

    fun decodeLineItems(json: String): List<BillLineItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        BillLineItem(
                            itemId = obj.optLong("itemId").takeIf { it > 0 },
                            name = obj.getString("name"),
                            mrp = obj.getDouble("mrp"),
                            discount = obj.getDouble("discount"),
                            quantity = obj.optInt("quantity", 1).coerceAtLeast(1),
                            roundOff = obj.optDouble("roundOff", 0.0)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
