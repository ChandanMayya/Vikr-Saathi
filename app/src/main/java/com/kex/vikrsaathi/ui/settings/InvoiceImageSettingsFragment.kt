package com.kex.vikrsaathi.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.model.template.DataBindingKey
import com.kex.vikrsaathi.databinding.FragmentInvoiceImageSettingsBinding
import com.kex.vikrsaathi.ui.settings.invoicebuilder.ImageBoundsAdjustDialog
import com.kex.vikrsaathi.util.ViewModelFactory

class InvoiceImageSettingsFragment : Fragment() {

    private var _binding: FragmentInvoiceImageSettingsBinding? = null
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
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return@registerForActivityResult
        when (target) {
            ImageTarget.HEADER -> {
                viewModel.saveHeaderImage(bitmap)
                binding.imageHeaderPreview.setImageBitmap(bitmap)
                offerTemplateLayoutSync(DataBindingKey.HEADER_IMAGE, bitmap)
            }
            ImageTarget.LOGO -> {
                viewModel.saveShopLogoImage(bitmap)
                binding.imageLogoPreview.setImageBitmap(bitmap)
                offerTemplateLayoutSync(DataBindingKey.SHOP_LOGO, bitmap)
            }
            ImageTarget.SIGNATURE -> {
                viewModel.saveSignatureImage(bitmap)
                binding.imageSignaturePreview.setImageBitmap(bitmap)
                offerTemplateLayoutSync(DataBindingKey.SIGNATURE_IMAGE, bitmap)
            }
        }
        pendingImageTarget = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceImageSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imageHeaderPreview.setImageBitmap(viewModel.getHeaderImage())
        binding.imageLogoPreview.setImageBitmap(viewModel.getShopLogoImage())
        binding.imageSignaturePreview.setImageBitmap(viewModel.getSignatureImage())

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
    }

    private fun offerTemplateLayoutSync(bindingKey: DataBindingKey, bitmap: Bitmap) {
        viewModel.checkImageLayoutSync(bindingKey, bitmap) { templateCount, sampleBounds ->
            if (!isAdded) return@checkImageLayoutSync
            ImageBoundsAdjustDialog.confirmSettingsSyncIfNeeded(
                fragment = this,
                affectedTemplateCount = templateCount,
                sampleBounds = sampleBounds
            ) { adjust ->
                if (!adjust) return@confirmSettingsSyncIfNeeded
                viewModel.syncImageLayoutForBinding(bindingKey, bitmap) { updated ->
                    if (!isAdded) return@syncImageLayoutForBinding
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.image_bounds_sync_done, updated),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class ImageTarget { HEADER, LOGO, SIGNATURE }
}
