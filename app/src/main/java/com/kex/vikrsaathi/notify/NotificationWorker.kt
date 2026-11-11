package com.kex.vikrsaathi.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.backup.ManualBackupStore
import com.kex.vikrsaathi.data.entity.NotificationMark
import com.kex.vikrsaathi.data.repository.NotificationPreferences
import com.kex.vikrsaathi.domain.inventory.CivilDate
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.domain.notify.DueCandidate
import com.kex.vikrsaathi.domain.notify.NotificationAccessPolicy
import com.kex.vikrsaathi.domain.notify.NotificationPlanner
import com.kex.vikrsaathi.domain.notify.ShopSnapshot
import java.util.Calendar
import java.util.TimeZone

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = NotificationPreferences(applicationContext)
        if (!prefs.enabled || prefs.paused) return Result.success()
        if (!NotificationChannels.notificationsEnabled(applicationContext)) return Result.success()
        val app = applicationContext as VikrSaathiApp
        val zone = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val today = CivilDate.today(now, zone)
        val shop = snapshot(app, today, now)
        val handled = app.database.notificationDao().handledSlots().toSet()
        val plan = NotificationPlanner.plan(
            nowMillis = now,
            zone = zone,
            today = today,
            rules = prefs.rules(),
            shop = shop,
            handledSlots = handled,
            allowed = true
        )
        val reveal = prefs.lockScreenDetails && !app.appLockManager.isLockEnabled
        plan.closedSlots.forEach { slot ->
            app.database.notificationDao().insert(
                NotificationMark(slotKey = slot, outcome = "SKIPPED", createdAt = now)
            )
        }
        plan.notices.forEach { notice ->
            val channel = NotificationChannels.channelFor(notice.kind)
            val allowed = NotificationAccessPolicy.canPost(
                sdk = android.os.Build.VERSION.SDK_INT,
                appEnabled = prefs.enabled,
                permissionGranted = hasPermission(),
                channelEnabled = NotificationChannels.channelEnabled(applicationContext, channel)
            )
            if (!allowed) return@forEach
            NotificationPoster.show(
                context = applicationContext,
                id = notice.slotKey.hashCode(),
                channelId = channel,
                title = notice.title,
                body = notice.body,
                destination = notice.destination,
                kind = notice.kind,
                revealOnLockScreen = reveal
            )
            app.database.notificationDao().insert(
                NotificationMark(
                    slotKey = notice.slotKey,
                    outcome = "SENT",
                    createdAt = now
                )
            )
        }
        return Result.success()
    }

    private fun hasPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return true
        return applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private suspend fun snapshot(app: VikrSaathiApp, today: Long, now: Long): ShopSnapshot {
        val finance = app.customerFinanceRepository
        val settings = app.settingsRepository
        val customers = finance.outstandingCustomers(now)
        val dues = customers.flatMap { customer ->
            finance.dueSlicesFor(customer.customerId, today).map { slice ->
                DueCandidate(
                    key = slice.billId?.let { "bill$it" } ?: "open${slice.entryId}",
                    outstandingPaise = slice.outstandingPaise,
                    dueEpochDay = slice.dueEpochDay
                )
            }
        }
        val zone = TimeZone.getDefault()
        val yesterday = dayRange(now, zone, -1)
        val week = dayRange(now, zone, -7, 0)
        val previous = dayRange(now, zone, -14, -7)
        val bills = app.database.billDao().getAllBillsWithDetailsSync().map { it.bill }
        fun totals(range: LongRange) = bills.filter { it.date in range }.let { rows ->
            rows.sumOf { Money.rupeesToPaise(it.total) } to rows.size
        }
        val (yesterdayPaise, yesterdayCount) = totals(yesterday)
        val (weekPaise, weekCount) = totals(week)
        val (prevPaise, prevCount) = totals(previous)
        val vendor = app.vendorRepository.vendorDueSnapshot(today)
        val alerts = if (settings.batchTrackingEnabled) app.batchRepository.alerts() else emptyList()
        return ShopSnapshot(
            dues = dues,
            receivablePaise = customers.filter { it.balancePaise > 0L }.sumOf { it.balancePaise },
            overduePaise = customers.sumOf { it.overduePaise },
            payablePaise = app.vendorRepository.totalPayablePaise(),
            vendorOverduePaise = vendor.first,
            vendorDueDatesRecorded = vendor.second,
            yesterdaySalesPaise = yesterdayPaise,
            yesterdayBills = yesterdayCount,
            weekSalesPaise = weekPaise,
            weekBills = weekCount,
            previousWeekSalesPaise = prevPaise,
            previousWeekBills = prevCount,
            lowStockCount = app.inventoryRepository.getLowStockItems(settings.lowStockThreshold).size,
            lowStockKey = app.inventoryRepository.getLowStockItems(settings.lowStockThreshold)
                .map { it.id }.sorted().joinToString(","),
            ordersNeedingAttention = app.vendorRepository.purchasingAttentionCount(),
            ordersPastDelivery = app.vendorRepository.ordersPastExpectedDelivery(now),
            expiredBatches = alerts.count { it.expired },
            expiringBatches = alerts.count { !it.expired },
            lastManualBackupAt = ManualBackupStore(applicationContext).load()?.exportedAt,
            customerBalancesEnabled = settings.customerBalanceEnabled,
            vendorEnabled = settings.vendorManagementEnabled,
            ordersEnabled = settings.orderManagementEnabled,
            expiryEnabled = settings.batchTrackingEnabled
        )
    }

    private fun dayRange(now: Long, zone: TimeZone, startOffsetDays: Int, endOffsetDays: Int = startOffsetDays + 1): LongRange {
        val start = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, startOffsetDays)
        }.timeInMillis
        val end = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, endOffsetDays)
        }.timeInMillis
        return start until end
    }
}
