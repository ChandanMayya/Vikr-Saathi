package com.kex.vikrsaathi.ui.settings

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.kex.vikrsaathi.R

class InvoiceConfigurationFragment : SettingsNavHubFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindNavHub(
            entries = listOf(
                SettingsNavEntry(
                    iconRes = R.drawable.ic_image_settings,
                    title = getString(R.string.settings_image_configuration),
                    subtitle = getString(R.string.settings_image_configuration_subtitle)
                ) {
                    findNavController().navigate(R.id.action_invoice_configuration_to_image)
                },
                SettingsNavEntry(
                    iconRes = R.drawable.ic_invoice_counter,
                    title = getString(R.string.settings_invoice_counter_configuration),
                    subtitle = getString(R.string.settings_invoice_counter_configuration_subtitle)
                ) {
                    findNavController().navigate(R.id.action_invoice_configuration_to_counter)
                },
                SettingsNavEntry(
                    iconRes = R.drawable.ic_invoice_layout,
                    title = getString(R.string.settings_invoice_layout_configuration),
                    subtitle = getString(R.string.settings_invoice_layout_configuration_subtitle)
                ) {
                    findNavController().navigate(R.id.action_invoice_configuration_to_layout)
                }
            ),
            subtitle = getString(R.string.settings_invoice_hub_subtitle)
        )
    }
}
