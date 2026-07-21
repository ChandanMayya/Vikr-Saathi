package com.kex.vikrsaathi.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentGeneralSettingsBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.AppThemeManager
import com.kex.vikrsaathi.util.ThemeMode
import com.kex.vikrsaathi.util.ViewModelFactory

class GeneralSettingsFragment : Fragment() {

    private var _binding: FragmentGeneralSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private val autoSave = SettingsAutoSave(onSave = { persistSettings() })
    private var suppressThemeSelection = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeneralSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        installHelpMenu(HelpScreen.GENERAL_SETTINGS)

        autoSave.suppress = true
        viewModel.shopName.observe(viewLifecycleOwner) {
            if (binding.editShopName.text.isNullOrEmpty()) binding.editShopName.setText(it)
        }
        viewModel.currencySymbol.observe(viewLifecycleOwner) {
            if (binding.editCurrencySymbol.text.isNullOrEmpty()) binding.editCurrencySymbol.setText(it)
        }
        viewModel.defaultDiscount.observe(viewLifecycleOwner) {
            if (binding.editDefaultDiscount.text.isNullOrEmpty()) {
                binding.editDefaultDiscount.setText(it.toString())
            }
        }
        autoSave.suppress = false

        viewModel.themeMode.observe(viewLifecycleOwner, ::bindThemeMode)
        binding.radioThemeMode.setOnCheckedChangeListener { _, checkedId ->
            if (suppressThemeSelection) return@setOnCheckedChangeListener
            val mode = themeModeFor(checkedId) ?: return@setOnCheckedChangeListener
            if (mode == viewModel.themeMode.value) return@setOnCheckedChangeListener
            viewModel.saveThemeMode(mode)
            if (AppThemeManager.apply(mode)) {
                requireActivity().recreate()
            }
        }

        autoSave.attach(
            binding.editShopName,
            binding.editCurrencySymbol,
            binding.editDefaultDiscount
        )
    }

    override fun onPause() {
        autoSave.flush()
        super.onPause()
    }

    override fun onDestroyView() {
        autoSave.clear()
        super.onDestroyView()
        _binding = null
    }

    private fun persistSettings() {
        viewModel.saveShopName(binding.editShopName.text.toString().trim())
        viewModel.saveCurrency(binding.editCurrencySymbol.text.toString().trim())
        viewModel.saveDefaultDiscount(
            binding.editDefaultDiscount.text.toString().toDoubleOrNull() ?: 0.0
        )
    }

    private fun bindThemeMode(mode: ThemeMode) {
        suppressThemeSelection = true
        binding.radioThemeMode.check(
            when (mode) {
                ThemeMode.LIGHT -> R.id.radioThemeLight
                ThemeMode.DARK -> R.id.radioThemeDark
                ThemeMode.AUTO -> R.id.radioThemeAuto
                ThemeMode.SYSTEM -> R.id.radioThemeSystem
            }
        )
        suppressThemeSelection = false
    }

    private fun themeModeFor(checkedId: Int): ThemeMode? = when (checkedId) {
        R.id.radioThemeLight -> ThemeMode.LIGHT
        R.id.radioThemeDark -> ThemeMode.DARK
        R.id.radioThemeAuto -> ThemeMode.AUTO
        R.id.radioThemeSystem -> ThemeMode.SYSTEM
        else -> null
    }
}
