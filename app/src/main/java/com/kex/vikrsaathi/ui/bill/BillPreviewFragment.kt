package com.kex.vikrsaathi.ui.bill

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentBillPreviewBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.FileShareHelper
import com.kex.vikrsaathi.util.ViewModelFactory

class BillPreviewFragment : Fragment() {

    private var _binding: FragmentBillPreviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillPreviewViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        installHelpMenu(HelpScreen.BILL_PREVIEW)

        binding.billPreviewCanvas.showPreview = true
        binding.billPreviewCanvas.previewGesturesEnabled = true
        binding.billPreviewCanvas.showGrid = false
        binding.billPreviewCanvas.showGuides = false

        val billId = arguments?.getLong(ARG_BILL_ID, -1L) ?: -1L
        if (savedInstanceState == null) {
            viewModel.load(requireContext(), billId)
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
            binding.billPreviewCanvas.alpha = if (loading) 0.4f else 1f
            binding.buttonDownloadPdf.isEnabled = !loading
            binding.buttonPrintBill.isEnabled = !loading
            binding.buttonDone.isEnabled = !loading
        }

        viewModel.preview.observe(viewLifecycleOwner) { state ->
            if (state == null) {
                if (viewModel.loading.value != true) {
                    Toast.makeText(requireContext(), R.string.bill_not_found, Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                return@observe
            }
            binding.textBillPreviewTitle.text = getString(R.string.bill_preview_title, state.billNumber)
            binding.textBillPreviewSubtitle.text = state.customerName
            binding.billPreviewCanvas.setTemplate(state.template, emptySet())
            binding.billPreviewCanvas.setRenderContext(state.renderContext)
        }

        binding.buttonDownloadPdf.setOnClickListener { exportPdf(print = false) }
        binding.buttonPrintBill.setOnClickListener { exportPdf(print = true) }
        binding.buttonDone.setOnClickListener { findNavController().navigateUp() }
    }

    private fun exportPdf(print: Boolean) {
        binding.buttonDownloadPdf.isEnabled = false
        binding.buttonPrintBill.isEnabled = false
        viewModel.exportPdf(requireContext()) { file ->
            binding.buttonDownloadPdf.isEnabled = true
            binding.buttonPrintBill.isEnabled = true
            if (file == null) {
                Toast.makeText(requireContext(), R.string.pdf_generation_failed, Toast.LENGTH_SHORT).show()
                return@exportPdf
            }
            if (print) {
                FileShareHelper.shareFile(
                    requireContext(),
                    file,
                    "application/pdf",
                    getString(R.string.print)
                )
            } else {
                try {
                    FileShareHelper.openFile(requireContext(), file, "application/pdf")
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), R.string.pdf_saved, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_BILL_ID = "billId"
    }
}
