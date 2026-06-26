package com.kex.vikrsaathi.ui.settings

import android.graphics.BitmapFactory
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
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

        val invoiceWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateInvoicePreview()
        }
        binding.editInvoicePrefix.addTextChangedListener(invoiceWatcher)
        binding.editInvoiceSuffix.addTextChangedListener(invoiceWatcher)
        binding.editInvoiceSeparator.addTextChangedListener(invoiceWatcher)
        binding.editInvoiceCounter.addTextChangedListener(invoiceWatcher)

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
            findNavController().navigate(R.id.action_settings_to_invoice_templates)
        }
        binding.buttonBackupRestore.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_backup)
        }
        binding.buttonSaveSettings.setOnClickListener {
            viewModel.saveShopName(binding.editShopName.text.toString().trim())
            viewModel.saveCurrency(binding.editCurrencySymbol.text.toString().trim())
            viewModel.saveDefaultDiscount(
                binding.editDefaultDiscount.text.toString().toDoubleOrNull() ?: 0.0
            )
            val counter = binding.editInvoiceCounter.text.toString().trim().toIntOrNull()
            if (counter == null || counter < 1) {
                Toast.makeText(requireContext(), R.string.invalid_invoice_counter, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveInvoiceConfig(
                prefix = binding.editInvoicePrefix.text.toString().trim(),
                suffix = binding.editInvoiceSuffix.text.toString().trim(),
                separator = binding.editInvoiceSeparator.text.toString().trim().ifEmpty { "/" },
                counter = counter
            )
            updateInvoicePreview()
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
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
        super.onDestroyView()
        _binding = null
    }

    private enum class ImageTarget { HEADER, LOGO, SIGNATURE }
}
