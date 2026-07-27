package com.kex.vikrsaathi.ui.analysis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentAnalysisBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.AnalyticsDateRange
import com.kex.vikrsaathi.util.FileShareHelper
import com.kex.vikrsaathi.util.PriceCalculator
import com.kex.vikrsaathi.util.ViewModelFactory
import java.util.Calendar
import java.util.Locale

class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AnalysisViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private var suppressChipChanges = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        installHelpMenu(HelpScreen.ANALYSIS)
        setupOptionsMenu()
        setupDateRangeChips()

        binding.buttonExportPdf.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            exportPdf()
        }
        binding.buttonExportExcel.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            exportExcel()
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressLoading.isVisible = loading
            binding.layoutContent.isVisible = !loading
        }

        viewModel.dashboard.observe(viewLifecycleOwner) { dashboard ->
            val symbol = viewModel.currencySymbol.value.orEmpty().ifBlank { "₹" }
            binding.textTotalSales.text =
                PriceCalculator.formatAmount(dashboard.summary.totalSales, symbol)
            binding.textBillCount.text = dashboard.summary.billCount.toString()
            binding.textAvgBill.text =
                PriceCalculator.formatAmount(dashboard.summary.avgBillValue, symbol)

            val hasSales = dashboard.summary.billCount > 0
            binding.salesTrendChart.isVisible = hasSales
            binding.textSalesTrendEmpty.isVisible = !hasSales
            if (hasSales) {
                binding.salesTrendChart.setPoints(dashboard.salesTrend)
            }

            bindRankedChart(
                chart = binding.topProductsChart,
                emptyView = binding.textTopProductsEmpty,
                items = dashboard.topProducts.map { it.itemName },
                values = dashboard.topProducts.map { it.totalRevenue },
                symbol = symbol
            )

            bindRankedChart(
                chart = binding.topCustomersChart,
                emptyView = binding.textTopCustomersEmpty,
                items = dashboard.topCustomers.map { it.customerName },
                values = dashboard.topCustomers.map { it.totalSpend },
                symbol = symbol
            )

            binding.textDiscountGiven.text =
                PriceCalculator.formatAmount(dashboard.discountImpact.discountGiven, symbol)
            binding.textAvgDiscount.text =
                String.format(Locale.getDefault(), "%.1f%%", dashboard.discountImpact.avgDiscountPercent)
            binding.textDiscountDetail.text = getString(
                R.string.analysis_discount_detail,
                PriceCalculator.formatAmount(dashboard.discountImpact.grossAtMrp, symbol),
                PriceCalculator.formatAmount(dashboard.discountImpact.netRevenue, symbol)
            )

            binding.textInventoryTotal.text =
                PriceCalculator.formatAmount(dashboard.inventoryValue.totalValue, symbol)
            binding.textInventoryMeta.text = getString(
                R.string.analysis_inventory_meta,
                dashboard.inventoryValue.itemCount
            )
            bindRankedChart(
                chart = binding.inventoryValueChart,
                emptyView = binding.textInventoryEmpty,
                items = dashboard.inventoryValue.topByValue.map { it.itemName },
                values = dashboard.inventoryValue.topByValue.map { it.stockValue },
                symbol = symbol
            )

            bindSlowMoversChart(dashboard.slowMovers.map { it.itemName to it.soldQty })

            val hasPeakHours = dashboard.peakHours.any { it.billCount > 0 }
            binding.peakHoursChart.isVisible = hasPeakHours
            binding.textPeakHoursEmpty.isVisible = !hasPeakHours
            if (hasPeakHours) {
                binding.peakHoursChart.setData(
                    dashboard.peakHours.map { formatHourLabel(it.hour) },
                    dashboard.peakHours.map { it.billCount.toDouble() }
                )
            }

            val hasDayOfWeek = dashboard.salesByDayOfWeek.any { it.billCount > 0 }
            binding.dayOfWeekChart.isVisible = hasDayOfWeek
            binding.textDayOfWeekEmpty.isVisible = !hasDayOfWeek
            if (hasDayOfWeek) {
                binding.dayOfWeekChart.setData(
                    dashboard.salesByDayOfWeek.map { formatDayOfWeekLabel(it.dayOfWeek) },
                    dashboard.salesByDayOfWeek.map { it.billCount.toDouble() }
                )
            }

            val mix = dashboard.customerMix
            val hasCustomerMix = mix.totalBills > 0
            binding.customerMixChart.isVisible = hasCustomerMix
            binding.textCustomerMixEmpty.isVisible = !hasCustomerMix
            binding.textCustomerMixDetail.isVisible = hasCustomerMix
            if (hasCustomerMix) {
                bindRankedChart(
                    chart = binding.customerMixChart,
                    emptyView = binding.textCustomerMixEmpty,
                    items = listOf(
                        getString(R.string.analysis_walk_in),
                        getString(R.string.analysis_registered)
                    ),
                    values = listOf(mix.walkInSales, mix.registeredSales),
                    symbol = symbol
                )
                binding.textCustomerMixDetail.text = getString(
                    R.string.analysis_customer_mix_detail,
                    mix.walkInBills,
                    mix.registeredBills
                )
            }

            binding.textHeldBillsSummary.text = getString(
                R.string.analysis_held_bills_summary,
                dashboard.heldBills.finalizedInPeriod,
                dashboard.heldBills.heldInPeriod
            )
            binding.textHeldBillsDetail.text = getString(
                R.string.analysis_held_bills_detail,
                String.format(Locale.getDefault(), "%.0f%%", dashboard.heldBills.completionRatePercent),
                dashboard.heldBills.activeHeldNow
            )
        }

        viewModel.dateRange.observe(viewLifecycleOwner, ::syncDateRangeChips)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun bindRankedChart(
        chart: RankedBarChartView,
        emptyView: View,
        items: List<String>,
        values: List<Double>,
        symbol: String
    ) {
        val hasItems = items.isNotEmpty()
        chart.isVisible = hasItems
        emptyView.isVisible = !hasItems
        if (!hasItems) return
        val maxValue = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        chart.setItems(
            items.mapIndexed { index, label ->
                RankedBarItem(
                    label = label,
                    barFraction = (values[index] / maxValue).toFloat(),
                    valueLabel = PriceCalculator.formatAmount(values[index], symbol)
                )
            }
        )
    }

    private fun bindSlowMoversChart(rows: List<Pair<String, Int>>) {
        val hasItems = rows.isNotEmpty()
        binding.slowMoversChart.isVisible = hasItems
        binding.textSlowMoversEmpty.isVisible = !hasItems
        if (!hasItems) return
        val maxSold = rows.maxOf { it.second }.coerceAtLeast(1)
        binding.slowMoversChart.setItems(
            rows.map { (name, sold) ->
                RankedBarItem(
                    label = name,
                    barFraction = sold.toFloat() / maxSold,
                    valueLabel = sold.toString()
                )
            }
        )
    }

    private fun formatHourLabel(hour: Int): String {
        return when {
            hour == 0 -> "12a"
            hour < 12 -> "${hour}a"
            hour == 12 -> "12p"
            else -> "${hour - 12}p"
        }
    }

    private fun formatDayOfWeekLabel(dayOfWeek: Int): String {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
        }.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: "?"
    }

    private fun setupOptionsMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_analysis_options, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.action_analysis_options) {
                    binding.drawerLayout.openDrawer(GravityCompat.END)
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun exportPdf() {
        val rangeLabel = currentRangeLabel()
        viewModel.exportPdf(requireContext(), rangeLabel) { file ->
            if (file == null) {
                Toast.makeText(requireContext(), R.string.analysis_export_no_data, Toast.LENGTH_SHORT).show()
                return@exportPdf
            }
            showExportDialog(file, "application/pdf", R.string.export_pdf_success)
        }
    }

    private fun exportExcel() {
        val rangeLabel = currentRangeLabel()
        viewModel.exportExcel(requireContext(), rangeLabel) { file ->
            if (file == null) {
                Toast.makeText(requireContext(), R.string.analysis_export_no_data, Toast.LENGTH_SHORT).show()
                return@exportExcel
            }
            showExportDialog(
                file,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                R.string.export_excel_success
            )
        }
    }

    private fun currentRangeLabel(): String {
        val range = viewModel.dateRange.value ?: AnalyticsDateRange.LAST_7_DAYS
        return getString(range.labelRes())
    }

    private fun showExportDialog(file: java.io.File, mimeType: String, messageRes: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.analysis)
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

    private fun setupDateRangeChips() {
        binding.chipGroupDateRange.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressChipChanges || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val range = when (checkedIds.first()) {
                R.id.chipToday -> AnalyticsDateRange.TODAY
                R.id.chipLast30Days -> AnalyticsDateRange.LAST_30_DAYS
                R.id.chipThisMonth -> AnalyticsDateRange.THIS_MONTH
                else -> AnalyticsDateRange.LAST_7_DAYS
            }
            viewModel.setDateRange(range)
        }
    }

    private fun syncDateRangeChips(range: AnalyticsDateRange) {
        val chipId = when (range) {
            AnalyticsDateRange.TODAY -> R.id.chipToday
            AnalyticsDateRange.LAST_7_DAYS -> R.id.chipLast7Days
            AnalyticsDateRange.LAST_30_DAYS -> R.id.chipLast30Days
            AnalyticsDateRange.THIS_MONTH -> R.id.chipThisMonth
        }
        suppressChipChanges = true
        binding.chipGroupDateRange.check(chipId)
        (binding.chipGroupDateRange.findViewById<Chip>(chipId))?.isChecked = true
        suppressChipChanges = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
