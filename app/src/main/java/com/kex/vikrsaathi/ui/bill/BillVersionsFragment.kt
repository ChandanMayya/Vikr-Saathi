package com.kex.vikrsaathi.ui.bill

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.model.BillVersionDetails
import com.kex.vikrsaathi.data.repository.StockShortfall
import com.kex.vikrsaathi.databinding.FragmentBillVersionsBinding
import com.kex.vikrsaathi.ui.finance.FinanceUi
import com.kex.vikrsaathi.util.InventoryMode
import com.kex.vikrsaathi.util.PdfPrintHelper
import com.kex.vikrsaathi.util.ViewModelFactory

class BillVersionsFragment : Fragment() {

    private var _binding: FragmentBillVersionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillVersionsViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var adapter: BillVersionAdapter
    private var selectedVersionNumber: Int? = null
    private var versions: List<BillVersionDetails> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillVersionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedVersionNumber = savedInstanceState?.getInt(STATE_SELECTED, -1)?.takeIf { it > 0 }
        adapter = BillVersionAdapter(
            currencySymbol = viewModel.currencySymbol,
            onSelect = { versionNumber ->
                selectedVersionNumber = versionNumber
                adapter.submit(versions, selectedVersionNumber)
            },
            onEditCurrent = {
                setFragmentResult(REQUEST_KEY, bundleOf(EXTRA_ENABLE_EDIT to true))
                findNavController().navigateUp()
            },
            onRestore = ::confirmRestore,
            onPrint = ::printVersion,
            onDuplicate = { duplicateVersion(it) }
        )
        binding.recyclerVersions.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerVersions.adapter = adapter

        viewModel.history.observe(viewLifecycleOwner) { state ->
            versions = state.versions
            binding.textBillNumber.text = state.billNumber
            binding.textBillNumber.isVisible = state.billNumber.isNotBlank()
            binding.textEmpty.isVisible = state.versions.isEmpty()
            binding.recyclerVersions.isVisible = state.versions.isNotEmpty()
            if (selectedVersionNumber == null || versions.none { it.version.versionNumber == selectedVersionNumber }) {
                selectedVersionNumber = versions.firstOrNull { it.isCurrent }?.version?.versionNumber
            }
            adapter.submit(versions, selectedVersionNumber)
        }

        val billId = arguments?.getLong(ARG_BILL_ID, -1L) ?: -1L
        viewModel.load(billId)
    }

    private fun confirmRestore(details: BillVersionDetails) {
        val newest = versions.maxOfOrNull { it.version.versionNumber } ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore)
            .setMessage(getString(R.string.version_restore_hint, newest + 1, newest))
            .setPositiveButton(R.string.restore) { _, _ ->
                restoreVersion(details.version.versionNumber)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun restoreVersion(versionNumber: Int, shortageAcknowledged: Boolean = false) {
        viewModel.restoreVersion(versionNumber, shortageAcknowledged) { outcome ->
            if (!isAdded) return@restoreVersion
            when (outcome) {
                VersionActionOutcome.Applied -> Unit
                is VersionActionOutcome.Created,
                VersionActionOutcome.Missing -> toast(getString(R.string.bill_not_found))
                is VersionActionOutcome.Shortage -> showShortage(outcome.shortfalls) {
                    restoreVersion(versionNumber, shortageAcknowledged = true)
                }
                is VersionActionOutcome.Failed -> toast(FinanceUi.message(requireContext(), outcome.error))
            }
        }
    }

    private fun duplicateVersion(details: BillVersionDetails, shortageAcknowledged: Boolean = false) {
        viewModel.duplicateVersion(details.version.versionNumber, shortageAcknowledged) { outcome ->
            if (!isAdded) return@duplicateVersion
            when (outcome) {
                is VersionActionOutcome.Created -> {
                    findNavController().navigate(
                        R.id.action_versions_to_bill,
                        bundleOf(BillFragment.ARG_BILL_ID to outcome.billId)
                    )
                }
                is VersionActionOutcome.Shortage -> showShortage(outcome.shortfalls) {
                    duplicateVersion(details, shortageAcknowledged = true)
                }
                VersionActionOutcome.Missing -> toast(getString(R.string.bill_not_found))
                is VersionActionOutcome.Failed -> toast(FinanceUi.message(requireContext(), outcome.error))
                VersionActionOutcome.Applied -> Unit
            }
        }
    }

    private fun printVersion(details: BillVersionDetails) {
        viewModel.printVersion(requireContext(), details) { file ->
            if (!isAdded) return@printVersion
            if (file == null) {
                toast(getString(R.string.pdf_generation_failed))
                return@printVersion
            }
            PdfPrintHelper.print(
                requireContext(),
                file,
                getString(R.string.print_pdf_job, details.version.billNumber)
            )
        }
    }

    private fun showShortage(shortfalls: List<StockShortfall>, onContinue: () -> Unit) {
        val details = shortfalls.joinToString("\n") {
            val line = getString(R.string.stock_shortfall_line, it.itemName, it.available, it.required)
            if (it.expired > 0) line + "\n" + getString(R.string.stock_shortfall_expired, it.expired) else line
        }
        when (viewModel.inventoryMode) {
            InventoryMode.BLOCK -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.insufficient_stock_title)
                    .setMessage(getString(R.string.insufficient_stock_block_message) + "\n\n" + details)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            InventoryMode.WARN -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.insufficient_stock_title)
                    .setMessage(getString(R.string.insufficient_stock_warn_message) + "\n\n" + details)
                    .setPositiveButton(R.string.yes) { _, _ -> onContinue() }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }
            InventoryMode.OFF -> onContinue()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedVersionNumber?.let { outState.putInt(STATE_SELECTED, it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_BILL_ID = "billId"
        const val REQUEST_KEY = "bill_versions"
        const val EXTRA_ENABLE_EDIT = "enableEdit"
        private const val STATE_SELECTED = "selectedVersion"
    }
}
