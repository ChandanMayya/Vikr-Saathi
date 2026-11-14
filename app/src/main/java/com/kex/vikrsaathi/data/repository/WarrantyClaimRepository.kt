package com.kex.vikrsaathi.data.repository

import androidx.room.withTransaction
import com.kex.vikrsaathi.data.dao.BillDao
import com.kex.vikrsaathi.data.dao.WarrantyClaimDao
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.WarrantyClaim
import com.kex.vikrsaathi.data.entity.WarrantyClaimEvent
import com.kex.vikrsaathi.data.model.WarrantySaleLine
import com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.domain.bill.claimSnapshot

class WarrantyClaimRepository(
    private val database: AppDatabase,
    private val claims: WarrantyClaimDao,
    private val bills: BillDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun search(query: String): List<WarrantySaleLine> =
        claims.searchSaleLines(query.trim())

    suspend fun saleLine(billItemId: Long): WarrantySaleLine? = claims.saleLine(billItemId)

    suspend fun history(): List<WarrantyClaim> = claims.allClaims()

    suspend fun claim(id: Long): WarrantyClaim? = claims.getClaim(id)

    suspend fun events(claimId: Long): List<WarrantyClaimEvent> = claims.eventsFor(claimId)

    suspend fun billStillExists(billId: Long?): Boolean {
        if (billId == null) return false
        return bills.getBillById(billId) != null
    }

    suspend fun createClaim(
        billItemId: Long,
        issue: String,
        condition: String,
        note: String
    ): WarrantyClaimOutcome {
        val reason = issue.trim()
        if (reason.isEmpty()) return WarrantyClaimOutcome.NotAllowed(WarrantyClaimPolicy.ISSUE_REQUIRED)
        return database.withTransaction {
            val line = claims.saleLine(billItemId)
                ?: return@withTransaction WarrantyClaimOutcome.NotAllowed("This sale item could not be found.")
            val now = clock()
            val coverage = WarrantyClaimPolicy.coverage(
                line.snapshot(),
                WarrantyClaimPolicy.today(now)
            )
            val blocked = WarrantyClaimPolicy.blockedMessage(coverage)
            if (blocked != null) return@withTransaction WarrantyClaimOutcome.NotAllowed(blocked)
            val sold = database.billItemDao().getItemsForBill(line.billId)
                .firstOrNull { it.id == billItemId }
            val returned = database.customerLedgerDao().activeReturnedQuantity(billItemId)
            if (sold != null && returned >= sold.quantity) {
                return@withTransaction WarrantyClaimOutcome.NotAllowed(WarrantyClaimPolicy.BLOCKED_RETURNED)
            }
            val snapshot = line.snapshot()
            var sequence = claims.maxSequence() + 1
            var reference = WarrantyClaimPolicy.reference(WarrantyClaimPolicy.yearOf(now), sequence)
            while (claims.countReference(reference) > 0) {
                sequence += 1
                reference = WarrantyClaimPolicy.reference(WarrantyClaimPolicy.yearOf(now), sequence)
            }
            val claim = WarrantyClaim(
                reference = reference,
                sequence = sequence,
                billId = line.billId,
                billItemId = line.billItemId,
                billNumber = line.billNumber,
                saleDateMillis = line.saleDateMillis,
                customerName = line.customerName?.trim().orEmpty(),
                customerPhone = line.customerPhone?.trim().orEmpty(),
                itemName = line.itemName,
                warrantyPeriod = snapshot?.period,
                warrantyUnit = snapshot?.unit,
                warrantyProvider = snapshot?.provider,
                warrantyProviderName = snapshot?.providerName.orEmpty(),
                warrantyTerms = snapshot?.terms.orEmpty(),
                warrantyStartEpochDay = snapshot?.startEpochDay,
                warrantyEndEpochDay = snapshot?.endEpochDay,
                issue = reason,
                condition = condition.trim(),
                note = note.trim(),
                status = WarrantyClaimPolicy.RECEIVED,
                createdAt = now,
                updatedAt = now
            )
            val id = claims.insertClaim(claim)
            claims.insertEvent(
                WarrantyClaimEvent(
                    claimId = id,
                    status = WarrantyClaimPolicy.RECEIVED,
                    createdAt = now
                )
            )
            WarrantyClaimOutcome.Saved(id)
        }
    }

    suspend fun updateStatus(
        claimId: Long,
        status: String,
        resolutionNote: String
    ): WarrantyClaimOutcome {
        if (status !in WarrantyClaimPolicy.statuses) {
            return WarrantyClaimOutcome.NotAllowed("Choose a claim status.")
        }
        val outcome = resolutionNote.trim()
        if (WarrantyClaimPolicy.needsResolution(status) && outcome.isEmpty()) {
            return WarrantyClaimOutcome.NotAllowed(WarrantyClaimPolicy.RESOLUTION_REQUIRED)
        }
        return database.withTransaction {
            val current = claims.getClaim(claimId)
                ?: return@withTransaction WarrantyClaimOutcome.NotAllowed("This claim could not be found.")
            if (!current.providerOutcome.isNullOrBlank()) {
                return@withTransaction WarrantyClaimOutcome.NotAllowed(WarrantyClaimPolicy.STATUS_FOLLOWS_OUTCOME)
            }
            val now = clock()
            val unchanged = current.status == status && current.resolutionNote == outcome
            if (unchanged) return@withTransaction WarrantyClaimOutcome.Saved(claimId)
            claims.updateClaim(
                current.copy(
                    status = status,
                    resolutionNote = if (WarrantyClaimPolicy.needsResolution(status)) outcome else current.resolutionNote,
                    updatedAt = now
                )
            )
            claims.insertEvent(
                WarrantyClaimEvent(
                    claimId = claimId,
                    status = status,
                    note = outcome,
                    createdAt = now
                )
            )
            WarrantyClaimOutcome.Saved(claimId)
        }
    }

    suspend fun recordServiceCentre(
        claimId: Long,
        centre: String,
        reference: String,
        sentEpochDay: Long?
    ): WarrantyClaimOutcome = database.withTransaction {
        val current = claims.getClaim(claimId)
            ?: return@withTransaction WarrantyClaimOutcome.NotAllowed("This claim could not be found.")
        val now = clock()
        val sent = sentEpochDay ?: WarrantyClaimPolicy.today(now)
        val nextStatus = WarrantyClaimPolicy.statusAfterServiceCentre(
            current.status,
            current.providerOutcome
        )
        claims.updateClaim(
            current.copy(
                serviceCentre = centre.trim(),
                serviceReference = reference.trim(),
                serviceSentEpochDay = sent,
                status = nextStatus,
                updatedAt = now
            )
        )
        val details = listOf(
            centre.trim(),
            reference.trim(),
            "Sent ${WarrantyPolicy.formatDate(sent)}"
        ).filter { it.isNotBlank() }.joinToString(" · ")
        claims.insertEvent(
            WarrantyClaimEvent(
                claimId = claimId,
                status = nextStatus,
                note = details,
                createdAt = now,
                eventKind = WarrantyClaimPolicy.EVENT_SERVICE
            )
        )
        WarrantyClaimOutcome.Saved(claimId)
    }

    suspend fun saveProviderOutcome(
        claimId: Long,
        outcome: String,
        note: String,
        responseEpochDay: Long?
    ): WarrantyClaimOutcome {
        if (outcome !in WarrantyClaimPolicy.outcomes) {
            return WarrantyClaimOutcome.NotAllowed("Choose a provider outcome.")
        }
        val detail = note.trim()
        if (outcome == WarrantyClaimPolicy.OTHER && detail.isEmpty()) {
            return WarrantyClaimOutcome.NotAllowed(WarrantyClaimPolicy.OUTCOME_NOTE_REQUIRED)
        }
        return database.withTransaction {
            val current = claims.getClaim(claimId)
                ?: return@withTransaction WarrantyClaimOutcome.NotAllowed("This claim could not be found.")
            val now = clock()
            val response = responseEpochDay ?: WarrantyClaimPolicy.today(now)
            val status = WarrantyClaimPolicy.statusForOutcome(outcome)
            claims.updateClaim(
                current.copy(
                    providerOutcome = outcome,
                    providerOutcomeNote = detail,
                    providerResponseEpochDay = response,
                    status = status,
                    resolutionNote = detail,
                    updatedAt = now
                )
            )
            val summary = listOf(
                WarrantyClaimPolicy.outcomeLabel(outcome),
                detail,
                WarrantyPolicy.formatDate(response)
            ).filter { it.isNotBlank() }.joinToString(" · ")
            claims.insertEvent(
                WarrantyClaimEvent(
                    claimId = claimId,
                    status = status,
                    note = summary,
                    createdAt = now,
                    eventKind = WarrantyClaimPolicy.EVENT_OUTCOME
                )
            )
            WarrantyClaimOutcome.Saved(claimId)
        }
    }

    suspend fun saveHandover(
        claimId: Long,
        returnedEpochDay: Long?,
        recipient: String,
        note: String
    ): WarrantyClaimOutcome = database.withTransaction {
        val current = claims.getClaim(claimId)
            ?: return@withTransaction WarrantyClaimOutcome.NotAllowed("This claim could not be found.")
        if (current.providerOutcome.isNullOrBlank()) {
            return@withTransaction WarrantyClaimOutcome.NotAllowed(
                "Save a provider outcome before returning the item."
            )
        }
        val now = clock()
        val returned = returnedEpochDay ?: WarrantyClaimPolicy.today(now)
        claims.updateClaim(
            current.copy(
                handoverEpochDay = returned,
                handoverRecipient = recipient.trim(),
                handoverNote = note.trim(),
                updatedAt = now
            )
        )
        val summary = listOf(
            WarrantyPolicy.formatDate(returned),
            recipient.trim(),
            note.trim()
        ).filter { it.isNotBlank() }.joinToString(" · ")
        claims.insertEvent(
            WarrantyClaimEvent(
                claimId = claimId,
                status = current.status,
                note = summary,
                createdAt = now,
                eventKind = WarrantyClaimPolicy.EVENT_HANDOVER
            )
        )
        WarrantyClaimOutcome.Saved(claimId)
    }
}

sealed class WarrantyClaimOutcome {
    data class Saved(val claimId: Long) : WarrantyClaimOutcome()
    data class NotAllowed(val message: String) : WarrantyClaimOutcome()
}
