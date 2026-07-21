package com.kex.vikrsaathi.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentInventorySettingsBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.InventoryMode
import com.kex.vikrsaathi.util.ViewModelFactory

class InventorySettingsFragment : Fragment() {

    private var _binding: FragmentInventorySettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private val autoSave = SettingsAutoSave(onSave = { persistSettings() })
    private var suppressInventorySelection = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventorySettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.INVENTORY_SETTINGS)

        autoSave.suppress = true
        viewModel.lowStockThreshold.observe(viewLifecycleOwner) {
            if (binding.editLowStockThreshold.text.isNullOrEmpty()) {
                binding.editLowStockThreshold.setText(it.toString())
            }
        }
        autoSave.suppress = false

        viewModel.inventoryMode.observe(viewLifecycleOwner, ::bindInventoryMode)
        binding.radioInventoryMode.setOnCheckedChangeListener { _, checkedId ->
            if (suppressInventorySelection) return@setOnCheckedChangeListener
            val mode = inventoryModeFor(checkedId) ?: return@setOnCheckedChangeListener
            if (mode == viewModel.inventoryMode.value) return@setOnCheckedChangeListener
            viewModel.saveInventoryMode(mode)
        }

        autoSave.attach(binding.editLowStockThreshold)
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
        viewModel.saveLowStockThreshold(
            binding.editLowStockThreshold.text.toString().toIntOrNull() ?: 5
        )
    }

    private fun bindInventoryMode(mode: InventoryMode) {
        suppressInventorySelection = true
        binding.radioInventoryMode.check(
            when (mode) {
                InventoryMode.OFF -> R.id.radioInventoryOff
                InventoryMode.WARN -> R.id.radioInventoryWarn
                InventoryMode.BLOCK -> R.id.radioInventoryBlock
            }
        )
        suppressInventorySelection = false
    }

    private fun inventoryModeFor(checkedId: Int): InventoryMode? = when (checkedId) {
        R.id.radioInventoryOff -> InventoryMode.OFF
        R.id.radioInventoryWarn -> InventoryMode.WARN
        R.id.radioInventoryBlock -> InventoryMode.BLOCK
        else -> null
    }
}
