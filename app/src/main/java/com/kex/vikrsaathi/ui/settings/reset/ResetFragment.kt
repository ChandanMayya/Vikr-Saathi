package com.kex.vikrsaathi.ui.settings.reset

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.reset.ResetHistoryEntry
import com.kex.vikrsaathi.data.reset.ResetOptions
import com.kex.vikrsaathi.databinding.FragmentResetBinding
import com.kex.vikrsaathi.util.ViewModelFactory

class ResetFragment : Fragment(), ResetHistoryBottomSheet.Callback {

    private var _binding: FragmentResetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResetViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private var progressDialog: AlertDialog? = null
    private var progressMessageView: TextView? = null
    private var progressIndicator: LinearProgressIndicator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbarMenu()

        binding.buttonAcceptDisclaimer.setOnClickListener {
            binding.layoutDisclaimer.visibility = View.GONE
            binding.layoutSelection.visibility = View.VISIBLE
        }

        binding.buttonProceedReset.setOnClickListener {
            val options = readSelectedOptions()
            if (!options.hasAnySelected()) {
                Toast.makeText(requireContext(), R.string.reset_none_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showFinalConfirmation(options)
        }

        viewModel.isWorking.observe(viewLifecycleOwner) { working ->
            binding.buttonAcceptDisclaimer.isEnabled = working != true
            binding.buttonProceedReset.isEnabled = working != true
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
        viewModel.resetComplete.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reset_complete)
                .setMessage(R.string.reset_complete_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            viewModel.clearResetResult()
        }
        viewModel.restoreComplete.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reset_restore_complete)
                .setMessage(
                    getString(
                        R.string.backup_import_customers_items,
                        result.customersImported,
                        result.itemsImported
                    )
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
            viewModel.clearRestoreResult()
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNullOrBlank()) return@observe
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    override fun onRestoreRequested(entry: ResetHistoryEntry) {
        val categoriesText = ResetHistoryBottomSheet.formatCategories(requireContext(), entry.resetCategories)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reset_restore_confirm_title)
            .setMessage(
                getString(
                    R.string.reset_restore_confirm_message,
                    ResetHistoryBottomSheet.formatDate(entry.performedAt),
                    categoriesText
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.reset_history_restore) { _, _ ->
                viewModel.restoreFromHistory(entry.id)
            }
            .show()
    }

    private fun readSelectedOptions(): ResetOptions {
        return ResetOptions(
            resetCustomers = binding.checkResetCustomers.isChecked,
            resetItems = binding.checkResetItems.isChecked,
            resetSales = binding.checkResetSales.isChecked,
            resetTemplates = binding.checkResetTemplates.isChecked,
            resetSettings = binding.checkResetSettings.isChecked,
            resetInvoiceConfig = binding.checkResetInvoiceConfig.isChecked
        )
    }

    private fun showFinalConfirmation(options: ResetOptions) {
        val labels = options.selectedCategoryKeys()
            .joinToString("\n") { "• ${ResetHistoryBottomSheet.formatCategory(requireContext(), it)}" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reset_final_confirm_title)
            .setMessage(getString(R.string.reset_final_confirm_message, labels))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.reset_final_confirm_action) { _, _ ->
                viewModel.performReset(options)
            }
            .show()
    }

    private fun setupToolbarMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_reset, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_reset_history -> {
                        ResetHistoryBottomSheet.newInstance()
                            .apply { callback = this@ResetFragment }
                            .show(childFragmentManager, "reset_history")
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showProgressDialog() {
        if (progressDialog?.isShowing == true) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_backup_progress, null)
        progressMessageView = dialogView.findViewById(R.id.textProgressMessage)
        progressIndicator = dialogView.findViewById(R.id.progressIndicator)
        dialogView.findViewById<TextView>(R.id.textProgressTitle).setText(R.string.reset_working)
        progressIndicator?.isIndeterminate = false
        progressIndicator?.progress = 0
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
