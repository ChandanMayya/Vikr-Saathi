package com.kex.vikrsaathi.data.repository

import android.content.Context
import com.kex.vikrsaathi.domain.notify.NoticeRules
import com.kex.vikrsaathi.domain.notify.NotifyKind
import com.kex.vikrsaathi.domain.notify.QuietHours
import com.kex.vikrsaathi.domain.notify.RepeatCadence
import com.kex.vikrsaathi.domain.notify.TypeSchedule
import com.kex.vikrsaathi.domain.notify.WallTime
import java.util.Calendar
import org.json.JSONObject

class NotificationPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var lockScreenDetails: Boolean
        get() = prefs.getBoolean(KEY_LOCK_DETAILS, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_DETAILS, value).apply()

    var permissionPrompted: Boolean
        get() = prefs.getBoolean(KEY_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROMPTED, value).apply()

    var paused: Boolean
        get() = prefs.getBoolean(KEY_PAUSED, false)
        set(value) = prefs.edit().putBoolean(KEY_PAUSED, value).apply()

    var defaultTime: WallTime
        get() = WallTime(prefs.getInt(KEY_HOUR, 9), prefs.getInt(KEY_MINUTE, 0))
        set(value) = prefs.edit().putInt(KEY_HOUR, value.hour).putInt(KEY_MINUTE, value.minute).apply()

    var quietEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET, value).apply()

    var quietStart: WallTime
        get() = WallTime(prefs.getInt(KEY_QUIET_START_H, 22), prefs.getInt(KEY_QUIET_START_M, 0))
        set(value) = prefs.edit().putInt(KEY_QUIET_START_H, value.hour).putInt(KEY_QUIET_START_M, value.minute).apply()

    var quietEnd: WallTime
        get() = WallTime(prefs.getInt(KEY_QUIET_END_H, 7), prefs.getInt(KEY_QUIET_END_M, 0))
        set(value) = prefs.edit().putInt(KEY_QUIET_END_H, value.hour).putInt(KEY_QUIET_END_M, value.minute).apply()

    var leadDays: Int
        get() = prefs.getInt(KEY_LEAD, 3)
        set(value) = prefs.edit().putInt(KEY_LEAD, value).apply()

    var graceDays: Int
        get() = prefs.getInt(KEY_GRACE, 0)
        set(value) = prefs.edit().putInt(KEY_GRACE, value.coerceAtLeast(0)).apply()

    var minOutstandingPaise: Long
        get() = prefs.getLong(KEY_MIN, 100L)
        set(value) = prefs.edit().putLong(KEY_MIN, value.coerceAtLeast(0L)).apply()

    var notifyOnDueDate: Boolean
        get() = prefs.getBoolean(KEY_ON_DUE, true)
        set(value) = prefs.edit().putBoolean(KEY_ON_DUE, value).apply()

    var overdueCadence: RepeatCadence
        get() = RepeatCadence.entries.firstOrNull { it.name == prefs.getString(KEY_OVERDUE, RepeatCadence.WEEKLY.name) }
            ?: RepeatCadence.WEEKLY
        set(value) = prefs.edit().putString(KEY_OVERDUE, value.name).apply()

    var lowStockWeekly: Boolean
        get() = prefs.getBoolean(KEY_LOW_WEEKLY, false)
        set(value) = prefs.edit().putBoolean(KEY_LOW_WEEKLY, value).apply()

    var skipEmptySales: Boolean
        get() = prefs.getBoolean(KEY_SKIP_EMPTY, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_EMPTY, value).apply()

    fun schedule(kind: NotifyKind): TypeSchedule {
        val enabled = prefs.getBoolean(enabledKey(kind), false)
        val custom = if (prefs.contains(hourKey(kind))) {
            WallTime(prefs.getInt(hourKey(kind), 9), prefs.getInt(minuteKey(kind), 0))
        } else {
            null
        }
        return TypeSchedule(
            enabled = enabled,
            customTime = custom,
            weekday = prefs.getInt(weekKey(kind), Calendar.MONDAY),
            dayOfMonth = prefs.getInt(monthKey(kind), 1).coerceIn(1, 28)
        )
    }

    fun setEnabled(kind: NotifyKind, enabled: Boolean) {
        prefs.edit().putBoolean(enabledKey(kind), enabled).apply()
    }

    fun setCustomTime(kind: NotifyKind, time: WallTime?) {
        val edit = prefs.edit()
        if (time == null) {
            edit.remove(hourKey(kind)).remove(minuteKey(kind))
        } else {
            edit.putInt(hourKey(kind), time.hour).putInt(minuteKey(kind), time.minute)
        }
        edit.apply()
    }

    fun setWeekday(kind: NotifyKind, weekday: Int) {
        prefs.edit().putInt(weekKey(kind), weekday).apply()
    }

    fun setDayOfMonth(kind: NotifyKind, day: Int) {
        prefs.edit().putInt(monthKey(kind), day.coerceIn(1, 28)).apply()
    }

    fun snoozeUntil(kind: NotifyKind): Long = prefs.getLong(snoozeKey(kind), 0L)

    fun snooze(kind: NotifyKind, untilMillis: Long) {
        prefs.edit().putLong(snoozeKey(kind), untilMillis).apply()
    }

    fun rules(): NoticeRules = NoticeRules(
        globalTime = defaultTime,
        quiet = if (quietEnabled) QuietHours(quietStart, quietEnd) else null,
        types = NotifyKind.entries.associateWith { schedule(it) },
        leadDays = leadDays,
        graceDays = graceDays,
        minOutstandingPaise = minOutstandingPaise,
        notifyOnDueDate = notifyOnDueDate,
        overdueCadence = overdueCadence,
        lowStockWeekly = lowStockWeekly,
        skipEmptySales = skipEmptySales,
        snoozeUntil = NotifyKind.entries.associateWith { snoozeUntil(it) }
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("lockScreenDetails", lockScreenDetails)
        put("defaultHour", defaultTime.hour)
        put("defaultMinute", defaultTime.minute)
        put("quietEnabled", quietEnabled)
        put("quietStartHour", quietStart.hour)
        put("quietStartMinute", quietStart.minute)
        put("quietEndHour", quietEnd.hour)
        put("quietEndMinute", quietEnd.minute)
        put("leadDays", leadDays)
        put("graceDays", graceDays)
        put("minOutstandingPaise", minOutstandingPaise)
        put("notifyOnDueDate", notifyOnDueDate)
        put("overdueCadence", overdueCadence.name)
        put("lowStockWeekly", lowStockWeekly)
        put("skipEmptySales", skipEmptySales)
        val types = JSONObject()
        NotifyKind.entries.forEach { kind ->
            val schedule = schedule(kind)
            types.put(kind.name, JSONObject().apply {
                put("enabled", schedule.enabled)
                schedule.customTime?.let {
                    put("hour", it.hour)
                    put("minute", it.minute)
                }
                put("weekday", schedule.weekday)
                put("dayOfMonth", schedule.dayOfMonth)
            })
        }
        put("types", types)
    }

    fun applyJson(obj: JSONObject) {
        lockScreenDetails = obj.optBoolean("lockScreenDetails", false)
        defaultTime = WallTime(obj.optInt("defaultHour", 9), obj.optInt("defaultMinute", 0))
        quietEnabled = obj.optBoolean("quietEnabled", false)
        quietStart = WallTime(obj.optInt("quietStartHour", 22), obj.optInt("quietStartMinute", 0))
        quietEnd = WallTime(obj.optInt("quietEndHour", 7), obj.optInt("quietEndMinute", 0))
        leadDays = obj.optInt("leadDays", 3)
        graceDays = obj.optInt("graceDays", 0)
        minOutstandingPaise = obj.optLong("minOutstandingPaise", 100L)
        notifyOnDueDate = obj.optBoolean("notifyOnDueDate", true)
        overdueCadence = RepeatCadence.entries.firstOrNull { it.name == obj.optString("overdueCadence") }
            ?: RepeatCadence.WEEKLY
        lowStockWeekly = obj.optBoolean("lowStockWeekly", false)
        skipEmptySales = obj.optBoolean("skipEmptySales", true)
        val types = obj.optJSONObject("types")
        NotifyKind.entries.forEach { kind ->
            val row = types?.optJSONObject(kind.name)
            setEnabled(kind, row?.optBoolean("enabled", false) == true)
            if (row != null && row.has("hour")) {
                setCustomTime(kind, WallTime(row.optInt("hour", 9), row.optInt("minute", 0)))
            } else {
                setCustomTime(kind, null)
            }
            setWeekday(kind, row?.optInt("weekday", Calendar.MONDAY) ?: Calendar.MONDAY)
            setDayOfMonth(kind, row?.optInt("dayOfMonth", 1) ?: 1)
        }
        enabled = false
        permissionPrompted = false
    }

    fun reset() {
        val edit = prefs.edit()
        listOf(
            KEY_ENABLED, KEY_LOCK_DETAILS, KEY_PROMPTED, KEY_PAUSED, KEY_HOUR, KEY_MINUTE, KEY_QUIET,
            KEY_QUIET_START_H, KEY_QUIET_START_M, KEY_QUIET_END_H, KEY_QUIET_END_M, KEY_LEAD, KEY_GRACE,
            KEY_MIN, KEY_ON_DUE, KEY_OVERDUE, KEY_LOW_WEEKLY, KEY_SKIP_EMPTY
        ).forEach { edit.remove(it) }
        NotifyKind.entries.forEach { kind ->
            edit.remove(enabledKey(kind)).remove(hourKey(kind)).remove(minuteKey(kind))
            edit.remove(weekKey(kind)).remove(monthKey(kind)).remove(snoozeKey(kind))
        }
        edit.apply()
    }

    private fun enabledKey(kind: NotifyKind) = "notify_${kind.name}_on"
    private fun hourKey(kind: NotifyKind) = "notify_${kind.name}_hour"
    private fun minuteKey(kind: NotifyKind) = "notify_${kind.name}_minute"
    private fun weekKey(kind: NotifyKind) = "notify_${kind.name}_weekday"
    private fun monthKey(kind: NotifyKind) = "notify_${kind.name}_dom"
    private fun snoozeKey(kind: NotifyKind) = "notify_${kind.name}_snooze"

    companion object {
        const val PREFS = "vikr_saathi_settings"
        private const val KEY_ENABLED = "notify_enabled"
        private const val KEY_LOCK_DETAILS = "notify_lock_details"
        private const val KEY_PROMPTED = "notify_prompted"
        private const val KEY_PAUSED = "notify_paused"
        private const val KEY_HOUR = "notify_default_hour"
        private const val KEY_MINUTE = "notify_default_minute"
        private const val KEY_QUIET = "notify_quiet"
        private const val KEY_QUIET_START_H = "notify_quiet_start_h"
        private const val KEY_QUIET_START_M = "notify_quiet_start_m"
        private const val KEY_QUIET_END_H = "notify_quiet_end_h"
        private const val KEY_QUIET_END_M = "notify_quiet_end_m"
        private const val KEY_LEAD = "notify_lead_days"
        private const val KEY_GRACE = "notify_grace_days"
        private const val KEY_MIN = "notify_min_paise"
        private const val KEY_ON_DUE = "notify_on_due"
        private const val KEY_OVERDUE = "notify_overdue_cadence"
        private const val KEY_LOW_WEEKLY = "notify_low_weekly"
        private const val KEY_SKIP_EMPTY = "notify_skip_empty_sales"
    }
}
