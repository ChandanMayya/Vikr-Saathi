package com.kex.vikrsaathi.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentInvoiceCounterSettingsBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.ViewModelFactory

class InvoiceCounterSettingsFragment : Fragment() {

    private var _binding: FragmentInvoiceCounterSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private val autoSave = SettingsAutoSave(onSave = { persistSettings() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceCounterSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        installHelpMenu(HelpScreen.INVOICE_COUNTER)

        autoSave.suppress = true
        binding.editInvoicePrefix.setText(viewModel.invoicePrefix())
        binding.editInvoiceSuffix.setText(viewModel.invoiceSuffix())
        binding.editInvoiceSeparator.setText(viewModel.invoiceSeparator())
        binding.editInvoiceCounter.setText(viewModel.invoiceCounter().toString())
        updateInvoicePreview()
        autoSave.suppress = false

        autoSave.attach(
            binding.editInvoicePrefix,
            binding.editInvoiceSuffix,
            binding.editInvoiceSeparator,
            binding.editInvoiceCounter
        )

        binding.editInvoiceCounter.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val counter = binding.editInvoiceCounter.text.toString().trim().toIntOrNull()
            if (counter == null || counter < 1) {
                autoSave.suppress = true
                binding.editInvoiceCounter.setText(viewModel.invoiceCounter().toString())
                autoSave.suppress = false
                updateInvoicePreview()
                Toast.makeText(requireContext(), R.string.invalid_invoice_counter, Toast.LENGTH_SHORT).show()
            }
        }
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
        val counter = binding.editInvoiceCounter.text.toString().trim().toIntOrNull()
        if (counter == null || counter < 1) return
        viewModel.saveInvoiceConfig(
            prefix = binding.editInvoicePrefix.text.toString().trim(),
            suffix = binding.editInvoiceSuffix.text.toString().trim(),
            separator = binding.editInvoiceSeparator.text.toString().trim().ifEmpty { "/" },
            counter = counter
        )
        updateInvoicePreview()
    }

    private fun updateInvoicePreview() {
        val counter = binding.editInvoiceCounter.text.toString().trim().toIntOrNull()
            ?: viewModel.invoiceCounter()
        val preview = com.kex.vikrsaathi.util.InvoiceNumberFormatter.preview(
            prefix = binding.editInvoicePrefix.text.toString().trim(),
            counter = counter,
            suffix = binding.editInvoiceSuffix.text.toString().trim(),
            separator = binding.editInvoiceSeparator.text.toString().trim().ifEmpty { "/" }
        )
        binding.textInvoicePreview.text = getString(R.string.invoice_number_preview, preview)
    }
}
