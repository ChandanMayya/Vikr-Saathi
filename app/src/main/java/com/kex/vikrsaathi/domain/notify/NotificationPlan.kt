package com.kex.vikrsaathi.domain.notify

import java.util.Calendar
import java.util.TimeZone

data class WallTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23 && minute in 0..59)
    }

    fun label(): String = "%02d:%02d".format(hour, minute)
}

data class QuietHours(val start: WallTime, val end: WallTime)

enum class NotifyKind {
    PAYMENT_DUE,
    CUSTOMER_DIGEST,
    VENDOR_DIGEST,
    DAILY_SALES,
    WEEKLY_SALES,
    LOW_STOCK,
    ORDER_FOLLOWUP,
    EXPIRY,
    BACKUP
}

enum class RepeatCadence { OFF, DAILY, WEEKLY, MONTHLY }

enum class Delivery { WAIT, SEND, SKIP }

data class TypeSchedule(
    val enabled: Boolean = false,
    val customTime: WallTime? = null,
    /** Calendar weekday, used by weekly types. Sunday is 1. */
    val weekday: Int = Calendar.MONDAY,
    val dayOfMonth: Int = 1
) {
    fun effectiveTime(global: WallTime): WallTime = customTime ?: global
}

object NotificationTimes {
    fun effective(custom: WallTime?, global: WallTime): WallTime = custom ?: global

