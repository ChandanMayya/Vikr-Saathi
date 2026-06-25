package com.loctell.vikrsaathi.ui.bills

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.loctell.vikrsaathi.R
import com.loctell.vikrsaathi.VikrSaathiApp
import com.loctell.vikrsaathi.data.model.BillWithDetails
import com.loctell.vikrsaathi.databinding.FragmentBillsHistoryBinding
import com.loctell.vikrsaathi.ui.bill.BillFragment
import com.loctell.vikrsaathi.util.BillsFilterHelper
import com.loctell.vikrsaathi.util.BillsHistoryPreferences
import com.loctell.vikrsaathi.util.FileShareHelper
import com.loctell.vikrsaathi.util.SalesReportFilter
import com.loctell.vikrsaathi.util.ViewModelFactory
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BillsHistoryFragment : Fragment() {

    private var _binding: FragmentBillsHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillsHistoryViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var adapter: BillHistoryAdapter
    private lateinit var preferences: BillsHistoryPreferences
    private var allBills: List<BillWithDetails> = emptyList()
    private var dateFrom: Long? = null
    private var dateTo: Long? = null
    private var suppressDrawerListeners = false
    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillsHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as VikrSaathiApp
        preferences = app.billsHistoryPreferences

        adapter = BillHistoryAdapter(
            currencySymbol = app.settingsRepository.currencySymbol,
            onOpen = { bill ->
                findNavController().navigate(
                    R.id.action_bills_history_to_bill,
                    bundleOf(
                        BillFragment.ARG_BILL_ID to bill.bill.id,
                        BillFragment.ARG_READ_ONLY to true
                    )
                )
            },
            onDuplicate = { bill ->
                viewModel.duplicateBill(bill.bill.id) { newId ->
                    findNavController().navigate(
                        R.id.action_bills_history_to_bill,
                        bundleOf(BillFragment.ARG_BILL_ID to newId)
                    )
                }
            },
            onDelete = { bill -> confirmDelete(bill) }
        )
        binding.recyclerBills.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerBills.adapter = adapter

        setupToolbarMenu()
        setupDrawer()
        applyFilterVisibility()
        updateDateButtonLabels()

        viewModel.allBills.observe(viewLifecycleOwner) { bills ->
            allBills = bills
            applyFilters()
        }

        binding.editSearchBills.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilters()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.editCounterFrom.addTextChangedListener(simpleFilterWatcher())
        binding.editCounterTo.addTextChangedListener(simpleFilterWatcher())

        binding.buttonDateFrom.setOnClickListener { showDatePicker(isFrom = true) }
        binding.buttonDateTo.setOnClickListener { showDatePicker(isFrom = false) }

        binding.buttonClearFilters.setOnClickListener { clearActiveFilterValues() }

        binding.historyDrawer.buttonDownloadPdfReport.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            downloadPdfReport()
        }
        binding.historyDrawer.buttonDownloadExcelReport.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            downloadExcelReport()
        }
        binding.historyDrawer.buttonUploadExcelBackup.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            findNavController().navigate(R.id.action_bills_history_to_excel_upload)
        }
    }

    private fun setupToolbarMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_bills_history, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.action_bills_history_options) {
                    syncDrawerToggles()
                    binding.drawerLayout.openDrawer(GravityCompat.END)
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupDrawer() {
        binding.historyDrawer.switchSearchFilter.setOnCheckedChangeListener { _, checked ->
            if (suppressDrawerListeners) return@setOnCheckedChangeListener
            preferences.searchFilterEnabled = checked
            if (!checked) binding.editSearchBills.text = null
            applyFilterVisibility()
            applyFilters()
        }
        binding.historyDrawer.switchDateRangeFilter.setOnCheckedChangeListener { _, checked ->
            if (suppressDrawerListeners) return@setOnCheckedChangeListener
            preferences.dateRangeFilterEnabled = checked
            if (!checked) {
                dateFrom = null
                dateTo = null
                updateDateButtonLabels()
            }
            applyFilterVisibility()
            applyFilters()
        }
        binding.historyDrawer.switchCounterRangeFilter.setOnCheckedChangeListener { _, checked ->
            if (suppressDrawerListeners) return@setOnCheckedChangeListener
            preferences.counterRangeFilterEnabled = checked
            if (!checked) {
                binding.editCounterFrom.text = null
                binding.editCounterTo.text = null
            }
            applyFilterVisibility()
            applyFilters()
        }
    }

    private fun syncDrawerToggles() {
        suppressDrawerListeners = true
        binding.historyDrawer.switchSearchFilter.isChecked = preferences.searchFilterEnabled
        binding.historyDrawer.switchDateRangeFilter.isChecked = preferences.dateRangeFilterEnabled
        binding.historyDrawer.switchCounterRangeFilter.isChecked = preferences.counterRangeFilterEnabled
        suppressDrawerListeners = false
    }

    private fun applyFilterVisibility() {
        binding.layoutSearchFilter.visibility =
            if (preferences.searchFilterEnabled) View.VISIBLE else View.GONE
        binding.layoutDateRangeFilter.visibility =
            if (preferences.dateRangeFilterEnabled) View.VISIBLE else View.GONE
        binding.layoutCounterRangeFilter.visibility =
            if (preferences.counterRangeFilterEnabled) View.VISIBLE else View.GONE

        val anyFilterEnabled = preferences.searchFilterEnabled ||
            preferences.dateRangeFilterEnabled ||
            preferences.counterRangeFilterEnabled
        binding.cardFilters.visibility =
            if (anyFilterEnabled) View.VISIBLE else View.GONE
    }

    private fun clearActiveFilterValues() {
        if (preferences.searchFilterEnabled) {
            binding.editSearchBills.text = null
        }
        if (preferences.dateRangeFilterEnabled) {
            dateFrom = null
            dateTo = null
            updateDateButtonLabels()
        }
        if (preferences.counterRangeFilterEnabled) {
            binding.editCounterFrom.text = null
            binding.editCounterTo.text = null
        }
        applyFilters()
    }

    private fun simpleFilterWatcher() = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilters()
        override fun afterTextChanged(s: Editable?) = Unit
    }

    private fun showDatePicker(isFrom: Boolean) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (isFrom) R.string.filter_date_from else R.string.filter_date_to)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            if (isFrom) {
                dateFrom = startOfDay(selection)
            } else {
                dateTo = endOfDay(selection)
            }
            updateDateButtonLabels()
            applyFilters()
        }
        picker.show(parentFragmentManager, if (isFrom) "date_from" else "date_to")
    }

    private fun updateDateButtonLabels() {
        binding.buttonDateFrom.text = dateFrom?.let { dateFormat.format(Date(it)) }
            ?: getString(R.string.filter_date_from)
        binding.buttonDateTo.text = dateTo?.let { dateFormat.format(Date(it)) }
            ?: getString(R.string.filter_date_to)
    }

    private fun applyFilters() {
        adapter.submitList(getFilteredBills())
    }

    private fun buildCurrentFilter(): SalesReportFilter {
        return SalesReportFilter(
            searchEnabled = preferences.searchFilterEnabled,
            searchQuery = binding.editSearchBills.text?.toString().orEmpty(),
            dateRangeEnabled = preferences.dateRangeFilterEnabled,
            dateFrom = dateFrom,
            dateTo = dateTo,
            counterRangeEnabled = preferences.counterRangeFilterEnabled,
            counterFrom = binding.editCounterFrom.text?.toString()?.trim()?.toIntOrNull(),
            counterTo = binding.editCounterTo.text?.toString()?.trim()?.toIntOrNull()
        )
    }

    private fun getFilteredBills(): List<BillWithDetails> {
        return BillsFilterHelper.apply(allBills, buildCurrentFilter())
    }

    private fun downloadPdfReport() {
        viewModel.exportPdfReport(requireContext(), allBills, buildCurrentFilter()) { file ->
            if (file == null) {
                android.widget.Toast.makeText(requireContext(), R.string.export_no_data, android.widget.Toast.LENGTH_SHORT).show()
                return@exportPdfReport
            }
            showExportDialog(file, "application/pdf", R.string.export_pdf_success)
        }
    }

    private fun downloadExcelReport() {
        viewModel.exportExcelReport(requireContext(), allBills, buildCurrentFilter()) { file ->
            if (file == null) {
                android.widget.Toast.makeText(requireContext(), R.string.export_no_data, android.widget.Toast.LENGTH_SHORT).show()
                return@exportExcelReport
            }
            showExportDialog(
                file,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                R.string.export_excel_success
            )
        }
    }

    private fun showExportDialog(file: java.io.File, mimeType: String, messageRes: Int) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bills_history)
            .setMessage(getString(messageRes))
            .setPositiveButton(R.string.open_file) { _, _ ->
                try {
                    FileShareHelper.openFile(requireContext(), file, mimeType)
                } catch (_: Exception) {
                    android.widget.Toast.makeText(requireContext(), file.absolutePath, android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.share_file) { _, _ ->
                FileShareHelper.shareFile(requireContext(), file, mimeType, getString(messageRes))
            }
            .show()
    }

    private fun startOfDay(utcMillis: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = utcMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun endOfDay(utcMillis: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = utcMillis
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    private fun confirmDelete(bill: BillWithDetails) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_bill)
            .setMessage(R.string.delete_bill_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteBill(bill.bill)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
