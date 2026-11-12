package com.kex.vikrsaathi.domain.bill

import com.kex.vikrsaathi.domain.inventory.CivilDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarrantyClaimPolicyTest {
    @Test
    fun coverageUsesTheSavedEndDateAndIncludesThatDay() {
        val today = CivilDate.of(2026, 11, 12)
        val active = WarrantyPolicy.Snapshot(
            12, WarrantyPolicy.MONTHS, WarrantyPolicy.MANUFACTURER,
            endEpochDay = CivilDate.of(2027, 11, 11)
        )
        assertEquals(WarrantyClaimPolicy.Coverage.ACTIVE, WarrantyClaimPolicy.coverage(active, today))
        assertEquals(
            WarrantyClaimPolicy.Coverage.ACTIVE,
            WarrantyClaimPolicy.coverage(active.copy(endEpochDay = today), today)
        )
        assertEquals(
            WarrantyClaimPolicy.Coverage.EXPIRED,
            WarrantyClaimPolicy.coverage(active.copy(endEpochDay = CivilDate.of(2026, 11, 11)), today)
        )
        assertEquals(WarrantyClaimPolicy.Coverage.NONE, WarrantyClaimPolicy.coverage(null, today))
        assertEquals(
            WarrantyClaimPolicy.Coverage.NONE,
            WarrantyClaimPolicy.coverage(active.copy(endEpochDay = null), today)
        )
        assertNull(WarrantyClaimPolicy.blockedMessage(WarrantyClaimPolicy.Coverage.ACTIVE))
        assertEquals(
            "A claim cannot be created because this sale has no saved warranty.",
            WarrantyClaimPolicy.blockedMessage(WarrantyClaimPolicy.Coverage.NONE)
        )
    }

    @Test
    fun referenceStaysDistinctFromAnInvoiceNumber() {
        assertEquals("WC-26-00001", WarrantyClaimPolicy.reference(2026, 1))
        assertEquals("WC-26-00002", WarrantyClaimPolicy.reference(2026, 2))
        assertEquals(true, WarrantyClaimPolicy.needsResolution(WarrantyClaimPolicy.RESOLVED))
        assertEquals(true, WarrantyClaimPolicy.needsResolution(WarrantyClaimPolicy.REJECTED))
        assertEquals(false, WarrantyClaimPolicy.needsResolution(WarrantyClaimPolicy.RECEIVED))
        assertEquals("Sent to service centre", WarrantyClaimPolicy.label(WarrantyClaimPolicy.SENT_TO_SERVICE))
    }
}
