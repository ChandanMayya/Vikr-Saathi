package com.kex.vikrsaathi.data.repository

import com.kex.vikrsaathi.data.dao.BillDraftDao
import com.kex.vikrsaathi.data.draft.BillDraftCodec
import com.kex.vikrsaathi.data.draft.HeldBillRestore
import com.kex.vikrsaathi.data.draft.HeldDraftSummary
import com.kex.vikrsaathi.data.entity.BillDraftEntity
import com.kex.vikrsaathi.data.model.BillLineItem

class BillDraftRepository(
    private val billDraftDao: BillDraftDao
) {

    suspend fun getAllHeldSummaries(): List<HeldDraftSummary> =
        billDraftDao.getAll().map { it.toSummary() }

    suspend fun holdBill(
        customerId: Long?,
        customerName: String,
        buyerAddress: String,
        buyerPhone: String,
        lineItems: List<BillLineItem>
    ): HeldDraftSummary {
        val total = lineItems.sumOf { it.lineTotal }
        val id = billDraftDao.insert(
            BillDraftEntity(
                customerId = customerId,
                customerName = customerName.ifBlank { "Walk-in customer" },
                buyerAddress = buyerAddress,
                buyerPhone = buyerPhone,
                lineItemsJson = BillDraftCodec.encodeLineItems(lineItems),
                grandTotal = total,
                itemCount = lineItems.sumOf { it.quantity },
                heldAt = System.currentTimeMillis()
            )
        )
        return billDraftDao.getById(id)?.toSummary()
            ?: HeldDraftSummary(
                id = id,
                customerName = customerName.ifBlank { "Walk-in customer" },
                itemCount = lineItems.sumOf { it.quantity },
                grandTotal = total,
                heldAt = System.currentTimeMillis()
            )
    }

    suspend fun consumeHeldBill(draftId: Long): HeldBillRestore? {
        val draft = billDraftDao.getById(draftId) ?: return null
        billDraftDao.deleteById(draftId)
        return HeldBillRestore(
            id = draft.id,
            customerId = draft.customerId,
            customerName = draft.customerName,
            buyerAddress = draft.buyerAddress,
            buyerPhone = draft.buyerPhone,
            lineItems = BillDraftCodec.decodeLineItems(draft.lineItemsJson)
        )
    }

    suspend fun deleteHeldBill(draftId: Long) {
        billDraftDao.deleteById(draftId)
    }

    private fun BillDraftEntity.toSummary() = HeldDraftSummary(
        id = id,
        customerName = customerName,
        itemCount = itemCount,
        grandTotal = grandTotal,
        heldAt = heldAt
    )
}
