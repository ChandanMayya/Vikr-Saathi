package com.kex.vikrsaathi.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentDocumentNumberSettingsBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.InvoiceNumberFormatter
import com.kex.vikrsaathi.util.ViewModelFactory

class DocumentNumberSettingsFragment : Fragment() {

    private var _binding: FragmentDocumentNumberSettingsBinding? = null
    private val binding get() = _binding!!
    private val quoteSeries get() = arguments?.getString(ARG_SERIES) == SERIES_QUOTE
    private val autoSave = SettingsAutoSave(onSave = { persistSettings() })

    private val viewModel: SettingsViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDocumentNumberSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.INVOICE_COUNTER)
        binding.layoutNumberCounter.isVisible = !quoteSeries
        binding.textNumberHint.setText(
            if (quoteSeries) R.string.quote_number_hint else R.string.request_number_hint
        )
        autoSave.suppress = true
        if (quoteSeries) {
            binding.editNumberPrefix.setText(viewModel.quotePrefix())
            binding.editNumberSuffix.setText(viewModel.quoteSuffix())
            binding.editNumberSeparator.setText(viewModel.quoteSeparator())
        } else {
            binding.editNumberPrefix.setText(viewModel.requestPrefix())
            binding.editNumberSuffix.setText(viewModel.requestSuffix())
            binding.editNumberSeparator.setText(viewModel.requestSeparator())
            binding.editNumberCounter.setText(viewModel.requestCounter().toString())
        }
        updatePreview()
        autoSave.suppress = false
        autoSave.attach(
            binding.editNumberPrefix,
            binding.editNumberSuffix,
            binding.editNumberSeparator,
            binding.editNumberCounter
        )
        binding.editNumberCounter.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus || quoteSeries) return@setOnFocusChangeListener
            val counter = binding.editNumberCounter.text.toString().trim().toIntOrNull()
            if (counter == null || counter < 1) {
                autoSave.suppress = true
                binding.editNumberCounter.setText(viewModel.requestCounter().toString())
                autoSave.suppress = false
                updatePreview()
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
        val prefix = binding.editNumberPrefix.text.toString().trim()
        val suffix = binding.editNumberSuffix.text.toString().trim()
        val separator = binding.editNumberSeparator.text.toString().trim().ifEmpty { "-" }
        if (quoteSeries) {
            viewModel.saveQuoteNumberConfig(prefix, suffix, separator)
        } else {
            val counter = binding.editNumberCounter.text.toString().trim().toIntOrNull()
            if (counter == null || counter < 1) return
            viewModel.saveRequestNumberConfig(prefix, suffix, separator, counter)
        }
        updatePreview()
    }

    private fun updatePreview() {
        val prefix = binding.editNumberPrefix.text.toString().trim()
        val suffix = binding.editNumberSuffix.text.toString().trim()
        val separator = binding.editNumberSeparator.text.toString().trim().ifEmpty { "-" }
        if (quoteSeries) {
            val preview = InvoiceNumberFormatter.preview(
                prefix = prefix,
                counter = 1,
                suffix = suffix,
                separator = separator,
                counterMinDigits = 0
            )
            binding.textNumberPreview.text = getString(R.string.quote_number_preview, preview)
        } else {
            val counter = binding.editNumberCounter.text.toString().trim().toIntOrNull()
                ?: viewModel.requestCounter()
            val preview = InvoiceNumberFormatter.preview(
                prefix = prefix,
                counter = counter,
                suffix = suffix,
                separator = separator,
                counterMinDigits = 3
            )
            binding.textNumberPreview.text = getString(R.string.request_number_preview, preview)
        }
    }

    companion object {
        const val ARG_SERIES = "series"
        const val SERIES_QUOTE = "QUOTE"
    }
}
