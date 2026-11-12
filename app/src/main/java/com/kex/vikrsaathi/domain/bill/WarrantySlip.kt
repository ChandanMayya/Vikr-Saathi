package com.kex.vikrsaathi.domain.bill

import com.kex.vikrsaathi.data.entity.WarrantyClaim
import com.kex.vikrsaathi.data.entity.WarrantyClaimEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Informational service slip. It is not a tax invoice, government form, or a promise of repair. */
object WarrantySlip {
    const val TITLE = "WARRANTY SERVICE SLIP"
    const val FOOTER = "Keep this slip for claim reference."

    data class Row(val label: String, val value: String)

    fun rows(claim: WarrantyClaim, shopName: String): List<Row> {
        val snapshot = claim.claimSnapshot()
        val rows = mutableListOf<Row>()
        if (shopName.isNotBlank()) rows += Row("Shop", shopName.trim())
        rows += Row("Claim", claim.reference)
        if (claim.customerName.isNotBlank()) rows += Row("Customer", claim.customerName)
        if (claim.customerPhone.isNotBlank()) rows += Row("Contact", claim.customerPhone)
        rows += Row("Item", claim.itemName)
        rows += Row("Bill", claim.billNumber)
        rows += Row("Sale date", formatWhen(claim.saleDateMillis))
        if (snapshot != null) {
            val provider = snapshot.providerName.trim().ifBlank { WarrantyPolicy.providerLabel(snapshot.provider) }
            if (provider.isNotBlank()) rows += Row("Warranty provider", provider)
            snapshot.startEpochDay?.let { rows += Row("Cover from", WarrantyPolicy.formatDate(it)) }
            snapshot.endEpochDay?.let { rows += Row("Cover until", WarrantyPolicy.formatDate(it)) }
            if (snapshot.terms.isNotBlank()) rows += Row("Warranty terms", snapshot.terms)
        }
        rows += Row("Date received", formatWhen(claim.createdAt))
        rows += Row("Issue", claim.issue)
        if (claim.condition.isNotBlank()) rows += Row("Item condition", claim.condition)
        if (claim.note.isNotBlank()) rows += Row("Note", claim.note)
        if (claim.serviceCentre.isNotBlank()) rows += Row("Service centre", claim.serviceCentre)
        if (claim.serviceReference.isNotBlank()) rows += Row("Service-centre reference", claim.serviceReference)
        claim.serviceSentEpochDay?.let { rows += Row("Sent date", WarrantyPolicy.formatDate(it)) }
        val providerOutcome = claim.providerOutcome
        if (!providerOutcome.isNullOrBlank()) {
            rows += Row("Provider outcome", WarrantyClaimPolicy.outcomeLabel(providerOutcome))
        }
        if (claim.providerOutcomeNote.isNotBlank()) rows += Row("Outcome note", claim.providerOutcomeNote)
        claim.providerResponseEpochDay?.let { rows += Row("Provider response date", WarrantyPolicy.formatDate(it)) }
        claim.handoverEpochDay?.let { rows += Row("Customer handover date", WarrantyPolicy.formatDate(it)) }
        if (claim.handoverRecipient.isNotBlank()) rows += Row("Handed to", claim.handoverRecipient)
        if (claim.handoverNote.isNotBlank()) rows += Row("Handover note", claim.handoverNote)
        rows += Row("Status", WarrantyClaimPolicy.label(claim.status))
        if (providerOutcome.isNullOrBlank() &&
            WarrantyClaimPolicy.needsResolution(claim.status) &&
            claim.resolutionNote.isNotBlank()
        ) {
            rows += Row("Outcome", claim.resolutionNote)
        }
        return rows
    }

    fun eventLine(event: WarrantyClaimEvent): String {
        val title = when (event.eventKind) {
            WarrantyClaimPolicy.EVENT_SERVICE -> WarrantyClaimPolicy.label(WarrantyClaimPolicy.SENT_TO_SERVICE)
            WarrantyClaimPolicy.EVENT_OUTCOME -> "Provider outcome"
            WarrantyClaimPolicy.EVENT_HANDOVER -> WarrantyClaimPolicy.HANDOVER_RETURNED
            else -> WarrantyClaimPolicy.label(event.status)
        }
        val whenLabel = formatWhen(event.createdAt)
        val note = event.note.trim()
        return if (note.isEmpty()) "$title · $whenLabel" else "$title · $whenLabel · $note"
    }

    fun formatWhen(millis: Long): String =
        SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(millis))
}
