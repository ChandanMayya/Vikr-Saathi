package com.kex.vikrsaathi.ui.finance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.databinding.DialogVendorFormBinding
import com.kex.vikrsaathi.databinding.FragmentFinanceListBinding
import com.kex.vikrsaathi.databinding.FragmentVendorsBinding
import com.kex.vikrsaathi.ui.common.applyListViewMode
import com.kex.vikrsaathi.ui.common.installListViewOptionsDrawer
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.ListViewMode
import com.kex.vikrsaathi.util.ListViewPreferences
import com.kex.vikrsaathi.util.ListViewScreen
import kotlinx.coroutines.launch

class OutstandingCustomersFragment : Fragment() {

    private var _binding: FragmentFinanceListBinding? = null
    private val binding get() = _binding!!
    private val adapter = FinanceRowAdapter { row ->
        findNavController().navigate(R.id.customerLedgerFragment, CustomerLedgerFragment.args(row.id))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFinanceListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textListHeader.setText(R.string.outstanding_customers)
        binding.recyclerFinance.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        val app = requireActivity().application as VikrSaathiApp
        val symbol = app.settingsRepository.currencySymbol
        viewLifecycleOwner.lifecycleScope.launch {
            val rows = app.customerFinanceRepository.outstandingCustomers()
            val customers = app.database.customerDao().getAllCustomersSync().associateBy { it.id }
            val dues = rows.filter { it.balancePaise > 0L }
            val advances = rows.filter { it.balancePaise < 0L }
            val display = buildList {
                if (dues.isNotEmpty()) add(FinanceRow(getString(R.string.dues_header), ""))
                dues.forEach { row ->
                    val name = customers[row.customerId]?.name ?: "Customer ${row.customerId}"
                    add(
                        FinanceRow(
                            name,
                            "${FinanceUi.formatPaise(row.balancePaise, symbol)} · ${FinanceUi.ageLabel(requireContext(), row.ageBucket)}",
                            row.customerId
                        )
                    )
                }
                if (advances.isNotEmpty()) add(FinanceRow(getString(R.string.advances_header), ""))
                advances.forEach { row ->
                    val name = customers[row.customerId]?.name ?: "Customer ${row.customerId}"
                    add(FinanceRow(name, FinanceUi.formatPaise(row.balancePaise, symbol), row.customerId))
                }
            }
            if (_binding == null) return@launch
            adapter.submit(display.ifEmpty { listOf(FinanceRow(getString(R.string.outstanding_empty), "")) })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class VendorListFragment : Fragment() {

    private var _binding: FragmentVendorsBinding? = null
    private val binding get() = _binding!!
    private lateinit var listViewPrefs: ListViewPreferences
    private var viewMode: ListViewMode = ListViewMode.COMFORTABLE
    private val adapter = VendorAdapter(
        onOpen = { vendor ->
            findNavController().navigate(
                R.id.vendorDetailFragment,
                bundleOf(VendorDetailFragment.ARG_VENDOR_ID to vendor.id)
            )
        },
        onEdit = { showVendorDialog(it) },
        onArchive = { confirmArchive(it) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVendorsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as VikrSaathiApp
        listViewPrefs = app.listViewPreferences
        viewMode = listViewPrefs.getMode(ListViewScreen.VENDORS)
        installHelpMenu(HelpScreen.VENDORS)
        installListViewOptionsDrawer(
            drawerLayout = binding.drawerLayout,
            optionsTitleView = binding.optionsDrawer.textOptionsTitle,
            titleRes = R.string.vendors_options,
            radioViewMode = binding.optionsDrawer.radioViewMode,
            currentMode = { viewMode },
            onModeSelected = ::applyViewMode
        )
        binding.recyclerVendors.adapter = adapter
        applyViewMode(viewMode, persist = false)
        binding.fabAddVendor.setOnClickListener { showVendorDialog(null) }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun applyViewMode(mode: ListViewMode, persist: Boolean = true) {
        viewMode = mode
        if (persist) listViewPrefs.setMode(ListViewScreen.VENDORS, mode)
        adapter.viewMode = mode
        binding.recyclerVendors.applyListViewMode(mode)
        binding.detailsHeader.root.isVisible = mode == ListViewMode.DETAILS
    }

    private fun reload() {
        val app = requireActivity().application as VikrSaathiApp
        val symbol = app.settingsRepository.currencySymbol
        viewLifecycleOwner.lifecycleScope.launch {
            val cards = app.vendorRepository.getAll().map { vendor ->
                val balance = app.vendorRepository.balancePaise(vendor.id)
                VendorCard(vendor, FinanceUi.formatPaise(balance, symbol))
            }
            if (_binding == null) return@launch
            adapter.submitList(cards)
        }
    }

    private fun showVendorDialog(existing: Vendor?) {
        val form = DialogVendorFormBinding.inflate(layoutInflater)
        existing?.let {
            form.editVendorName.setText(it.name)
            form.editVendorPhone.setText(it.phone)
            form.editVendorAddress.setText(it.address)
            form.editVendorEmail.setText(it.email)
            form.editVendorNotes.setText(it.notes)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.add_vendor else R.string.edit_vendor)
            .setView(form.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val app = requireActivity().application as VikrSaathiApp
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        app.vendorRepository.save(
                            Vendor(
                                id = existing?.id ?: 0,
                                name = form.editVendorName.text?.toString().orEmpty(),
                                phone = form.editVendorPhone.text?.toString().orEmpty(),
                                address = form.editVendorAddress.text?.toString().orEmpty(),
                                email = form.editVendorEmail.text?.toString().orEmpty(),
                                notes = form.editVendorNotes.text?.toString().orEmpty(),
                                archived = existing?.archived ?: false,
                                createdAt = existing?.createdAt ?: System.currentTimeMillis()
                            )
                        )
                    }.onFailure { error ->
                        android.widget.Toast.makeText(
                            requireContext(),
                            FinanceUi.message(requireContext(), error),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    reload()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmArchive(vendor: Vendor) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.archive_vendor)
            .setMessage(vendor.name)
            .setPositiveButton(R.string.archive_vendor) { _, _ ->
                val app = requireActivity().application as VikrSaathiApp
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { app.vendorRepository.deleteIfNoHistory(vendor) }
                        .onFailure {
                            app.vendorRepository.archive(vendor.id)
                            android.widget.Toast.makeText(
                                requireContext(),
                                FinanceUi.message(requireContext(), it),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    reload()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
