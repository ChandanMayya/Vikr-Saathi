package com.kex.vikrsaathi.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderHubMathTest {

    @Test
    fun requestStatusFollowsQuotesAndAwards() {
        assertEquals(HubRecordStatus.DRAFT, OrderHubMath.requestStatus(RequestStatus.DRAFT, 0))
        assertEquals(HubRecordStatus.COLLECTING_QUOTES, OrderHubMath.requestStatus(RequestStatus.QUOTING, 1))
        assertEquals(HubRecordStatus.READY_TO_COMPARE, OrderHubMath.requestStatus(RequestStatus.QUOTING, 2))
        assertEquals(
            HubRecordStatus.PARTIALLY_AWARDED,
            OrderHubMath.requestStatus(RequestStatus.PARTIALLY_AWARDED, 3)
        )
        assertEquals(HubRecordStatus.AWARDED, OrderHubMath.requestStatus(RequestStatus.AWARDED, 2))
        assertEquals(HubRecordStatus.CANCELLED, OrderHubMath.requestStatus(RequestStatus.CANCELLED, 0))
    }

    @Test
    fun receiptQuantitiesDrivePurchaseOrderStatusAndAmount() {
        val rows = OrderHubMath.rows(
            requests = emptyList(),
            orders = listOf(
                order(1, createdAt = 100, lines = listOf(line(10, 5, 200))),
                order(2, createdAt = 200, lines = listOf(line(20, 4, 100))),
                order(3, createdAt = 300, lines = listOf(line(30, 4, 100)))
            ),
            receipts = listOf(
                HubReceiptInput(orderId = 2, orderLineId = 20, itemId = 1, receivedQty = 1),
                HubReceiptInput(orderId = 3, orderLineId = 30, itemId = 1, receivedQty = 4),
                HubReceiptInput(orderId = 99, orderLineId = null, itemId = 1, receivedQty = 9)
            )
        )
        assertEquals(HubRecordStatus.RECEIVED, rows.first { it.id == 3L }.status)
        assertEquals(HubRecordStatus.PARTIALLY_RECEIVED, rows.first { it.id == 2L }.status)
        assertEquals(HubRecordStatus.ORDERED, rows.first { it.id == 1L }.status)
        assertEquals(1_000L, rows.first { it.id == 1L }.amountPaise)
        assertTrue(OrderHubMath.showReceive(HubRecordStatus.PARTIALLY_RECEIVED))
        assertFalse(OrderHubMath.showReceive(HubRecordStatus.RECEIVED))
    }

    @Test
    fun receiptWithoutLineLinkMatchesTheOrderItem() {
        val received = OrderHubMath.receivedQuantities(
            orderId = 7,
            lines = listOf(line(1, 8, 10)),
            receipts = listOf(HubReceiptInput(orderId = 7, orderLineId = null, itemId = 4, receivedQty = 3))
        )
        assertEquals(3, received[1L])
    }

    @Test
    fun filtersKeepEveryStatusAndStillSurfaceOlderOpenWork() {
        val rows = OrderHubMath.rows(
            requests = listOf(
                HubRequestInput(1, "PR-001", 1_000, RequestStatus.CANCELLED, 1, 0),
                HubRequestInput(2, "PR-002", 5_000, RequestStatus.QUOTING, 2, 2),
                HubRequestInput(3, "PR-003", 1_000, RequestStatus.AWARDED, 1, 1)
            ),
            orders = listOf(order(8, createdAt = 1_000, lines = listOf(line(80, 2, 50)))),
            receipts = emptyList()
        )
        val ranged = OrderHubMath.filter(rows, HubTypeFilter.ALL, HubStatusFilter.ALL, 4_000, 6_000)
        assertEquals(listOf(2L), ranged.map { it.id })

        val open = OrderHubMath.filter(rows, HubTypeFilter.ALL, HubStatusFilter.OPEN, 4_000, 6_000)
        assertEquals(setOf(2L, 8L), open.map { it.id }.toSet())

        val requestsOnly = OrderHubMath.filter(rows, HubTypeFilter.REQUESTS, HubStatusFilter.ALL, null, null)
        assertTrue(requestsOnly.none { it.kind == HubRecordKind.PURCHASE_ORDER })
        assertTrue(requestsOnly.any { it.status == HubRecordStatus.CANCELLED })

        val counts = OrderHubMath.counts(rows, HubTypeFilter.ALL, 4_000, 6_000)
        assertEquals(1, counts.inProgress)
        assertEquals(0, counts.toReceive)
        assertEquals(0, counts.completed)
        assertEquals(2, counts.open)
    }

    @Test
    fun missingVendorAndEmptyOrderOmitTheAmount() {
        val rows = OrderHubMath.rows(
            requests = emptyList(),
            orders = listOf(order(4, createdAt = 10, vendorName = null, lines = emptyList())),
            receipts = emptyList()
        )
        assertNull(rows.single().vendorName)
        assertNull(rows.single().amountPaise)
        assertEquals(HubRecordStatus.ORDERED, rows.single().status)
    }

    private fun line(id: Long, quantity: Int, rate: Long) =
        HubOrderLineInput(id, itemId = 4, quantity = quantity, ratePaise = rate)

    private fun order(
        id: Long,
        createdAt: Long,
        lines: List<HubOrderLineInput>,
        vendorName: String? = "Shree"
    ) = HubOrderInput(id, vendorId = 1, vendorName = vendorName, number = "PO-$id", createdAt = createdAt, lines = lines)
}
