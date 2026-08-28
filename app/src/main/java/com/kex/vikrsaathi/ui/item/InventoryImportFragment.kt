package com.kex.vikrsaathi.ui.item

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
import com.kex.vikrsaathi.databinding.FragmentInventoryImportBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.FileShareHelper
import com.kex.vikrsaathi.util.ItemCatalogExcelExporter
import com.kex.vikrsaathi.util.ViewModelFactory
import java.io.File

class InventoryImportFragment : Fragment() {

    private var _binding: FragmentInventoryImportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventoryImportViewModel by viewModels {
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
        _binding = FragmentInventoryImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        installHelpMenu(HelpScreen.INVENTORY_IMPORT)

        binding.buttonDownloadTemplate.setOnClickListener {
            val file = ItemCatalogExcelExporter.exportTemplate(requireContext())
            showFileDialog(
                file = file,
                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                messageRes = R.string.inventory_template_saved
            )
        }

        binding.buttonSelectInventoryFile.setOnClickListener {
            filePicker.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "text/csv",
                    "text/comma-separated-values",
                    "application/csv"
                )
            )
        }

        binding.buttonImportInventory.setOnClickListener {
            binding.buttonImportInventory.isEnabled = false
            viewModel.importFile(requireContext()) { result ->
                binding.buttonImportInventory.isEnabled = viewModel.previewRows.value?.isNotEmpty() == true
                showImportResult(result)
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
                binding.buttonImportInventory.isEnabled = false
                if (viewModel.selectedUri.value != null) {
                    Toast.makeText(requireContext(), R.string.no_inventory_rows_found, Toast.LENGTH_SHORT).show()
                }
                return@observe
            }

            binding.textPreviewSummary.visibility = View.VISIBLE
            binding.textPreviewSummary.text = getString(R.string.inventory_rows_found, rows.size)
            binding.cardPreview.visibility = View.VISIBLE
            binding.buttonImportInventory.isEnabled = viewModel.importing.value != true

            val preview = rows.take(5).joinToString("\n") { row ->
                "${row.name} | MRP ${row.mrp} | Stock ${row.stockQty}"
            }
            val suffix = if (rows.size > 5) "\n…" else ""
            binding.textPreviewDetails.text = preview + suffix
        }

        viewModel.importing.observe(viewLifecycleOwner) { importing ->
            binding.buttonImportInventory.isEnabled =
                !importing && viewModel.previewRows.value?.isNotEmpty() == true
            binding.buttonImportInventory.text = if (importing) {
                getString(R.string.importing)
            } else {
                getString(R.string.import_inventory)
            }
        }
    }

    private fun showImportResult(result: com.kex.vikrsaathi.util.ItemCatalogImportResult) {
        val message = buildString {
            append(getString(R.string.inventory_import_complete, result.added, result.updated))
            if (result.errors.isNotEmpty()) {
                append("\n\n")
                append(getString(R.string.inventory_import_errors_header))
                append("\n")
                append(result.errors.take(10).joinToString("\n"))
                if (result.errors.size > 10) {
                    append("\n")
                    append(getString(R.string.inventory_import_errors_more, result.errors.size - 10))
                }
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.import_inventory)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showFileDialog(file: File, mimeType: String, messageRes: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.inventory_import)
            .setMessage(getString(messageRes))
            .setPositiveButton(R.string.open_file) { _, _ ->
                try {
                    FileShareHelper.openFile(requireContext(), file, mimeType)
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), file.absolutePath, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.share_file) { _, _ ->
                FileShareHelper.shareFile(requireContext(), file, mimeType, getString(messageRes))
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
