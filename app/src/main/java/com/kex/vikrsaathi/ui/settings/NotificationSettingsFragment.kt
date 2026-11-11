package com.kex.vikrsaathi.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.repository.NotificationPreferences
import com.kex.vikrsaathi.databinding.FragmentNotificationSettingsBinding
import com.kex.vikrsaathi.domain.notify.NotificationAccessPolicy
import com.kex.vikrsaathi.domain.notify.NotifyKind
import com.kex.vikrsaathi.domain.notify.RepeatCadence
import com.kex.vikrsaathi.domain.notify.WallTime
import com.kex.vikrsaathi.notify.NotificationChannels
import com.kex.vikrsaathi.notify.NotificationPoster
import com.kex.vikrsaathi.notify.NotificationScheduler
import java.util.Calendar

class NotificationSettingsFragment : Fragment() {

    private var _binding: FragmentNotificationSettingsBinding? = null
    private val binding get() = _binding!!

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { render() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun prefs() = NotificationPreferences(requireContext())

    private fun render() {
        val root = binding.notificationContent
        root.removeAllViews()
        val prefs = prefs()
        val shop = (requireActivity().application as VikrSaathiApp).settingsRepository
        val blocked = !NotificationChannels.notificationsEnabled(requireContext()) ||
            (Build.VERSION.SDK_INT >= 33 && !hasPostPermission())
        if (blocked) {
            root.addView(card(getString(R.string.notify_blocked_title), getString(R.string.notify_blocked_body)) {
                addButton(getString(R.string.notify_open_settings)) { openSystemSettings() }
            })
        }
        root.addView(card(getString(R.string.notify_default_time), getString(R.string.notify_around, prefs.defaultTime.label())) {
            addButton(prefs.defaultTime.label()) { pickTime(prefs.defaultTime) { prefs.defaultTime = it; render() } }
            addSwitch(getString(R.string.notify_quiet), prefs.quietEnabled) {
                prefs.quietEnabled = it
                render()
            }
            if (prefs.quietEnabled) {
                addBody(getString(R.string.notify_quiet_hint))
                addButton(getString(R.string.notify_quiet_start) + " " + prefs.quietStart.label()) {
                    pickTime(prefs.quietStart) { prefs.quietStart = it; render() }
                }
                addButton(getString(R.string.notify_quiet_end) + " " + prefs.quietEnd.label()) {
                    pickTime(prefs.quietEnd) { prefs.quietEnd = it; render() }
                }
            }
            addSwitch(getString(R.string.notify_lock_details), prefs.lockScreenDetails) {
                prefs.lockScreenDetails = it
            }
            addBody(getString(R.string.notify_lock_details_hint))
            addButton(getString(R.string.notify_test)) { sendTest() }
        })
        section(root, R.string.notify_section_customers)
        typeCard(root, prefs, NotifyKind.PAYMENT_DUE, R.string.notify_due_title, R.string.notify_due_hint, R.string.notify_due_preview, weekly = false) {
            addBody(getString(R.string.notify_lead))
            rowButtons(listOf(1 to "1", 3 to "3", 7 to "7")) { days ->
                prefs.leadDays = days
                render()
            }
            addSwitch(getString(R.string.notify_on_due), prefs.notifyOnDueDate) { prefs.notifyOnDueDate = it }
            addButton(getString(R.string.notify_overdue_repeat) + ": " + cadenceLabel(prefs.overdueCadence)) {
                pickCadence(prefs.overdueCadence) { prefs.overdueCadence = it; render() }
            }
            addButton(getString(R.string.notify_grace) + ": " + prefs.graceDays) {
                prefs.graceDays = if (prefs.graceDays >= 7) 0 else prefs.graceDays + 1
                render()
            }
        }
        typeCard(root, prefs, NotifyKind.CUSTOMER_DIGEST, R.string.notify_customer_digest, R.string.notify_customer_digest_hint, R.string.notify_customer_digest_preview, weekly = true)
        if (shop.vendorManagementEnabled) {
            section(root, R.string.notify_section_vendors)
            typeCard(root, prefs, NotifyKind.VENDOR_DIGEST, R.string.notify_vendor_digest, R.string.notify_vendor_digest_hint, R.string.notify_vendor_digest_preview, weekly = true)
        }
        section(root, R.string.notify_section_sales)
        typeCard(root, prefs, NotifyKind.DAILY_SALES, R.string.notify_daily_sales, R.string.notify_daily_sales_hint, R.string.notify_daily_sales_preview, weekly = false)
        typeCard(root, prefs, NotifyKind.WEEKLY_SALES, R.string.notify_weekly_sales, R.string.notify_weekly_sales_hint, R.string.notify_weekly_sales_preview, weekly = true)
        section(root, R.string.notify_section_inventory)
        typeCard(root, prefs, NotifyKind.LOW_STOCK, R.string.notify_low_stock, R.string.notify_low_stock_hint, R.string.notify_low_stock_preview, weekly = prefs.lowStockWeekly) {
            addSwitch(getString(R.string.notify_low_stock_weekly), prefs.lowStockWeekly) {
                prefs.lowStockWeekly = it
                render()
            }
        }
        if (shop.orderManagementEnabled) {
            typeCard(root, prefs, NotifyKind.ORDER_FOLLOWUP, R.string.notify_orders, R.string.notify_orders_hint, R.string.notify_orders_preview, weekly = false)
        }
        if (shop.batchTrackingEnabled) {
            typeCard(root, prefs, NotifyKind.EXPIRY, R.string.notify_expiry, R.string.notify_expiry_hint, R.string.notify_expiry_preview, weekly = false)
        }
        section(root, R.string.notify_section_backup)
        typeCard(root, prefs, NotifyKind.BACKUP, R.string.notify_backup, R.string.notify_backup_hint, R.string.notify_backup_preview, weekly = false, monthly = true)
    }

    private fun section(root: LinearLayout, title: Int) {
        root.addView(TextView(requireContext()).apply {
            setText(title)
            setPadding(0, dp(16), 0, dp(4))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        })
    }

    private fun typeCard(
        root: LinearLayout,
        prefs: NotificationPreferences,
        kind: NotifyKind,
        title: Int,
        hint: Int,
        preview: Int,
        weekly: Boolean,
        monthly: Boolean = false,
        extra: (LinearLayout.() -> Unit)? = null
    ) {
        val schedule = prefs.schedule(kind)
        val time = schedule.effectiveTime(prefs.defaultTime)
        root.addView(card(getString(title), getString(hint)) {
            addSwitch(getString(title), schedule.enabled) {
                prefs.setEnabled(kind, it)
                NotificationScheduler.reconcile(requireContext())
                render()
            }
            if (!schedule.enabled) return@card
            val channel = NotificationChannels.channelFor(kind)
            if (!NotificationChannels.channelEnabled(requireContext(), channel)) {
                addBody(getString(R.string.notify_channel_blocked))
            }
            val timeLabel = if (schedule.customTime == null) {
                getString(R.string.notify_using_default, time.label())
            } else {
                getString(R.string.notify_custom_time, time.label())
            }
            addBody(timeLabel)
            addBody(getString(R.string.notify_around, time.label()))
            if (schedule.customTime == null) {
                addButton(getString(R.string.notify_use_different)) {
                    pickTime(time) { prefs.setCustomTime(kind, it); render() }
                }
            } else {
                addButton(getString(R.string.notify_custom_time, schedule.customTime.label())) {
                    pickTime(schedule.customTime) { prefs.setCustomTime(kind, it); render() }
                }
                addButton(getString(R.string.notify_use_default)) {
                    prefs.setCustomTime(kind, null)
                    render()
                }
            }
            if (weekly) {
                addButton(getString(R.string.notify_weekday) + ": " + weekdayLabel(schedule.weekday)) {
                    pickWeekday(schedule.weekday) { prefs.setWeekday(kind, it); render() }
                }
            }
            if (monthly) {
                addButton(getString(R.string.notify_day_of_month) + ": " + schedule.dayOfMonth) {
                    prefs.setDayOfMonth(kind, if (schedule.dayOfMonth >= 28) 1 else schedule.dayOfMonth + 1)
                    render()
                }
            }
            extra?.invoke(this)
            addBody(getString(R.string.notify_preview) + ": " + getString(preview))
        })
    }

    private fun card(title: String, body: String, fill: LinearLayout.() -> Unit): MaterialCardView {
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            })
            addBody(body)
            fill()
        }
        return MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            addView(content)
            content.setPadding(dp(16))
        }
    }

    private fun LinearLayout.addBody(text: String) {
        addView(TextView(context).apply {
            this.text = text
            setPadding(0, dp(6), 0, 0)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        })
    }

    private fun LinearLayout.addButton(label: String, onClick: () -> Unit) {
        addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnClickListener { onClick() }
        })
    }

    private fun LinearLayout.addSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        addView(MaterialSwitch(context).apply {
            text = label
            isChecked = checked
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnCheckedChangeListener { _, value -> onChange(value) }
        })
    }

    private fun LinearLayout.rowButtons(options: List<Pair<Int, String>>, onPick: (Int) -> Unit) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEach { (value, label) ->
            row.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { onPick(value) }
            })
        }
        addView(row)
    }

    private fun pickTime(current: WallTime, onPick: (WallTime) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText(R.string.notify_default_time)
            .build()
        picker.addOnPositiveButtonClickListener { onPick(WallTime(picker.hour, picker.minute)) }
        picker.show(parentFragmentManager, "notify-time")
    }

    private fun pickWeekday(current: Int, onPick: (Int) -> Unit) {
        val days = listOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )
        val labels = days.map { weekdayLabel(it) }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notify_weekday)
            .setSingleChoiceItems(labels, days.indexOf(current)) { dialog, which ->
                onPick(days[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun pickCadence(current: RepeatCadence, onPick: (RepeatCadence) -> Unit) {
        val values = listOf(RepeatCadence.OFF, RepeatCadence.WEEKLY, RepeatCadence.DAILY)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notify_overdue_repeat)
            .setSingleChoiceItems(
                values.map { cadenceLabel(it) }.toTypedArray(),
                values.indexOf(current)
            ) { dialog, which ->
                onPick(values[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun sendTest() {
        val channel = NotificationChannels.SUMMARIES
        NotificationChannels.ensure(requireContext())
        val allowed = NotificationAccessPolicy.canPost(
            sdk = Build.VERSION.SDK_INT,
            appEnabled = prefs().enabled,
            permissionGranted = hasPostPermission(),
            channelEnabled = NotificationChannels.channelEnabled(requireContext(), channel)
        )
        if (!allowed) {
            Toast.makeText(requireContext(), R.string.notify_test_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        NotificationPoster.show(
            context = requireContext(),
            id = 910_001,
            channelId = channel,
            title = getString(R.string.notify_test_title),
            body = getString(R.string.notify_test_body),
            destination = "backup",
            kind = null,
            revealOnLockScreen = false
        )
    }

    private fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
            Settings.EXTRA_APP_PACKAGE,
            requireContext().packageName
        )
        startActivity(intent)
    }

    private fun hasPostPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun weekdayLabel(day: Int): String = when (day) {
        Calendar.SUNDAY -> getString(R.string.weekday_sun)
        Calendar.TUESDAY -> getString(R.string.weekday_tue)
        Calendar.WEDNESDAY -> getString(R.string.weekday_wed)
        Calendar.THURSDAY -> getString(R.string.weekday_thu)
        Calendar.FRIDAY -> getString(R.string.weekday_fri)
        Calendar.SATURDAY -> getString(R.string.weekday_sat)
        else -> getString(R.string.weekday_mon)
    }

    private fun cadenceLabel(cadence: RepeatCadence): String = when (cadence) {
        RepeatCadence.OFF -> getString(R.string.notify_repeat_off)
        RepeatCadence.DAILY -> getString(R.string.notify_repeat_daily)
        else -> getString(R.string.notify_repeat_weekly)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
