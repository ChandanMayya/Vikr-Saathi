package com.kex.vikrsaathi.ui.settings.backup

import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.backup.BackupExportOptions
import com.kex.vikrsaathi.data.backup.BackupManifest
import com.kex.vikrsaathi.databinding.FragmentBackupBinding
import com.kex.vikrsaathi.util.FileShareHelper
import com.kex.vikrsaathi.util.ViewModelFactory
import java.text.DateFormat
import java.util.Date

class BackupFragment : Fragment() {

    private var _binding: FragmentBackupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BackupViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private var progressDialog: AlertDialog? = null
    private var progressMessageView: TextView? = null
    private var progressIndicator: LinearProgressIndicator? = null
    private var pendingExportOptions: BackupExportOptions? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val options = pendingExportOptions
        pendingExportOptions = null
        if (granted && options != null) {
            viewModel.exportBackup(options)
        } else if (!granted) {
            Toast.makeText(requireContext(), R.string.backup_storage_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    private val importPicker = registerForActivityResult(
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
        viewModel.loadImportPreview(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonExportBackup.setOnClickListener { showExportOptions() }
        binding.buttonImportBackup.setOnClickListener {
            importPicker.launch(arrayOf("application/json", "text/plain"))
        }

        viewModel.isWorking.observe(viewLifecycleOwner) { working ->
            binding.buttonExportBackup.isEnabled = working != true
            binding.buttonImportBackup.isEnabled = working != true
            if (working == true) {
                showProgressDialog()
            } else {
                dismissProgressDialog()
            }
        }
        viewModel.progressMessage.observe(viewLifecycleOwner) { message ->
            progressMessageView?.text = message
        }
        viewModel.progressPercent.observe(viewLifecycleOwner) { percent ->
            progressIndicator?.isIndeterminate = false
            progressIndicator?.setProgressCompat(percent, true)
        }
        viewModel.exportComplete.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_export_complete)
                .setMessage(getString(R.string.backup_export_saved, result.displayPath))
                .setPositiveButton(R.string.backup_share_file) { _, _ ->
                    val uri = result.shareUri(requireContext()) ?: return@setPositiveButton
                    FileShareHelper.shareUri(
                        requireContext(),
                        uri,
                        "application/json",
                        getString(R.string.backup_share_title)
                    )
                }
                .setNegativeButton(android.R.string.ok, null)
                .show()
            viewModel.clearExportResult()
        }
        viewModel.importManifest.observe(viewLifecycleOwner) { manifest ->
            if (manifest == null) return@observe
            showImportConfirmation(manifest)
        }
        viewModel.importResult.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            val summary = buildString {
                append(getString(R.string.backup_import_summary, result.billsImported, result.billsSkipped))
                append("\n")
                append(getString(R.string.backup_import_customers_items, result.customersImported, result.itemsImported))
                append("\n")
                append(getString(R.string.backup_import_templates, result.templatesImported))
                if (result.settingsRestored) {
                    append("\n")
                    append(getString(R.string.backup_import_settings_restored))
                }
                if (result.errors.isNotEmpty()) {
                    append("\n\n")
                    append(result.errors.take(5).joinToString("\n"))
                }
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_import_complete)
                .setMessage(summary)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            viewModel.clearImportResult()
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNullOrBlank()) return@observe
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    private fun showExportOptions() {
        val sheet = BackupExportOptionsBottomSheet.newInstance()
        sheet.callback = object : BackupExportOptionsBottomSheet.Callback {
            override fun onExport(options: BackupExportOptions) {
                startExport(options)
            }
        }
        sheet.show(parentFragmentManager, "backup_export_options")
    }

    private fun startExport(options: BackupExportOptions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingExportOptions = options
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        viewModel.exportBackup(options)
    }

    private fun showImportConfirmation(manifest: BackupManifest) {
        val dateText = if (manifest.exportedAt > 0L) {
            DateFormat.getDateTimeInstance().format(Date(manifest.exportedAt))
        } else {
            getString(R.string.backup_unknown_date)
        }
        val details = buildString {
            append(getString(R.string.backup_import_confirm_intro, dateText))
            append("\n\n")
            if (manifest.includes.isNotEmpty()) {
                append(getString(R.string.backup_import_includes, manifest.includes.joinToString(", ")))
                append("\n")
            }
            if (manifest.customerCount > 0) {
                append(getString(R.string.backup_count_customers, manifest.customerCount))
                append("\n")
            }
            if (manifest.itemCount > 0) {
                append(getString(R.string.backup_count_items, manifest.itemCount))
                append("\n")
            }
            if (manifest.billCount > 0) {
                append(getString(R.string.backup_count_bills, manifest.billCount))
                append("\n")
            }
            if (manifest.templateCount > 0) {
                append(getString(R.string.backup_count_templates, manifest.templateCount))
                append("\n")
            }
            if (manifest.hasHeaderImage || manifest.hasSignatureImage || manifest.hasLogoImage) {
                append(getString(R.string.backup_includes_branding_images))
                append("\n")
            }
            append("\n")
            append(getString(R.string.backup_import_warning))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_confirm_import)
            .setMessage(details.trim())
            .setPositiveButton(R.string.backup_import_now) { _, _ ->
                viewModel.confirmImport()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                viewModel.clearImportManifest()
            }
            .setOnCancelListener {
                viewModel.clearImportManifest()
            }
            .show()
    }

    private fun showProgressDialog() {
        if (progressDialog?.isShowing == true) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_backup_progress, null)
        progressMessageView = dialogView.findViewById(R.id.textProgressMessage)
        progressIndicator = dialogView.findViewById(R.id.progressIndicator)
        progressIndicator?.isIndeterminate = true
        progressMessageView?.text = getString(R.string.backup_working)
        progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        progressDialog?.show()
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressMessageView = null
        progressIndicator = null
    }

    override fun onDestroyView() {
        dismissProgressDialog()
        super.onDestroyView()
        _binding = null
    }
}
