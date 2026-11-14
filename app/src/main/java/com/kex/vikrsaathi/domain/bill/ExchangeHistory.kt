package com.kex.vikrsaathi.domain.bill

/**
 * Links an original sale to the separate replacement bill created for an exchange.
 * The two bills stay separate records.
 */
object ExchangeHistory {

    enum class Role { ORIGINAL, REPLACEMENT }

    data class ReturnLink(
        val originalBillId: Long?,
        val replacementBillId: Long?,
        val voided: Boolean,
        val returnedQuantity: Int
    )

    data class Relation(
        val role: Role,
        val otherBillId: Long,
        val returnedQuantity: Int
    )

    fun relations(links: List<ReturnLink>): Map<Long, List<Relation>> {
        val result = linkedMapOf<Long, MutableList<Relation>>()
        links.filter { !it.voided && it.originalBillId != null && it.replacementBillId != null }
            .forEach { link ->
                val originalId = link.originalBillId ?: return@forEach
                val replacementId = link.replacementBillId ?: return@forEach
                val quantity = link.returnedQuantity.coerceAtLeast(0)
                result.getOrPut(originalId) { mutableListOf() }
                    .add(Relation(Role.ORIGINAL, replacementId, quantity))
                result.getOrPut(replacementId) { mutableListOf() }
                    .add(Relation(Role.REPLACEMENT, originalId, quantity))
            }
        return result
    }
}

object ExchangeReason {
    const val WRONG_ITEM = "Wrong item"
    const val DAMAGED = "Damaged or defective"
    const val CHANGED_MIND = "Customer changed mind"
    const val PRICE_DIFFERENCE = "Price difference"
    const val OTHER = "Other"

    val choices = listOf(WRONG_ITEM, DAMAGED, CHANGED_MIND, PRICE_DIFFERENCE, OTHER)

    enum class Problem { MISSING, NOTE_REQUIRED }

    fun problem(isExchange: Boolean, reason: String, note: String): Problem? {
        if (!isExchange) return null
        val selected = reason.trim()
        if (selected.isEmpty() || selected !in choices) return Problem.MISSING
        if (selected == OTHER && note.trim().isEmpty()) return Problem.NOTE_REQUIRED
        return null
    }
}
