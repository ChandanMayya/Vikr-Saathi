package com.kex.vikrsaathi.ui.settings

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentSettingsBinding
import com.kex.vikrsaathi.util.ViewModelFactory

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private var pendingImageTarget: ImageTarget? = null
    private var suppressAutoSave = false
    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private var autoSaveRunnable: Runnable? = null

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val target = pendingImageTarget ?: return@registerForActivityResult
        uri ?: return@registerForActivityResult
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return@registerForActivityResult
        when (target) {
            ImageTarget.HEADER -> {
                viewModel.saveHeaderImage(bitmap)
                binding.imageHeaderPreview.setImageBitmap(bitmap)
            }
            ImageTarget.LOGO -> {
                viewModel.saveShopLogoImage(bitmap)
                binding.imageLogoPreview.setImageBitmap(bitmap)
            }
            ImageTarget.SIGNATURE -> {
                viewModel.saveSignatureImage(bitmap)
                binding.imageSignaturePreview.setImageBitmap(bitmap)
            }
        }
        pendingImageTarget = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        suppressAutoSave = true
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

        binding.imageHeaderPreview.setImageBitmap(viewModel.getHeaderImage())
        binding.imageLogoPreview.setImageBitmap(viewModel.getShopLogoImage())
        binding.imageSignaturePreview.setImageBitmap(viewModel.getSignatureImage())

        binding.editInvoicePrefix.setText(viewModel.invoicePrefix())
        binding.editInvoiceSuffix.setText(viewModel.invoiceSuffix())
        binding.editInvoiceSeparator.setText(viewModel.invoiceSeparator())
        binding.editInvoiceCounter.setText(viewModel.invoiceCounter().toString())
        updateInvoicePreview()
        suppressAutoSave = false

        val autoSaveWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateInvoicePreview()
                scheduleAutoSave()
            }
        }
        binding.editShopName.addTextChangedListener(autoSaveWatcher)
        binding.editCurrencySymbol.addTextChangedListener(autoSaveWatcher)
        binding.editDefaultDiscount.addTextChangedListener(autoSaveWatcher)
        binding.editInvoicePrefix.addTextChangedListener(autoSaveWatcher)
        binding.editInvoiceSuffix.addTextChangedListener(autoSaveWatcher)
        binding.editInvoiceSeparator.addTextChangedListener(autoSaveWatcher)
        binding.editInvoiceCounter.addTextChangedListener(autoSaveWatcher)

        binding.editInvoiceCounter.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val counter = binding.editInvoiceCounter.text.toString().trim().toIntOrNull()
            if (counter == null || counter < 1) {
                suppressAutoSave = true
                binding.editInvoiceCounter.setText(viewModel.invoiceCounter().toString())
                suppressAutoSave = false
                updateInvoicePreview()
                Toast.makeText(requireContext(), R.string.invalid_invoice_counter, Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonChangeHeader.setOnClickListener {
            pendingImageTarget = ImageTarget.HEADER
            imagePicker.launch("image/*")
        }
        binding.buttonChangeLogo.setOnClickListener {
            pendingImageTarget = ImageTarget.LOGO
            imagePicker.launch("image/*")
        }
        binding.buttonChangeSignature.setOnClickListener {
            pendingImageTarget = ImageTarget.SIGNATURE
            imagePicker.launch("image/*")
        }
        binding.buttonInvoiceTemplates.setOnClickListener {
            persistSettings()
            findNavController().navigate(R.id.action_settings_to_invoice_templates)
        }
        binding.buttonBackupRestore.setOnClickListener {
            persistSettings()
            findNavController().navigate(R.id.action_settings_to_backup)
        }
        binding.buttonResetData.setOnClickListener {
            persistSettings()
            findNavController().navigate(R.id.action_settings_to_reset)
        }
    }

    override fun onPause() {
        persistSettings()
        super.onPause()
    }

    private fun scheduleAutoSave() {
        if (suppressAutoSave) return
        autoSaveRunnable?.let { autoSaveHandler.removeCallbacks(it) }
        autoSaveRunnable = Runnable { persistSettings() }
        autoSaveHandler.postDelayed(autoSaveRunnable!!, 400)
    }

    private fun persistSettings() {
        if (suppressAutoSave) return
        val counter = binding.editInvoiceCounter.text.toString().trim().toIntOrNull()
        if (counter == null || counter < 1) return

        viewModel.saveShopName(binding.editShopName.text.toString().trim())
        viewModel.saveCurrency(binding.editCurrencySymbol.text.toString().trim())
        viewModel.saveDefaultDiscount(
            binding.editDefaultDiscount.text.toString().toDoubleOrNull() ?: 0.0
        )
        viewModel.saveInvoiceConfig(
            prefix = binding.editInvoicePrefix.text.toString().trim(),
            suffix = binding.editInvoiceSuffix.text.toString().trim(),
            separator = binding.editInvoiceSeparator.text.toString().trim().ifEmpty { "/" },
            counter = counter
        )
    }

    private fun updateInvoicePreview() {
        val counter = binding.editInvoiceCounter.text.toString().trim().toIntOrNull() ?: viewModel.invoiceCounter()
        val preview = com.kex.vikrsaathi.util.InvoiceNumberFormatter.preview(
            prefix = binding.editInvoicePrefix.text.toString().trim(),
            counter = counter,
            suffix = binding.editInvoiceSuffix.text.toString().trim(),
            separator = binding.editInvoiceSeparator.text.toString().trim().ifEmpty { "/" }
        )
        binding.textInvoicePreview.text = getString(R.string.invoice_number_preview, preview)
    }

    override fun onDestroyView() {
        autoSaveRunnable?.let { autoSaveHandler.removeCallbacks(it) }
        autoSaveRunnable = null
        super.onDestroyView()
        _binding = null
    }

    private enum class ImageTarget { HEADER, LOGO, SIGNATURE }
}
