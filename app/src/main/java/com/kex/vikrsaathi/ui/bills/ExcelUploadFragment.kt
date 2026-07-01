package com.kex.vikrsaathi.ui.bills

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentExcelUploadBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.ViewModelFactory

class ExcelUploadFragment : Fragment() {

    private var _binding: FragmentExcelUploadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExcelUploadViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        viewModel.loadPreview(requireContext(), uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExcelUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        installHelpMenu(HelpScreen.EXCEL_UPLOAD)

        binding.buttonSelectExcelFile.setOnClickListener {
            filePicker.launch(arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "text/csv",
                "text/comma-separated-values",
                "application/csv"
            ))
        }

        binding.buttonImportExcel.setOnClickListener {
            viewModel.importFile(requireContext()) { result ->
                val message = getString(
                    R.string.import_complete,
                    result.imported,
                    result.skipped
                )
                val details = if (result.errors.isEmpty()) {
                    message
                } else {
                    message + "\n\n" + result.errors.take(5).joinToString("\n")
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.import_excel)
                    .setMessage(details)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }

        viewModel.selectedUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                binding.textSelectedFile.visibility = View.VISIBLE
                binding.textSelectedFile.text = uri.lastPathSegment ?: uri.toString()
            }
        }

        viewModel.previewRows.observe(viewLifecycleOwner) { rows ->
            if (rows.isEmpty()) {
                binding.textPreviewSummary.visibility = View.GONE
                binding.cardPreview.visibility = View.GONE
                binding.buttonImportExcel.isEnabled = false
                if (viewModel.selectedUri.value != null) {
                    Toast.makeText(requireContext(), R.string.no_rows_found, Toast.LENGTH_SHORT).show()
                }
                return@observe
            }

            val billCount = rows.map { it.billNumber }.distinct().size
            binding.textPreviewSummary.visibility = View.VISIBLE
            binding.textPreviewSummary.text = getString(R.string.rows_found, rows.size, billCount)
            binding.cardPreview.visibility = View.VISIBLE
            binding.buttonImportExcel.isEnabled = true

            val preview = rows.take(5).joinToString("\n") { row ->
                "${row.billNumber} | ${row.itemName} x${row.quantity} | MRP ${row.mrp}"
            }
            val suffix = if (rows.size > 5) "\n…" else ""
            binding.textPreviewDetails.text = preview + suffix
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