    /**
     * A check may run later than the chosen minute. [catchUpMinutes] is how long after
     * that minute the notice is still worth sending. Quiet hours hold it only while the
     * catch-up window is still open.
     */
    fun decide(
        nowMillis: Long,
        zone: TimeZone,
        scheduled: WallTime,
        weekday: Int?,
        dayOfMonth: Int?,
        quiet: QuietHours?,
        catchUpMinutes: Int,
        alreadyHandled: Boolean
    ): Delivery {
        if (alreadyHandled) return Delivery.SKIP
        val now = calendar(nowMillis, zone)
        if (weekday != null && now.get(Calendar.DAY_OF_WEEK) != weekday) return Delivery.WAIT
        if (dayOfMonth != null && now.get(Calendar.DAY_OF_MONTH) != dayOfMonth) return Delivery.WAIT
        val scheduledAt = calendar(nowMillis, zone).apply {
            set(Calendar.HOUR_OF_DAY, scheduled.hour)
            set(Calendar.MINUTE, scheduled.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = scheduledAt.timeInMillis
        val end = start + catchUpMinutes.coerceAtLeast(0) * 60_000L
        if (nowMillis < start) return Delivery.WAIT
        if (nowMillis > end) return Delivery.SKIP
        if (quiet != null && inQuietHours(now, quiet)) return Delivery.WAIT
        return Delivery.SEND
    }

    fun inQuietHours(now: Calendar, quiet: QuietHours): Boolean {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = quiet.start.hour * 60 + quiet.start.minute
        val end = quiet.end.hour * 60 + quiet.end.minute
        return if (start == end) {
            false
        } else if (start < end) {
            minute in start until end
        } else {
            minute >= start || minute < end
        }
    }

    fun slotKey(kind: NotifyKind, nowMillis: Long, zone: TimeZone, weekly: Boolean, monthly: Boolean): String {
        val now = calendar(nowMillis, zone)
        val date = "%04d-%02d-%02d".format(
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )
        return when {
            monthly -> "$kind:${now.get(Calendar.YEAR)}-${now.get(Calendar.MONTH) + 1}"
            weekly -> "$kind:${now.get(Calendar.WEEK_OF_YEAR)}-${now.get(Calendar.YEAR)}"
            else -> "$kind:$date"
        }
    }

    fun calendar(nowMillis: Long, zone: TimeZone): Calendar =
        Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
}

data class DueCandidate(
    val key: String,
    val outstandingPaise: Long,
    val dueEpochDay: Long?
)

enum class DueState { OVERDUE, DUE_SOON, UPCOMING, NONE }

object DueDates {
    fun state(dueEpochDay: Long?, today: Long, leadDays: Int, graceDays: Int): DueState {
        if (dueEpochDay == null) return DueState.NONE
        val overdueAfter = dueEpochDay + graceDays.coerceAtLeast(0)
        return when {
            today > overdueAfter -> DueState.OVERDUE
            today >= dueEpochDay - leadDays.coerceAtLeast(0) -> DueState.DUE_SOON
            else -> DueState.UPCOMING
        }
    }

    fun reminderCandidates(
        items: List<DueCandidate>,
        today: Long,
        leadDays: Int,
        graceDays: Int,
        minOutstandingPaise: Long,
        includeDueDate: Boolean,
        overdueCadence: RepeatCadence
    ): List<DueCandidate> {
        return items.filter { item ->
            if (item.outstandingPaise < minOutstandingPaise || item.outstandingPaise <= 0L) return@filter false
            when (DueDates.state(item.dueEpochDay, today, leadDays, graceDays)) {
                DueState.NONE, DueState.UPCOMING -> false
                DueState.DUE_SOON -> includeDueDate || item.dueEpochDay != today
                DueState.OVERDUE -> overdueCadence != RepeatCadence.OFF
            }
        }
    }
}

data class ShopSnapshot(
    val dues: List<DueCandidate> = emptyList(),
    val receivablePaise: Long = 0L,
    val overduePaise: Long = 0L,
    val payablePaise: Long = 0L,
    val vendorOverduePaise: Long = 0L,
    val vendorDueDatesRecorded: Boolean = false,
    val yesterdaySalesPaise: Long = 0L,
    val yesterdayBills: Int = 0,
    val weekSalesPaise: Long = 0L,
    val weekBills: Int = 0,
    val previousWeekSalesPaise: Long = 0L,
    val previousWeekBills: Int = 0,
    val lowStockCount: Int = 0,
    val lowStockKey: String = "",
    val ordersNeedingAttention: Int = 0,
    val ordersPastDelivery: Int = 0,
    val expiredBatches: Int = 0,
    val expiringBatches: Int = 0,
    val lastManualBackupAt: Long? = null,
    val customerBalancesEnabled: Boolean = true,
    val vendorEnabled: Boolean = true,
    val ordersEnabled: Boolean = true,
    val expiryEnabled: Boolean = false
)

data class PlannedNotice(
    val kind: NotifyKind,
    val slotKey: String,
    val title: String,
    val body: String,
    val destination: String
)

data class PlanResult(
    val notices: List<PlannedNotice> = emptyList(),
    val closedSlots: List<String> = emptyList()
)

data class NoticeRules(
    val globalTime: WallTime = WallTime(9, 0),
    val quiet: QuietHours? = null,
    val catchUpMinutes: Int = 180,
    val types: Map<NotifyKind, TypeSchedule> = emptyMap(),
    val leadDays: Int = 3,
    val graceDays: Int = 0,
    val minOutstandingPaise: Long = 100L,
    val notifyOnDueDate: Boolean = true,
    val overdueCadence: RepeatCadence = RepeatCadence.WEEKLY,
    val lowStockWeekly: Boolean = false,
    val skipEmptySales: Boolean = true,
    val backupAfterDays: Int = 30,
    val snoozeUntil: Map<NotifyKind, Long> = emptyMap()
)

object NotificationPlanner {
    fun plan(
        nowMillis: Long,
        zone: TimeZone,
        today: Long,
        rules: NoticeRules,
        shop: ShopSnapshot,
        handledSlots: Set<String>,
        allowed: Boolean
    ): PlanResult {
        if (!allowed) return PlanResult()
        val notices = mutableListOf<PlannedNotice>()
        val closed = mutableListOf<String>()
        NotifyKind.entries.forEach { kind ->
            val schedule = rules.types[kind] ?: TypeSchedule()
            if (!schedule.enabled || !featureOn(kind, shop)) return@forEach
            val snooze = rules.snoozeUntil[kind] ?: 0L
            if (snooze > nowMillis) return@forEach
            if (kind == NotifyKind.PAYMENT_DUE) {
                val candidates = DueDates.reminderCandidates(
                    shop.dues, today, rules.leadDays, rules.graceDays,
                    rules.minOutstandingPaise, rules.notifyOnDueDate, rules.overdueCadence
                )
                val dueSoon = candidates.filter {
                    DueDates.state(it.dueEpochDay, today, rules.leadDays, rules.graceDays) != DueState.OVERDUE
                }
                val includeOverdue = when (rules.overdueCadence) {
                    RepeatCadence.OFF -> false
                    RepeatCadence.DAILY -> true
                    RepeatCadence.WEEKLY ->
                        NotificationTimes.calendar(nowMillis, zone).get(Calendar.DAY_OF_WEEK) == schedule.weekday
                    RepeatCadence.MONTHLY -> false
                }
                consider(
                    kind, schedule, rules, shop, today, nowMillis, zone, handledSlots, notices, closed,
                    weekly = dueSoon.isEmpty() && rules.overdueCadence == RepeatCadence.WEEKLY,
                    monthly = false,
                    includeOverdue = includeOverdue
                )
                return@forEach
            }
            val weekly = kind == NotifyKind.CUSTOMER_DIGEST || kind == NotifyKind.VENDOR_DIGEST ||
                kind == NotifyKind.WEEKLY_SALES || (kind == NotifyKind.LOW_STOCK && rules.lowStockWeekly)
            consider(
                kind, schedule, rules, shop, today, nowMillis, zone, handledSlots, notices, closed,
                weekly = weekly, monthly = kind == NotifyKind.BACKUP, includeOverdue = true
            )
        }
        return PlanResult(notices, closed)
    }

    private fun consider(
        kind: NotifyKind,
        schedule: TypeSchedule,
        rules: NoticeRules,
        shop: ShopSnapshot,
        today: Long,
        nowMillis: Long,
        zone: TimeZone,
        handledSlots: Set<String>,
        notices: MutableList<PlannedNotice>,
        closed: MutableList<String>,
        weekly: Boolean,
        monthly: Boolean,
        includeOverdue: Boolean
    ) {
        val snooze = rules.snoozeUntil[kind] ?: 0L
        val snoozeActive = snooze > 0L && nowMillis >= snooze &&
            nowMillis <= snooze + rules.catchUpMinutes.coerceAtLeast(0) * 60_000L
        val base = NotificationTimes.slotKey(kind, nowMillis, zone, weekly, monthly)
        val slot = when {
            snoozeActive -> "snooze:${kind.name}:$snooze"
            kind == NotifyKind.LOW_STOCK && shop.lowStockKey.isNotEmpty() -> "$base:${shop.lowStockKey}"
            else -> base
        }
        if (snoozeActive && slot in handledSlots) return
        val decision = if (snoozeActive) {
            val quiet = rules.quiet != null &&
                NotificationTimes.inQuietHours(NotificationTimes.calendar(nowMillis, zone), rules.quiet)
            if (quiet) Delivery.WAIT else Delivery.SEND
        } else {
            NotificationTimes.decide(
                nowMillis = nowMillis,
                zone = zone,
                scheduled = schedule.effectiveTime(rules.globalTime),
                weekday = if (weekly) schedule.weekday else null,
                dayOfMonth = if (monthly) schedule.dayOfMonth else null,
                quiet = rules.quiet,
                catchUpMinutes = rules.catchUpMinutes,
                alreadyHandled = slot in handledSlots
            )
        }
        if (decision == Delivery.SKIP && slot !in handledSlots) {
            closed += slot
            return
        }
        if (decision != Delivery.SEND || slot in handledSlots) return
        val notice = content(kind, rules, shop, today, nowMillis, zone, includeOverdue) ?: run {
            closed += slot
            return
        }
        notices += notice.copy(slotKey = slot)
    }

    private fun featureOn(kind: NotifyKind, shop: ShopSnapshot): Boolean = when (kind) {
        NotifyKind.PAYMENT_DUE, NotifyKind.CUSTOMER_DIGEST -> shop.customerBalancesEnabled
        NotifyKind.VENDOR_DIGEST -> shop.vendorEnabled
        NotifyKind.ORDER_FOLLOWUP -> shop.ordersEnabled
        NotifyKind.EXPIRY -> shop.expiryEnabled
        else -> true
    }

    private fun content(
        kind: NotifyKind,
        rules: NoticeRules,
        shop: ShopSnapshot,
        today: Long,
        nowMillis: Long,
        zone: TimeZone,
        includeOverdue: Boolean
    ): PlannedNotice? {
        return when (kind) {
            NotifyKind.PAYMENT_DUE -> {
                val due = DueDates.reminderCandidates(
                    shop.dues, today, rules.leadDays, rules.graceDays,
                    rules.minOutstandingPaise, rules.notifyOnDueDate, rules.overdueCadence
                ).filter { item ->
                    val overdue = DueDates.state(item.dueEpochDay, today, rules.leadDays, rules.graceDays) == DueState.OVERDUE
                    !overdue || includeOverdue
                }
                if (due.isEmpty()) null else PlannedNotice(
                    kind, "", "Payment reminders",
                    "${due.size} bills or balances need attention. Outstanding ${rupees(due.sumOf { it.outstandingPaise })}.",
                    "outstanding"
                )
            }
            NotifyKind.CUSTOMER_DIGEST -> PlannedNotice(
                kind, "", "Customer balances",
                "Customers owe ${rupees(shop.receivablePaise)}. Overdue ${rupees(shop.overduePaise)}.",
                "outstanding"
            )
            NotifyKind.VENDOR_DIGEST -> {
                val overdue = if (shop.vendorDueDatesRecorded) {
                    " Overdue ${rupees(shop.vendorOverduePaise)}."
                } else {
                    ""
                }
                PlannedNotice(
                    kind, "", "Vendor balances",
                    "You owe vendors ${rupees(shop.payablePaise)}.$overdue",
                    "vendors"
                )
            }
            NotifyKind.DAILY_SALES -> {
                if (rules.skipEmptySales && shop.yesterdayBills == 0) null else PlannedNotice(
                    kind, "", "Yesterday's sales",
                    "${shop.yesterdayBills} bills, ${rupees(shop.yesterdaySalesPaise)}.",
                    "bills-yesterday"
                )
            }
            NotifyKind.WEEKLY_SALES -> PlannedNotice(
                kind, "", "Weekly sales",
                "${shop.weekBills} bills, ${rupees(shop.weekSalesPaise)}. Previous week ${rupees(shop.previousWeekSalesPaise)}.",
                "dashboard-week"
            )
            NotifyKind.LOW_STOCK -> {
                if (shop.lowStockCount == 0) null else PlannedNotice(
                    kind, "", "Low stock",
                    "${shop.lowStockCount} items are at or below the low-stock line.",
                    "stock"
                )
            }
            NotifyKind.ORDER_FOLLOWUP -> {
                if (shop.ordersNeedingAttention == 0 && shop.ordersPastDelivery == 0) null else PlannedNotice(
                    kind, "", "Purchase orders",
                    "${shop.ordersNeedingAttention} orders need attention. ${shop.ordersPastDelivery} are past the expected delivery date.",
                    "orders"
                )
            }
            NotifyKind.EXPIRY -> {
                if (shop.expiredBatches == 0 && shop.expiringBatches == 0) null else PlannedNotice(
                    kind, "", "Expiry",
                    "${shop.expiredBatches} lots are expired. ${shop.expiringBatches} expire soon.",
                    "stock-expired"
                )
            }
            NotifyKind.BACKUP -> {
                val stale = shop.lastManualBackupAt == null ||
                    nowMillis - shop.lastManualBackupAt > rules.backupAfterDays * 86_400_000L
                if (!stale) null else PlannedNotice(
                    kind, "", "Backup reminder",
                    "There is no recent manual backup on this device.",
                    "backup"
                )
            }
        }
    }

    private fun rupees(paise: Long): String = "₹${paise / 100}.${(kotlin.math.abs(paise % 100)).toString().padStart(2, '0')}"
}

object NotificationAccessPolicy {
    fun canPost(sdk: Int, appEnabled: Boolean, permissionGranted: Boolean, channelEnabled: Boolean): Boolean {
        if (!appEnabled || !channelEnabled) return false
        return sdk < 33 || permissionGranted
    }
}
