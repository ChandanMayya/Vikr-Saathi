package com.kex.vikrsaathi.domain.finance

enum class HubRecordKind {
    REQUEST,
    PURCHASE_ORDER
}

enum class HubRecordStatus {
    DRAFT,
    COLLECTING_QUOTES,
    READY_TO_COMPARE,
    PARTIALLY_AWARDED,
    AWARDED,
    CANCELLED,
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED
}

enum class HubTypeFilter {
    ALL,
    REQUESTS,
    PURCHASE_ORDERS
}

enum class HubStatusFilter {
    ALL,
    IN_PROGRESS,
    TO_RECEIVE,
    COMPLETED,
    OPEN
}

data class HubRequestInput(
    val id: Long,
    val number: String,
    val createdAt: Long,
    val status: String,
    val lineCount: Int,
    val quoteCount: Int
)

data class HubOrderLineInput(
    val lineId: Long,
    val itemId: Long?,
    val quantity: Int,
    val ratePaise: Long
)

data class HubOrderInput(
    val id: Long,
    val vendorId: Long,
    val vendorName: String?,
    val number: String,
    val createdAt: Long,
    val lines: List<HubOrderLineInput>
)

data class HubReceiptInput(
    val orderId: Long?,
    val orderLineId: Long?,
    val itemId: Long?,
    val receivedQty: Int
)

data class OrderHubRow(
    val kind: HubRecordKind,
    val id: Long,
    val number: String,
    val dateMillis: Long,
    val vendorId: Long?,
    val vendorName: String?,
    val lineCount: Int?,
    val amountPaise: Long?,
    val status: HubRecordStatus
)

data class HubCounts(
    val inProgress: Int,
    val toReceive: Int,
    val completed: Int,
    val open: Int
)

object OrderHubMath {

    fun rows(
        requests: List<HubRequestInput>,
        orders: List<HubOrderInput>,
        receipts: List<HubReceiptInput>
    ): List<OrderHubRow> {
        val requestRows = requests.map { request ->
            OrderHubRow(
                kind = HubRecordKind.REQUEST,
                id = request.id,
                number = request.number,
                dateMillis = request.createdAt,
                vendorId = null,
                vendorName = null,
                lineCount = request.lineCount,
                amountPaise = null,
                status = requestStatus(request.status, request.quoteCount)
            )
        }
        val orderRows = orders.map { order ->
            val received = receivedQuantities(order.id, order.lines, receipts)
            OrderHubRow(
                kind = HubRecordKind.PURCHASE_ORDER,
                id = order.id,
                number = order.number,
                dateMillis = order.createdAt,
                vendorId = order.vendorId,
                vendorName = order.vendorName,
                lineCount = null,
                amountPaise = if (order.lines.isEmpty()) null else order.lines.sumOf { it.ratePaise * it.quantity },
                status = purchaseOrderStatus(order.lines, received)
            )
        }
        return (requestRows + orderRows).sortedWith(
            compareByDescending<OrderHubRow> { it.dateMillis }.thenByDescending { it.id }
        )
    }

    fun requestStatus(persisted: String, quoteCount: Int): HubRecordStatus = when (persisted) {
        RequestStatus.CANCELLED -> HubRecordStatus.CANCELLED
        RequestStatus.AWARDED -> HubRecordStatus.AWARDED
        RequestStatus.PARTIALLY_AWARDED -> HubRecordStatus.PARTIALLY_AWARDED
        RequestStatus.QUOTING -> if (quoteCount >= 2) {
            HubRecordStatus.READY_TO_COMPARE
        } else {
            HubRecordStatus.COLLECTING_QUOTES
        }
        else -> HubRecordStatus.DRAFT
    }

    fun receivedQuantities(
        orderId: Long,
        lines: List<HubOrderLineInput>,
        receipts: List<HubReceiptInput>
    ): Map<Long, Int> {
        val totals = lines.associate { it.lineId to 0 }.toMutableMap()
        receipts.forEach { receipt ->
            val linked = receipt.orderLineId
            if (linked != null && linked in totals) {
                totals[linked] = totals.getValue(linked) + receipt.receivedQty
                return@forEach
            }
            if (linked == null && receipt.orderId == orderId) {
                val match = lines.firstOrNull { it.itemId != null && it.itemId == receipt.itemId } ?: return@forEach
                totals[match.lineId] = totals.getValue(match.lineId) + receipt.receivedQty
            }
        }
        return totals
    }

