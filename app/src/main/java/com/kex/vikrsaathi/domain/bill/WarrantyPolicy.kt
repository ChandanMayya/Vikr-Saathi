package com.kex.vikrsaathi.domain.bill

import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.BillVersionItem
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.domain.inventory.CivilDate
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Sale-time warranty terms. These are informational records of what the shop saved.
 * They do not decide whether a manufacturer or service center must accept a claim.
 *
 * The covered period includes the sale date and ends the day before the calendar
 * anniversary. A sale on 12 Nov 2026 with 12 months is valid until 11 Nov 2027.
 * Months and years are added on the UTC calendar. When the destination month has no
 * matching day, the calendar lands on that month's last day, and the final day is
 * then removed. One day of cover ends on the sale date itself.
 */
object WarrantyPolicy {
    const val DAYS = "DAYS"
    const val MONTHS = "MONTHS"
    const val YEARS = "YEARS"
    const val MANUFACTURER = "MANUFACTURER"
    const val SHOP = "SHOP"
    const val OTHER = "OTHER"
    const val NO_RECORD = "No warranty recorded"

    val units = listOf(DAYS, MONTHS, YEARS)
    val providers = listOf(MANUFACTURER, SHOP, OTHER)

    data class Snapshot(
        val period: Int,
        val unit: String,
        val provider: String,
        val providerName: String = "",
        val terms: String = "",
        val startEpochDay: Long? = null,
        val endEpochDay: Long? = null
    ) {
        fun withoutDates(): Snapshot = copy(startEpochDay = null, endEpochDay = null)

        fun sameTerms(other: Snapshot?): Boolean {
            if (other == null) return false
            return period == other.period &&
                unit == other.unit &&
                provider == other.provider &&
                providerName == other.providerName &&
                terms == other.terms
        }
    }

    fun termsFrom(item: Item): Snapshot? = termsFrom(
        applies = item.warrantyApplies,
        period = item.warrantyPeriod,
        unit = item.warrantyUnit,
        provider = item.warrantyProvider,
        providerName = item.warrantyProviderName,
        terms = item.warrantyTerms
    )

    /** New bill lines take the item master only while the shop setting is on. */
    fun forNewSale(item: Item, shopEnabled: Boolean): Snapshot? =
        if (shopEnabled) termsFrom(item) else null

    fun termsFrom(
        applies: Boolean,
        period: Int,
        unit: String,
        provider: String,
        providerName: String,
        terms: String
    ): Snapshot? {
        if (!applies || period <= 0 || unit !in units || provider !in providers) return null
        return Snapshot(
            period = period,
            unit = unit,
            provider = provider,
            providerName = providerName.trim(),
            terms = terms.trim()
        )
    }

    fun parsePeriod(text: String): Int? {
        val value = text.trim().toIntOrNull() ?: return null
        return value.takeIf { it > 0 }
    }

    fun fromStored(
        period: Int?,
        unit: String?,
        provider: String?,
        providerName: String?,
        terms: String?,
        startEpochDay: Long?,
        endEpochDay: Long?
    ): Snapshot? {
        if (period == null || period <= 0 || unit.isNullOrBlank()) return null
        return Snapshot(
            period = period,
            unit = unit,
            provider = provider.orEmpty(),
            providerName = providerName.orEmpty(),
            terms = terms.orEmpty(),
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay
        )
    }

    /**
     * Keeps a snapshot that already has both dates. Otherwise the start is the
     * calendar day of [saleMillis] and the end follows [endEpochDay].
     * [saleMillis] is the bill's sale date. An edit passes the original sale
     * date, not the time of the edit, so the preview and the stored row match.
     */
    fun resolve(snapshot: Snapshot?, saleMillis: Long): Snapshot? {
        if (snapshot == null) return null
        if (snapshot.startEpochDay != null && snapshot.endEpochDay != null) return snapshot
        val start = CivilDate.today(saleMillis)
        val end = endEpochDay(start, snapshot.period, snapshot.unit) ?: return null
        return snapshot.copy(startEpochDay = start, endEpochDay = end)
    }

    fun endEpochDay(startEpochDay: Long, period: Int, unit: String): Long? {
        if (period <= 0) return null
        val field = when (unit) {
            DAYS -> Calendar.DAY_OF_MONTH
            MONTHS -> Calendar.MONTH
            YEARS -> Calendar.YEAR
            else -> return null
        }
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = startEpochDay * 86_400_000L
        utc.add(field, period)
        utc.add(Calendar.DAY_OF_MONTH, -1)
        return CivilDate.of(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH) + 1,
            utc.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun periodLabel(period: Int, unit: String): String {
        val word = when (unit) {
            DAYS -> if (period == 1) "day" else "days"
            MONTHS -> if (period == 1) "month" else "months"
            YEARS -> if (period == 1) "year" else "years"
            else -> return ""
        }
        return "$period $word"
    }

    fun headline(snapshot: Snapshot): String = "Warranty: ${periodLabel(snapshot.period, snapshot.unit)}"

    fun validUntil(snapshot: Snapshot): String? {
        val end = snapshot.endEpochDay ?: return null
        return "Valid until ${formatDate(end)}"
    }

    /** Provider name, otherwise the provider type, plus terms when the shop saved them. */
    fun extraLine(snapshot: Snapshot): String? {
        val provider = snapshot.providerName.trim().ifBlank { providerLabel(snapshot.provider) }
        val terms = snapshot.terms.trim()
        return listOf(provider, terms).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }
    }

    fun providerLabel(provider: String): String = when (provider) {
        MANUFACTURER -> "Manufacturer"
        SHOP -> "Shop"
        OTHER -> "Other"
        else -> ""
    }

    fun formatDate(epochDay: Long): String {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = epochDay * 86_400_000L
        val month = utc.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US)
        return "${utc.get(Calendar.DAY_OF_MONTH)} $month ${utc.get(Calendar.YEAR)}"
    }

    fun printText(itemName: String, snapshot: Snapshot?): String {
        if (snapshot == null || snapshot.endEpochDay == null) return itemName
        return buildString {
            append(itemName)
            append('\n')
            append(headline(snapshot))
            validUntil(snapshot)?.let {
                append('\n')
                append(it)
            }
            extraLine(snapshot)?.let {
                append('\n')
                append(it)
            }
        }
    }

    fun sameTerms(left: Snapshot?, right: Snapshot?): Boolean = when {
        left == null && right == null -> true
        left == null || right == null -> false
        else -> left.sameTerms(right)
    }
}

fun BillItem.warrantySnapshot(): WarrantyPolicy.Snapshot? = WarrantyPolicy.fromStored(
    warrantyPeriod, warrantyUnit, warrantyProvider, warrantyProviderName, warrantyTerms,
    warrantyStartEpochDay, warrantyEndEpochDay
)

fun BillVersionItem.warrantySnapshot(): WarrantyPolicy.Snapshot? = WarrantyPolicy.fromStored(
    warrantyPeriod, warrantyUnit, warrantyProvider, warrantyProviderName, warrantyTerms,
    warrantyStartEpochDay, warrantyEndEpochDay
)
