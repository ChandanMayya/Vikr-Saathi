package com.kex.vikrsaathi.ui.finance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentAccountsSettingsBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu

class AccountsSettingsFragment : Fragment() {

    private var _binding: FragmentAccountsSettingsBinding? = null
    private val binding get() = _binding!!

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
        binding.switchCustomerBalances.setOnCheckedChangeListener { _, checked ->
            settings.customerBalanceEnabled = checked
        }
        binding.switchVendorManagement.setOnCheckedChangeListener { _, checked ->
            settings.vendorManagementEnabled = checked
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
