package com.kex.vikrsaathi.domain.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NotificationPlanTest {

    private val zone = TimeZone.getTimeZone("Asia/Kolkata")

    @Test
    fun customTimeWinsAndSurvivesGlobalChange() {
        val global = WallTime(9, 0)
        val custom = WallTime(18, 30)
        assertEquals(custom, TypeSchedule(customTime = custom).effectiveTime(global))
        assertEquals(custom, TypeSchedule(customTime = custom).effectiveTime(WallTime(7, 15)))
        assertEquals(WallTime(7, 15), TypeSchedule().effectiveTime(WallTime(7, 15)))
    }

    @Test
    fun clearingCustomTimeInheritsLaterGlobalChanges() {
        val schedule = TypeSchedule(enabled = true, customTime = null)
        assertEquals(WallTime(9, 0), schedule.effectiveTime(WallTime(9, 0)))
        assertEquals(WallTime(11, 45), schedule.effectiveTime(WallTime(11, 45)))
    }

    @Test
    fun quietHoursWrapMidnightAndEqualBoundsAreOff() {
        val calendar = calendarAt(22, 30)
        assertTrue(NotificationTimes.inQuietHours(calendar, QuietHours(WallTime(22, 0), WallTime(7, 0))))
        assertFalse(NotificationTimes.inQuietHours(calendarAt(12, 0), QuietHours(WallTime(22, 0), WallTime(7, 0))))
        assertTrue(NotificationTimes.inQuietHours(calendarAt(6, 0), QuietHours(WallTime(22, 0), WallTime(7, 0))))
        assertFalse(NotificationTimes.inQuietHours(calendarAt(12, 0), QuietHours(WallTime(9, 0), WallTime(9, 0))))
    }

    @Test
    fun deliveryWaitsBeforeWindowSkipsAfterCatchUpAndWaitsInQuietHours() {
        val day = at(2026, Calendar.NOVEMBER, 11, 8, 0)
        assertEquals(
            Delivery.WAIT,
            NotificationTimes.decide(day, zone, WallTime(9, 0), null, null, null, 180, false)
        )
        val onTime = at(2026, Calendar.NOVEMBER, 11, 9, 20)
        assertEquals(
            Delivery.SEND,
            NotificationTimes.decide(onTime, zone, WallTime(9, 0), null, null, null, 180, false)
        )
        val quiet = QuietHours(WallTime(9, 0), WallTime(10, 0))
        assertEquals(
            Delivery.WAIT,
            NotificationTimes.decide(onTime, zone, WallTime(9, 0), null, null, quiet, 180, false)
        )
        val late = at(2026, Calendar.NOVEMBER, 11, 13, 0)
        assertEquals(
            Delivery.SKIP,
            NotificationTimes.decide(late, zone, WallTime(9, 0), null, null, null, 180, false)
        )
        assertEquals(
            Delivery.SKIP,
            NotificationTimes.decide(onTime, zone, WallTime(9, 0), null, null, null, 180, true)
        )
    }

    @Test
    fun weeklyWaitOnTheWrongWeekday() {
        val wednesday = at(2026, Calendar.NOVEMBER, 11, 9, 0)
        assertEquals(Calendar.WEDNESDAY, NotificationTimes.calendar(wednesday, zone).get(Calendar.DAY_OF_WEEK))
        assertEquals(
            Delivery.WAIT,
            NotificationTimes.decide(wednesday, zone, WallTime(9, 0), Calendar.MONDAY, null, null, 180, false)
        )
        assertEquals(
            Delivery.SEND,
            NotificationTimes.decide(wednesday, zone, WallTime(9, 0), Calendar.WEDNESDAY, null, null, 180, false)
        )
    }

    @Test
    fun slotKeysChangeWithDayWeekAndMonth() {
        val now = at(2026, Calendar.NOVEMBER, 11, 9, 0)
        assertTrue(NotificationTimes.slotKey(NotifyKind.DAILY_SALES, now, zone, false, false).contains("2026-11-11"))
        val weekly = NotificationTimes.slotKey(NotifyKind.WEEKLY_SALES, now, zone, true, false)
        val next = at(2026, Calendar.NOVEMBER, 18, 9, 0)
        assertTrue(weekly != NotificationTimes.slotKey(NotifyKind.WEEKLY_SALES, next, zone, true, false))
        assertTrue(NotificationTimes.slotKey(NotifyKind.BACKUP, now, zone, false, true).endsWith("2026-11"))
    }

    @Test
    fun timezoneShiftChangesTheLocalDay() {
        val utc = TimeZone.getTimeZone("UTC")
        val india = TimeZone.getTimeZone("Asia/Kolkata")
        val instant = at(2026, Calendar.NOVEMBER, 11, 2, 30, utc)
        val utcKey = NotificationTimes.slotKey(NotifyKind.DAILY_SALES, instant, utc, false, false)
        val indiaKey = NotificationTimes.slotKey(NotifyKind.DAILY_SALES, instant, india, false, false)
        assertTrue(utcKey.contains("2026-11-11"))
        assertTrue(indiaKey.contains("2026-11-11"))
        val lateUtc = at(2026, Calendar.NOVEMBER, 11, 20, 0, utc)
        assertTrue(NotificationTimes.slotKey(NotifyKind.DAILY_SALES, lateUtc, utc, false, false).contains("2026-11-11"))
        assertTrue(NotificationTimes.slotKey(NotifyKind.DAILY_SALES, lateUtc, india, false, false).contains("2026-11-12"))
    }

    @Test
    fun dueStateUsesRemainingBalanceAndGrace() {
        val today = 20_000L
        assertEquals(DueState.NONE, DueDates.state(null, today, 3, 0))
        assertEquals(DueState.UPCOMING, DueDates.state(today + 10, today, 3, 0))
        assertEquals(DueState.DUE_SOON, DueDates.state(today + 2, today, 3, 0))
        assertEquals(DueState.DUE_SOON, DueDates.state(today, today, 3, 1))
        assertEquals(DueState.OVERDUE, DueDates.state(today - 2, today, 3, 0))
        val paid = DueCandidate("bill", 0L, today - 5)
        val small = DueCandidate("bill", 50L, today - 5)
        val open = DueCandidate("bill", 500L, today - 5)
        val kept = DueDates.reminderCandidates(listOf(paid, small, open), today, 3, 0, 100L, true, RepeatCadence.WEEKLY)
        assertEquals(listOf(open), kept)
    }

    @Test
    fun partialPaymentAndReturnStopTheReminderWhenBelowThreshold() {
        val today = 10L
        val partlyPaid = DueCandidate("bill-1", 80L, today)
        assertTrue(
            DueDates.reminderCandidates(listOf(partlyPaid), today, 3, 0, 100L, true, RepeatCadence.DAILY).isEmpty()
        )
    }

    @Test
    fun hiddenFeaturesAndEmptySalesProduceNothing() {
        val now = at(2026, Calendar.NOVEMBER, 11, 9, 0)
        val rules = rules(NotifyKind.VENDOR_DIGEST, NotifyKind.DAILY_SALES, NotifyKind.EXPIRY, NotifyKind.ORDER_FOLLOWUP)
        val shop = ShopSnapshot(
            yesterdayBills = 0,
            vendorEnabled = false,
            ordersEnabled = false,
            expiryEnabled = false
        )
        val plan = NotificationPlanner.plan(now, zone, 20_000L, rules, shop, emptySet(), true)
        assertTrue(plan.notices.none { it.kind == NotifyKind.VENDOR_DIGEST })
        assertTrue(plan.notices.none { it.kind == NotifyKind.ORDER_FOLLOWUP })
        assertTrue(plan.notices.none { it.kind == NotifyKind.EXPIRY })
        assertTrue(plan.notices.none { it.kind == NotifyKind.DAILY_SALES })
        assertTrue(plan.closedSlots.any { it.startsWith(NotifyKind.DAILY_SALES.name) })
    }

    @Test
    fun overdueWeeklyIsNotIncludedOnAnotherWeekday() {
        val now = at(2026, Calendar.NOVEMBER, 11, 9, 0)
        val today = 20_000L
        val rules = rules(NotifyKind.PAYMENT_DUE).copy(overdueCadence = RepeatCadence.WEEKLY)
        val shop = ShopSnapshot(
            dues = listOf(DueCandidate("old", 500L, today - 10)),
            customerBalancesEnabled = true
        )
        val plan = NotificationPlanner.plan(now, zone, today, rules, shop, emptySet(), true)
        assertTrue(plan.notices.isEmpty())
    }

    @Test
    fun dueSoonAndOverdueShareOneNoticeOnTheOverdueWeekday() {
        val monday = at(2026, Calendar.NOVEMBER, 9, 9, 0)
        assertEquals(Calendar.MONDAY, NotificationTimes.calendar(monday, zone).get(Calendar.DAY_OF_WEEK))
        val today = 20_000L
        val rules = rules(NotifyKind.PAYMENT_DUE).copy(overdueCadence = RepeatCadence.WEEKLY)
        val shop = ShopSnapshot(
            dues = listOf(
                DueCandidate("soon", 400L, today),
                DueCandidate("late", 900L, today - 4)
            )
        )
        val plan = NotificationPlanner.plan(monday, zone, today, rules, shop, emptySet(), true)
        assertEquals(1, plan.notices.size)
        assertTrue(plan.notices.single().body.contains("2"))
    }

    @Test
    fun handledSlotAndStaleRunDoNotSendAgain() {
        val now = at(2026, Calendar.NOVEMBER, 11, 9, 0)
        val rules = rules(NotifyKind.DAILY_SALES)
        val shop = ShopSnapshot(yesterdaySalesPaise = 1000L, yesterdayBills = 2)
        val first = NotificationPlanner.plan(now, zone, 1L, rules, shop, emptySet(), true)
        assertEquals(1, first.notices.size)
        val second = NotificationPlanner.plan(now, zone, 1L, rules, shop, setOf(first.notices.single().slotKey), true)
        assertTrue(second.notices.isEmpty())
        val late = NotificationPlanner.plan(at(2026, Calendar.NOVEMBER, 11, 15, 0), zone, 1L, rules, shop, emptySet(), true)
        assertTrue(late.notices.isEmpty())
        assertTrue(late.closedSlots.isNotEmpty())
    }

    @Test
    fun lowStockFingerprintChangeIsANewSlot() {
        val now = at(2026, Calendar.NOVEMBER, 11, 9, 0)
        val rules = rules(NotifyKind.LOW_STOCK)
        val first = NotificationPlanner.plan(
            now, zone, 1L, rules, ShopSnapshot(lowStockCount = 1, lowStockKey = "4"), emptySet(), true
        )
        val second = NotificationPlanner.plan(
            now, zone, 1L, rules,
            ShopSnapshot(lowStockCount = 2, lowStockKey = "4,9"),
            setOf(first.notices.single().slotKey),
            true
        )
        assertEquals(1, second.notices.size)
        assertTrue(second.notices.single().slotKey.endsWith("4,9"))
    }

    @Test
    fun permissionPolicyBeforeAndAfterApi33() {
        assertTrue(NotificationAccessPolicy.canPost(32, true, false, true))
        assertFalse(NotificationAccessPolicy.canPost(33, true, false, true))
        assertTrue(NotificationAccessPolicy.canPost(33, true, true, true))
        assertFalse(NotificationAccessPolicy.canPost(34, true, true, false))
        assertFalse(NotificationAccessPolicy.canPost(24, false, true, true))
    }

    @Test
    fun disabledMasterSwitchPlansNothing() {
        val now = at(2026, Calendar.NOVEMBER, 11, 9, 0)
        val plan = NotificationPlanner.plan(
            now, zone, 1L, rules(NotifyKind.DAILY_SALES),
            ShopSnapshot(yesterdayBills = 1, yesterdaySalesPaise = 10L),
            emptySet(),
            allowed = false
        )
        assertTrue(plan.notices.isEmpty())
        assertTrue(plan.closedSlots.isEmpty())
    }

    @Test
    fun snoozeUsesASeparateSlot() {
        val now = at(2026, Calendar.NOVEMBER, 11, 9, 0)
        val rules = rules(NotifyKind.DAILY_SALES).copy(snoozeUntil = mapOf(NotifyKind.DAILY_SALES to now - 60_000L))
        val plan = NotificationPlanner.plan(
            now, zone, 1L, rules, ShopSnapshot(yesterdayBills = 1, yesterdaySalesPaise = 10L), emptySet(), true
        )
        assertEquals(1, plan.notices.size)
        assertTrue(plan.notices.single().slotKey.startsWith("snooze:"))
    }

    private fun rules(vararg kinds: NotifyKind): NoticeRules {
        return NoticeRules(
            globalTime = WallTime(9, 0),
            types = NotifyKind.entries.associateWith { kind ->
                TypeSchedule(enabled = kind in kinds, weekday = Calendar.MONDAY)
            }
        )
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, timeZone: TimeZone = zone): Long {
        return Calendar.getInstance(timeZone).apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun calendarAt(hour: Int, minute: Int): Calendar =
        NotificationTimes.calendar(at(2026, Calendar.NOVEMBER, 11, hour, minute), zone)
}
