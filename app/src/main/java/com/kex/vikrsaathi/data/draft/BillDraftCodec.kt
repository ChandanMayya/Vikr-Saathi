package com.kex.vikrsaathi.data.draft

import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
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
                    put("isNew", line.isNew)
                    if (line.lineKey.isNotEmpty()) put("lineKey", line.lineKey)
                    line.openingStock?.let { put("openingStock", it) }
                    line.barcode?.let { put("barcode", it) }
                    line.warranty?.let { warranty ->
                        put("warrantyPeriod", warranty.period)
                        put("warrantyUnit", warranty.unit)
                        put("warrantyProvider", warranty.provider)
                        put("warrantyProviderName", warranty.providerName)
                        put("warrantyTerms", warranty.terms)
                        warranty.startEpochDay?.let { put("warrantyStartEpochDay", it) }
                        warranty.endEpochDay?.let { put("warrantyEndEpochDay", it) }
                    }
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
                            roundOff = obj.optDouble("roundOff", 0.0),
                            isNew = obj.optBoolean("isNew", false),
                            openingStock = if (obj.has("openingStock")) {
                                obj.optInt("openingStock").coerceAtLeast(0)
                            } else {
                                null
                            },
                            barcode = obj.optString("barcode").trim().ifEmpty { null },
                            lineKey = obj.optString("lineKey").trim(),
                            warranty = if (obj.has("warrantyPeriod")) {
                                WarrantyPolicy.fromStored(
                                    period = obj.optInt("warrantyPeriod"),
                                    unit = obj.optString("warrantyUnit"),
                                    provider = obj.optString("warrantyProvider"),
                                    providerName = obj.optString("warrantyProviderName"),
                                    terms = obj.optString("warrantyTerms"),
                                    startEpochDay = obj.optLong("warrantyStartEpochDay").takeIf {
                                        obj.has("warrantyStartEpochDay")
                                    },
                                    endEpochDay = obj.optLong("warrantyEndEpochDay").takeIf {
                                        obj.has("warrantyEndEpochDay")
                                    }
                                )
                            } else {
                                null
                            }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