    fun purchaseOrderStatus(
        lines: List<HubOrderLineInput>,
        receivedByLine: Map<Long, Int>
    ): HubRecordStatus {
        if (lines.isEmpty()) return HubRecordStatus.ORDERED
        val anyReceived = lines.any { (receivedByLine[it.lineId] ?: 0) > 0 }
        val fullyReceived = lines.all { (receivedByLine[it.lineId] ?: 0) >= it.quantity }
        return when {
            fullyReceived -> HubRecordStatus.RECEIVED
            anyReceived -> HubRecordStatus.PARTIALLY_RECEIVED
            else -> HubRecordStatus.ORDERED
        }
    }

    fun isOpen(status: HubRecordStatus): Boolean = when (status) {
        HubRecordStatus.AWARDED,
        HubRecordStatus.CANCELLED,
        HubRecordStatus.RECEIVED -> false
        else -> true
    }

    fun showReceive(status: HubRecordStatus): Boolean =
        status == HubRecordStatus.ORDERED || status == HubRecordStatus.PARTIALLY_RECEIVED

    fun filter(
        rows: List<OrderHubRow>,
        type: HubTypeFilter,
        status: HubStatusFilter,
        fromInclusive: Long?,
        toInclusive: Long?
    ): List<OrderHubRow> {
        return rows.filter { row ->
            matchesType(row, type) &&
                matchesStatus(row, status) &&
                (status == HubStatusFilter.OPEN || inRange(row.dateMillis, fromInclusive, toInclusive))
        }
    }

    fun counts(
        rows: List<OrderHubRow>,
        type: HubTypeFilter,
        fromInclusive: Long?,
        toInclusive: Long?
    ): HubCounts {
        val typed = rows.filter { matchesType(it, type) }
        val dated = typed.filter { inRange(it.dateMillis, fromInclusive, toInclusive) }
        return HubCounts(
            inProgress = dated.count { matchesStatus(it, HubStatusFilter.IN_PROGRESS) },
            toReceive = dated.count { matchesStatus(it, HubStatusFilter.TO_RECEIVE) },
            completed = dated.count { matchesStatus(it, HubStatusFilter.COMPLETED) },
            open = typed.count { matchesStatus(it, HubStatusFilter.OPEN) }
        )
    }

    private fun matchesType(row: OrderHubRow, type: HubTypeFilter): Boolean = when (type) {
        HubTypeFilter.ALL -> true
        HubTypeFilter.REQUESTS -> row.kind == HubRecordKind.REQUEST
        HubTypeFilter.PURCHASE_ORDERS -> row.kind == HubRecordKind.PURCHASE_ORDER
    }

    private fun matchesStatus(row: OrderHubRow, status: HubStatusFilter): Boolean = when (status) {
        HubStatusFilter.ALL -> true
        HubStatusFilter.IN_PROGRESS -> row.status in IN_PROGRESS
        HubStatusFilter.TO_RECEIVE -> showReceive(row.status)
        HubStatusFilter.COMPLETED -> row.status == HubRecordStatus.AWARDED || row.status == HubRecordStatus.RECEIVED
        HubStatusFilter.OPEN -> isOpen(row.status)
    }

    private fun inRange(dateMillis: Long, fromInclusive: Long?, toInclusive: Long?): Boolean {
        if (fromInclusive == null || toInclusive == null) return true
        return dateMillis in fromInclusive..toInclusive
    }

    private val IN_PROGRESS = setOf(
        HubRecordStatus.DRAFT,
        HubRecordStatus.COLLECTING_QUOTES,
        HubRecordStatus.READY_TO_COMPARE,
        HubRecordStatus.PARTIALLY_AWARDED,
        HubRecordStatus.ORDERED,
        HubRecordStatus.PARTIALLY_RECEIVED
    )
}
