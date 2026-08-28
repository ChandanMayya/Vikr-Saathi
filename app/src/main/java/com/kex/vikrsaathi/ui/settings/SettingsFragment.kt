package com.kex.vikrsaathi.ui.settings

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu

class SettingsFragment : SettingsNavHubFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.SETTINGS)
        bindNavHub(
            entries = listOf(
                SettingsNavEntry(
                    iconRes = R.drawable.ic_settings_general,
                    title = getString(R.string.settings_general_title),
                    subtitle = getString(R.string.settings_general_subtitle)
                ) {
                    findNavController().navigate(R.id.action_settings_to_general)
                },
                SettingsNavEntry(
                    iconRes = R.drawable.ic_settings_security,
                    title = getString(R.string.settings_security_title),
                    subtitle = getString(R.string.settings_security_subtitle)
                ) {
                    findNavController().navigate(R.id.action_settings_to_security)
                },
                SettingsNavEntry(
                    iconRes = R.drawable.ic_settings_inventory,
                    title = getString(R.string.settings_inventory_title),
                    subtitle = getString(R.string.settings_inventory_subtitle)
                ) {
                    findNavController().navigate(R.id.action_settings_to_inventory)
                },
                SettingsNavEntry(
                    iconRes = R.drawable.ic_invoice_config,
                    title = getString(R.string.invoice_configuration),
                    subtitle = getString(R.string.settings_invoice_config_subtitle)
                ) {
                    findNavController().navigate(R.id.action_settings_to_invoice_configuration)
                },
                SettingsNavEntry(
                    iconRes = R.drawable.ic_backup_restore,
                    title = getString(R.string.backup_restore),
                    subtitle = getString(R.string.backup_restore_hint)
                ) {
                    findNavController().navigate(R.id.action_settings_to_backup)
                },
                SettingsNavEntry(
                    iconRes = R.drawable.ic_reset_data,
                    title = getString(R.string.reset_data),
                    subtitle = getString(R.string.reset_data_hint)
                ) {
                    findNavController().navigate(R.id.action_settings_to_reset)
                }
            ),
            subtitle = getString(R.string.settings_hub_subtitle),
            footer = getString(R.string.settings_app_version, appVersionName())
        )
    }

    private fun appVersionName(): String = runCatching {
        requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
            .versionName
    }.getOrNull().orEmpty()
}
