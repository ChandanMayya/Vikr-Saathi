package com.kex.vikrsaathi.ui.finance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.repository.NotificationPreferences
import com.kex.vikrsaathi.databinding.FragmentAccountsSettingsBinding
import com.kex.vikrsaathi.notify.NotificationScheduler
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu

class AccountsSettingsFragment : Fragment() {

    private var _binding: FragmentAccountsSettingsBinding? = null
    private val binding get() = _binding!!

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val notes = NotificationPreferences(requireContext())
        notes.permissionPrompted = true
        notes.enabled = granted
        NotificationScheduler.reconcile(requireContext())
        bindAllowNotifications()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccountsSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.ACCOUNTS)
        val settings = (requireActivity().application as VikrSaathiApp).settingsRepository
        binding.switchCustomerBalances.isChecked = settings.customerBalanceEnabled
        binding.switchVendorManagement.isChecked = settings.vendorManagementEnabled
        binding.switchOrderManagement.isChecked = settings.orderManagementEnabled
        binding.switchCustomerBalances.setOnCheckedChangeListener { _, checked ->
            settings.customerBalanceEnabled = checked
        }
        binding.switchVendorManagement.setOnCheckedChangeListener { _, checked ->
            settings.vendorManagementEnabled = checked
        }
        binding.switchOrderManagement.setOnCheckedChangeListener { _, checked ->
            settings.orderManagementEnabled = checked
        }
        binding.switchWarranty.isChecked = settings.warrantyEnabled
        binding.switchWarranty.setOnCheckedChangeListener { _, checked ->
            settings.warrantyEnabled = checked
        }
        bindAllowNotifications()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) bindAllowNotifications()
    }

    private fun bindAllowNotifications() {
        val notes = NotificationPreferences(requireContext())
        binding.switchAllowNotifications.setOnCheckedChangeListener(null)
        binding.switchAllowNotifications.isChecked = notes.enabled
        binding.switchAllowNotifications.setOnCheckedChangeListener { _, checked ->
            onAllowNotifications(checked)
        }
    }

    private fun onAllowNotifications(checked: Boolean) {
        val notes = NotificationPreferences(requireContext())
        if (!checked) {
            notes.enabled = false
            NotificationScheduler.reconcile(requireContext())
            return
        }
        if (!notes.permissionPrompted) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.notify_opt_in_title)
                .setMessage(R.string.notify_opt_in_body)
                .setPositiveButton(R.string.notify_continue) { _, _ ->
                    notes.permissionPrompted = true
                    enableNotificationsIfAllowed()
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
        enableNotificationsIfAllowed()
    }

    private fun enableNotificationsIfAllowed() {
        val notes = NotificationPreferences(requireContext())
        if (Build.VERSION.SDK_INT >= 33 && !notificationsPermitted()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        notes.enabled = true
        NotificationScheduler.reconcile(requireContext())
        bindAllowNotifications()
    }

    private fun notificationsPermitted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
