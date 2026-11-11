package com.kex.vikrsaathi.ui.settings

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.MainActivity
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.repository.NotificationPreferences
import com.kex.vikrsaathi.notify.NotificationScheduler
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu

class SettingsFragment : SettingsNavHubFragment() {

    private val appLockManager
        get() = (requireActivity().application as VikrSaathiApp).appLockManager

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        NotificationPreferences(requireContext()).permissionPrompted = true
        NotificationScheduler.reconcile(requireContext())
        bindHub()
    }

    override fun onResume() {
        super.onResume()
        if (view != null) bindHub()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.SETTINGS)
        binding.cardNotifications.isVisible = true
        binding.switchAllowNotifications.setOnCheckedChangeListener { _, checked ->
            onAllowNotifications(checked)
        }
        bindHub()
    }

    private fun bindHub() {
        val notes = NotificationPreferences(requireContext())
        binding.switchAllowNotifications.setOnCheckedChangeListener(null)
        binding.switchAllowNotifications.isChecked = notes.enabled
        binding.switchAllowNotifications.setOnCheckedChangeListener { _, checked ->
            onAllowNotifications(checked)
        }
        val entries = buildList {
            add(
                SettingsNavEntry(
                    iconRes = R.drawable.ic_settings_general,
                    title = getString(R.string.settings_general_title),
                    subtitle = getString(R.string.settings_general_subtitle)
                ) { findNavController().navigate(R.id.action_settings_to_general) }
            )
            if (notes.enabled) {
                add(
                    SettingsNavEntry(
                        iconRes = R.drawable.ic_settings_general,
                        title = getString(R.string.notify_customizations),
                        subtitle = getString(R.string.notify_customizations_hint)
                    ) { findNavController().navigate(R.id.action_settings_to_notifications) }
                )
            }
            add(
                SettingsNavEntry(
                    iconRes = R.drawable.ic_settings_security,
                    title = getString(R.string.settings_security_title),
                    subtitle = getString(R.string.settings_security_subtitle)
                ) { findNavController().navigate(R.id.action_settings_to_security) }
            )
            add(
                SettingsNavEntry(
                    iconRes = R.drawable.ic_settings_inventory,
                    title = getString(R.string.settings_inventory_title),
                    subtitle = getString(R.string.settings_inventory_subtitle)
                ) { findNavController().navigate(R.id.action_settings_to_inventory) }
            )
            add(
                SettingsNavEntry(
                    iconRes = R.drawable.ic_settings_configurations,
                    title = getString(R.string.settings_accounts_title),
                    subtitle = getString(R.string.settings_accounts_subtitle)
                ) { findNavController().navigate(R.id.action_settings_to_accounts) }
            )
            add(
                SettingsNavEntry(
                    iconRes = R.drawable.ic_invoice_config,
                    title = getString(R.string.invoice_configuration),
                    subtitle = getString(R.string.settings_invoice_config_subtitle)
                ) { findNavController().navigate(R.id.action_settings_to_invoice_configuration) }
            )
            add(
                SettingsNavEntry(
                    iconRes = R.drawable.ic_backup_restore,
                    title = getString(R.string.backup_restore),
                    subtitle = getString(R.string.backup_restore_hint)
                ) { findNavController().navigate(R.id.action_settings_to_backup) }
            )
            add(
                SettingsNavEntry(
                    iconRes = R.drawable.ic_reset_data,
                    title = getString(R.string.reset_data),
                    subtitle = getString(R.string.reset_data_hint)
                ) { findNavController().navigate(R.id.action_settings_to_reset) }
            )
        }
        bindNavHub(
            entries = entries,
            subtitle = getString(R.string.settings_hub_subtitle),
            footer = getString(R.string.settings_app_version, appVersionName()),
            showLogout = appLockManager.isLockEnabled,
            onLogout = ::confirmLogout
        )
    }

    private fun onAllowNotifications(checked: Boolean) {
        val notes = NotificationPreferences(requireContext())
        if (!checked) {
            notes.enabled = false
            NotificationScheduler.reconcile(requireContext())
            bindHub()
            return
        }
        if (!notes.permissionPrompted) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.notify_opt_in_title)
                .setMessage(R.string.notify_opt_in_body)
                .setPositiveButton(R.string.notify_continue) { _, _ ->
                    notes.enabled = true
                    notes.permissionPrompted = true
                    NotificationScheduler.reconcile(requireContext())
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        bindHub()
                    }
                }
                .setNegativeButton(R.string.notify_not_now) { _, _ ->
                    binding.switchAllowNotifications.isChecked = false
                }
                .setOnCancelListener {
                    binding.switchAllowNotifications.isChecked = notes.enabled
                }
                .show()
            return
        }
        notes.enabled = true
        NotificationScheduler.reconcile(requireContext())
        bindHub()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_logout_confirm_title)
            .setMessage(R.string.settings_logout_confirm_message)
            .setPositiveButton(R.string.settings_logout) { _, _ ->
                val activity = requireActivity()
                if (activity is MainActivity) {
                    activity.navigateToDashboard()
                    activity.requestAppLock()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun appVersionName(): String = runCatching {
        requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
            .versionName
    }.getOrNull().orEmpty()
}
