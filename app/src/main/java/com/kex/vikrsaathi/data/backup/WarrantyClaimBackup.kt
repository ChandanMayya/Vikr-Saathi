package com.kex.vikrsaathi.data.backup

import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.WarrantyClaim
import com.kex.vikrsaathi.data.entity.WarrantyClaimEvent
import org.json.JSONArray
import org.json.JSONObject

object WarrantyClaimBackup {
    fun encode(claims: List<WarrantyClaim>, events: List<WarrantyClaimEvent>): JSONArray {
        val byClaim = events.groupBy { it.claimId }
        return JSONArray().apply {
            claims.forEach { claim ->
                put(encodeClaim(claim, byClaim[claim.id].orEmpty()))
            }
        }
    }

    fun decode(root: JSONObject): List<DecodedClaim> {
        val array = root.optJSONArray("warrantyClaims") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) add(decodeClaim(array.getJSONObject(i)))
        }
    }

    /** Prefer the line whose saved warranty end date matches the claim. */
    fun matchingBillItemId(items: List<BillItem>, itemName: String, endEpochDay: Long?): Long? {
        val named = items.filter { it.itemName == itemName }
        return named.firstOrNull { it.warrantyEndEpochDay == endEpochDay }?.id ?: named.firstOrNull()?.id
    }

    private fun encodeClaim(claim: WarrantyClaim, events: List<WarrantyClaimEvent>): JSONObject {
        return JSONObject().apply {
            put("legacyId", claim.id)
            put("reference", claim.reference)
            put("sequence", claim.sequence)
            claim.billId?.let { put("billLegacyId", it) }
            claim.billItemId?.let { put("billItemLegacyId", it) }
            put("billNumber", claim.billNumber)
            put("saleDateMillis", claim.saleDateMillis)
            put("customerName", claim.customerName)
            put("customerPhone", claim.customerPhone)
            put("itemName", claim.itemName)
            claim.warrantyPeriod?.let { put("warrantyPeriod", it) }
            claim.warrantyUnit?.let { put("warrantyUnit", it) }
            claim.warrantyProvider?.let { put("warrantyProvider", it) }
            put("warrantyProviderName", claim.warrantyProviderName)
            put("warrantyTerms", claim.warrantyTerms)
            claim.warrantyStartEpochDay?.let { put("warrantyStartEpochDay", it) }
            claim.warrantyEndEpochDay?.let { put("warrantyEndEpochDay", it) }
            put("issue", claim.issue)
            put("condition", claim.condition)
            put("note", claim.note)
            put("status", claim.status)
            put("resolutionNote", claim.resolutionNote)
            put("createdAt", claim.createdAt)
            put("updatedAt", claim.updatedAt)
            put("serviceCentre", claim.serviceCentre)
            put("serviceReference", claim.serviceReference)
            claim.serviceSentEpochDay?.let { put("serviceSentEpochDay", it) }
            claim.providerOutcome?.let { put("providerOutcome", it) }
            put("providerOutcomeNote", claim.providerOutcomeNote)
            claim.providerResponseEpochDay?.let { put("providerResponseEpochDay", it) }
            claim.handoverEpochDay?.let { put("handoverEpochDay", it) }
            put("handoverRecipient", claim.handoverRecipient)
            put("handoverNote", claim.handoverNote)
            put("events", JSONArray().apply {
                events.forEach { event ->
                    put(JSONObject().apply {
                        put("status", event.status)
                        put("note", event.note)
                        put("createdAt", event.createdAt)
                        put("eventKind", event.eventKind)
                    })
                }
            })
        }
    }

    private fun decodeClaim(obj: JSONObject): DecodedClaim {
        val events = mutableListOf<DecodedEvent>()
        val array = obj.optJSONArray("events")
        if (array != null) {
            for (i in 0 until array.length()) {
                val event = array.getJSONObject(i)
                events += DecodedEvent(
                    status = event.optString("status"),
                    note = event.optString("note"),
                    createdAt = event.optLong("createdAt"),
                    eventKind = event.optString("eventKind").ifBlank {
                        com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy.EVENT_STATUS
                    }
                )
            }
        }
        return DecodedClaim(
            legacyId = obj.optLong("legacyId"),
            reference = obj.optString("reference"),
            sequence = obj.optInt("sequence"),
            billLegacyId = obj.optLong("billLegacyId").takeIf { obj.has("billLegacyId") },
            billItemLegacyId = obj.optLong("billItemLegacyId").takeIf { obj.has("billItemLegacyId") },
            billNumber = obj.optString("billNumber"),
            saleDateMillis = obj.optLong("saleDateMillis"),
            customerName = obj.optString("customerName"),
            customerPhone = obj.optString("customerPhone"),
            itemName = obj.optString("itemName"),
            warrantyPeriod = obj.optInt("warrantyPeriod").takeIf { obj.has("warrantyPeriod") },
            warrantyUnit = obj.optString("warrantyUnit").takeIf { obj.has("warrantyUnit") },
            warrantyProvider = obj.optString("warrantyProvider").takeIf { obj.has("warrantyProvider") },
            warrantyProviderName = obj.optString("warrantyProviderName"),
            warrantyTerms = obj.optString("warrantyTerms"),
            warrantyStartEpochDay = obj.optLong("warrantyStartEpochDay").takeIf { obj.has("warrantyStartEpochDay") },
            warrantyEndEpochDay = obj.optLong("warrantyEndEpochDay").takeIf { obj.has("warrantyEndEpochDay") },
            issue = obj.optString("issue"),
            condition = obj.optString("condition"),
            note = obj.optString("note"),
            status = obj.optString("status"),
            resolutionNote = obj.optString("resolutionNote"),
            createdAt = obj.optLong("createdAt"),
            updatedAt = obj.optLong("updatedAt"),
            serviceCentre = obj.optString("serviceCentre"),
            serviceReference = obj.optString("serviceReference"),
            serviceSentEpochDay = obj.optLong("serviceSentEpochDay").takeIf { obj.has("serviceSentEpochDay") },
            providerOutcome = obj.optString("providerOutcome").takeIf { obj.has("providerOutcome") },
            providerOutcomeNote = obj.optString("providerOutcomeNote"),
            providerResponseEpochDay = obj.optLong("providerResponseEpochDay").takeIf { obj.has("providerResponseEpochDay") },
            handoverEpochDay = obj.optLong("handoverEpochDay").takeIf { obj.has("handoverEpochDay") },
            handoverRecipient = obj.optString("handoverRecipient"),
            handoverNote = obj.optString("handoverNote"),
            events = events
        )
    }

    data class DecodedEvent(
        val status: String,
        val note: String,
        val createdAt: Long,
        val eventKind: String = com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy.EVENT_STATUS
    )

    data class DecodedClaim(
        val legacyId: Long,
        val reference: String,
        val sequence: Int,
        val billLegacyId: Long?,
        val billItemLegacyId: Long?,
        val billNumber: String,
        val saleDateMillis: Long,
        val customerName: String,
        val customerPhone: String,
        val itemName: String,
        val warrantyPeriod: Int?,
        val warrantyUnit: String?,
        val warrantyProvider: String?,
        val warrantyProviderName: String,
        val warrantyTerms: String,
        val warrantyStartEpochDay: Long?,
        val warrantyEndEpochDay: Long?,
        val issue: String,
        val condition: String,
        val note: String,
        val status: String,
        val resolutionNote: String,
        val createdAt: Long,
        val updatedAt: Long,
        val serviceCentre: String = "",
        val serviceReference: String = "",
        val serviceSentEpochDay: Long? = null,
        val providerOutcome: String? = null,
        val providerOutcomeNote: String = "",
        val providerResponseEpochDay: Long? = null,
        val handoverEpochDay: Long? = null,
        val handoverRecipient: String = "",
        val handoverNote: String = "",
        val events: List<DecodedEvent>
    )
}
