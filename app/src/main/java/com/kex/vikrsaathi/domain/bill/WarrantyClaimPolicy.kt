package com.kex.vikrsaathi.domain.bill

import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.WarrantyClaim
import com.kex.vikrsaathi.domain.inventory.CivilDate
import java.util.Calendar

/**
 * Records a shop's warranty claim against a sale-time snapshot.
 * A claim does not decide whether a manufacturer or service centre must accept it,
 * and it does not create a refund, exchange, or repair charge.
 */
object WarrantyClaimPolicy {
    const val RECEIVED = "RECEIVED"
    const val UNDER_INSPECTION = "UNDER_INSPECTION"
    const val SENT_TO_SERVICE = "SENT_TO_SERVICE"
    const val AWAITING_CUSTOMER = "AWAITING_CUSTOMER"
    const val RESOLVED = "RESOLVED"
    const val REJECTED = "REJECTED"

    const val ACTIVE = "Active warranty"
    const val EXPIRED = "Warranty expired"
    const val NO_RECORD = WarrantyPolicy.NO_RECORD
    const val BLOCKED_NONE = "A claim cannot be created because this sale has no saved warranty."
    const val BLOCKED_EXPIRED = "A claim cannot be created because this warranty has expired."
    const val BLOCKED_RETURNED = "A claim cannot be created because this item has already been returned."
    const val ISSUE_REQUIRED = "Enter the issue or reason."
    const val RESOLUTION_REQUIRED = "Add the outcome for this status."
    const val OUTCOME_NOTE_REQUIRED = "Add an outcome note."
    const val STATUS_FOLLOWS_OUTCOME = "This claim status follows the saved provider outcome."
    const val SAVE_IN_PROGRESS = "Wait for the current save to finish."
    const val HANDOVER_WAITING = "Awaiting customer"
    const val HANDOVER_RETURNED = "Returned to customer"
    const val HANDOVER_NOT_YET = "Item not yet returned to customer"

    const val EVENT_STATUS = "STATUS"
    const val EVENT_SERVICE = "SERVICE"
    const val EVENT_OUTCOME = "OUTCOME"
    const val EVENT_HANDOVER = "HANDOVER"

    const val REPAIRED = "REPAIRED"
    const val REPLACEMENT = "REPLACEMENT"
    const val NO_FAULT = "NO_FAULT"
    const val DECLINED = "DECLINED"
    const val OTHER = "OTHER"

    val outcomes = listOf(REPAIRED, REPLACEMENT, NO_FAULT, DECLINED, OTHER)

    val statuses = listOf(
        RECEIVED,
        UNDER_INSPECTION,
        SENT_TO_SERVICE,
        AWAITING_CUSTOMER,
        RESOLVED,
        REJECTED
    )

    enum class Coverage { ACTIVE, EXPIRED, NONE }

    fun label(status: String): String = when (status) {
        RECEIVED -> "Received"
        UNDER_INSPECTION -> "Under inspection"
        SENT_TO_SERVICE -> "Sent to service centre"
        AWAITING_CUSTOMER -> "Awaiting customer"
        RESOLVED -> "Resolved"
        REJECTED -> "Rejected"
        else -> status
    }

    fun needsResolution(status: String): Boolean = status == RESOLVED || status == REJECTED

    fun outcomeLabel(outcome: String): String = when (outcome) {
        REPAIRED -> "Repaired"
        REPLACEMENT -> "Replacement provided by provider"
        NO_FAULT -> "No fault found"
        DECLINED -> "Claim declined"
        OTHER -> "Other"
        else -> outcome
    }

    /** A provider replacement is the provider's own outcome. It is not a shop sale or stock movement. */
    fun statusForOutcome(outcome: String): String =
        if (outcome == DECLINED) REJECTED else RESOLVED

    fun handoverLabel(returned: Boolean, status: String = ""): String = when {
        returned -> HANDOVER_RETURNED
        status == AWAITING_CUSTOMER -> HANDOVER_WAITING
        else -> HANDOVER_NOT_YET
    }

    /** Recording where the item was sent does not reopen a claim that is already closed. */
    fun statusAfterServiceCentre(currentStatus: String, providerOutcome: String?): String {
        if (!providerOutcome.isNullOrBlank()) return currentStatus
        return when (currentStatus) {
            RESOLVED, REJECTED, AWAITING_CUSTOMER -> currentStatus
            else -> SENT_TO_SERVICE
        }
    }

    /**
     * Coverage uses the sale snapshot only. The end date is the last covered day.
     * A missing snapshot, or a snapshot without an end date, is [Coverage.NONE].
     */
    fun coverage(snapshot: WarrantyPolicy.Snapshot?, todayEpochDay: Long): Coverage {
        val end = snapshot?.endEpochDay ?: return Coverage.NONE
        if (snapshot.period <= 0 || snapshot.unit.isBlank()) return Coverage.NONE
        return if (end >= todayEpochDay) Coverage.ACTIVE else Coverage.EXPIRED
    }

    fun stateLabel(coverage: Coverage): String = when (coverage) {
        Coverage.ACTIVE -> ACTIVE
        Coverage.EXPIRED -> EXPIRED
        Coverage.NONE -> NO_RECORD
    }

    fun blockedMessage(coverage: Coverage): String? = when (coverage) {
        Coverage.ACTIVE -> null
        Coverage.EXPIRED -> BLOCKED_EXPIRED
        Coverage.NONE -> BLOCKED_NONE
    }

    /** Example: year 2026 and sequence 1 is WC-26-00001. The sequence does not restart each year. */
    fun reference(year: Int, sequence: Int): String {
        val yy = (year % 100).toString().padStart(2, '0')
        val number = sequence.coerceAtLeast(1).toString().padStart(5, '0')
        return "WC-$yy-$number"
    }

    fun yearOf(millis: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        return calendar.get(Calendar.YEAR)
    }

    fun today(millis: Long): Long = CivilDate.today(millis)
}

fun BillItem.claimSnapshot(): WarrantyPolicy.Snapshot? = warrantySnapshot()

fun WarrantyClaim.claimSnapshot(): WarrantyPolicy.Snapshot? = WarrantyPolicy.fromStored(
    warrantyPeriod,
    warrantyUnit,
    warrantyProvider,
    warrantyProviderName,
    warrantyTerms,
    warrantyStartEpochDay,
    warrantyEndEpochDay
)
