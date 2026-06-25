package com.loctell.vikrsaathi.ui.settings

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.loctell.vikrsaathi.R
import com.loctell.vikrsaathi.VikrSaathiApp
import com.loctell.vikrsaathi.databinding.FragmentSettingsBinding
import com.loctell.vikrsaathi.util.ViewModelFactory

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
        binding.imageSignaturePreview.setImageBitmap(viewModel.getSignatureImage())

        binding.buttonChangeHeader.setOnClickListener {
            pendingImageTarget = ImageTarget.HEADER
            imagePicker.launch("image/*")
        }
        binding.buttonChangeSignature.setOnClickListener {
            pendingImageTarget = ImageTarget.SIGNATURE
            imagePicker.launch("image/*")
        }
        binding.buttonSaveSettings.setOnClickListener {
            viewModel.saveShopName(binding.editShopName.text.toString().trim())
            viewModel.saveCurrency(binding.editCurrencySymbol.text.toString().trim())
            viewModel.saveDefaultDiscount(
                binding.editDefaultDiscount.text.toString().toDoubleOrNull() ?: 0.0
            )
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class ImageTarget { HEADER, SIGNATURE }
}
