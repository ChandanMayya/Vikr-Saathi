package com.kex.vikrsaathi.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentDashboardBinding
import com.kex.vikrsaathi.domain.dashboard.DashboardPeriod
import com.kex.vikrsaathi.domain.dashboard.DashboardPeriods
import com.kex.vikrsaathi.domain.dashboard.GrossProfitCalculator
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.util.PriceCalculator
import com.kex.vikrsaathi.util.ViewModelFactory
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private val lowStockAdapter = LowStockAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        installHelpMenu(HelpScreen.DASHBOARD)

        val app = requireActivity().application as VikrSaathiApp
        updateToolbarTitle(app.settingsRepository.shopName)

        binding.recyclerLowStock.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLowStock.adapter = lowStockAdapter

        viewModel.shopName.observe(viewLifecycleOwner, ::updateToolbarTitle)

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.skeletonLoader.root.isVisible = loading
            binding.layoutStats.isVisible = !loading
            binding.textQuickActions.isVisible = !loading
            binding.dashboardGrid.isVisible = !loading
        }

        binding.togglePeriod.check(
            when (viewModel.selectedPeriod) {
                DashboardPeriod.WEEK -> R.id.buttonPeriodWeek
                DashboardPeriod.MONTH -> R.id.buttonPeriodMonth
                DashboardPeriod.TODAY -> R.id.buttonPeriodToday
            }
        )
        binding.togglePeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val period = when (checkedId) {
                R.id.buttonPeriodWeek -> DashboardPeriod.WEEK
                R.id.buttonPeriodMonth -> DashboardPeriod.MONTH
                else -> DashboardPeriod.TODAY
            }
            viewModel.setPeriod(period)
        }
        binding.cardPeriodSales.setOnClickListener { openBillsForPeriod() }
        binding.topItemsChart.onItemClick = { item -> openBillsForPeriod(item.name) }
        binding.buttonProfitInfo.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dashboard_gross_profit)
                .setMessage(R.string.dashboard_profit_help)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        lowStockAdapter.onClick = { row ->
            findNavController().navigate(
                R.id.action_dashboard_to_stock,
                bundleOf("focusItemId" to row.itemId, "focusItemName" to row.name)
            )
        }

        viewModel.periodStats.observe(viewLifecycleOwner) { stats ->
            if (stats == null) return@observe
            val symbol = viewModel.currencySymbol.value.orEmpty().ifBlank { "₹" }
            binding.textPeriodRange.text = DashboardPeriods.formatRange(stats.window)
            binding.textTodaySalesAmount.text = PriceCalculator.formatAmount(stats.totalSales, symbol)
            binding.textTodayBillCount.text = resources.getQuantityString(
                R.plurals.dashboard_bill_count,
                stats.billCount,
                stats.billCount
            )
            val average = if (stats.billCount == 0) 0.0 else stats.totalSales / stats.billCount
            binding.textAverageBill.text = getString(
                R.string.dashboard_avg_bill,
                PriceCalculator.formatAmount(average, symbol)
            )
            renderComparison(stats, symbol)
            renderProfit(stats, symbol)
            val hasTopItems = stats.topItems.isNotEmpty()
            binding.topItemsChart.isVisible = hasTopItems
            binding.textTopItemsEmpty.isVisible = !hasTopItems
            if (hasTopItems) binding.topItemsChart.setItems(stats.topItems)
        }

        viewModel.customerDuesPaise.observe(viewLifecycleOwner) { paise ->
            val symbol = viewModel.currencySymbol.value.orEmpty().ifBlank { "₹" }
            binding.textCustomerDues.text = getString(
                R.string.dashboard_outstanding,
                com.kex.vikrsaathi.ui.finance.FinanceUi.formatBalance(paise, symbol)
            )
            binding.textCustomerDues.setTextColor(
                requireContext().getColor(
                    if (paise > 0L) R.color.balance_receivable else R.color.text_secondary_dark
                )
            )
        }
        viewModel.vendorPayablesPaise.observe(viewLifecycleOwner) { paise ->
            val symbol = viewModel.currencySymbol.value.orEmpty().ifBlank { "₹" }
            binding.textVendorPayables.text = getString(
                R.string.dashboard_payables,
                com.kex.vikrsaathi.ui.finance.FinanceUi.formatBalance(paise, symbol)
            )
            binding.textVendorPayables.setTextColor(
                requireContext().getColor(
                    if (paise > 0L) R.color.balance_payable else R.color.text_secondary_dark
                )
            )
        }

        viewModel.orderAttentionCount.observe(viewLifecycleOwner) { count ->
            binding.textOrdersAttention.isVisible = count > 0
            if (count > 0) {
                binding.textOrdersAttention.text = getString(R.string.orders_need_attention, count)
            }
        }

        viewModel.lowStock.observe(viewLifecycleOwner) { summary ->
            if (summary.count == 0) {
                binding.textLowStockSummary.text = getString(R.string.low_stock_empty)
                binding.recyclerLowStock.isVisible = false
                binding.textLowStockViewAll.isVisible = false
                lowStockAdapter.submitList(emptyList())
            } else {
                binding.textLowStockSummary.text =
                    getString(R.string.low_stock_count, summary.count)
                binding.recyclerLowStock.isVisible = true
                binding.textLowStockViewAll.isVisible = summary.count > summary.items.size
                lowStockAdapter.submitList(summary.items)
            }
        }

        binding.cardNewBill.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_bill)
        }
        binding.cardCustomers.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_customers)
        }
        binding.cardItems.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_items)
        }
        binding.cardBillsHistory.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_bills_history)
        }
        binding.cardVendors.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_vendors)
        }
        binding.cardStock.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_stock)
        }
        binding.cardAnalysis.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_analysis)
        }
        binding.cardOrders.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_orders)
        }
        binding.cardLowStock.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_stock)
        }
        binding.textLowStockViewAll.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_stock)
        }
        binding.cardSettings.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_settings)
        }
        binding.textCustomerDues.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_outstanding)
        }
        binding.textVendorPayables.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_vendors)
        }
        applyDashboardLayout()
    }

    override fun onResume() {
        super.onResume()
        applyDashboardLayout()
        viewModel.refresh()
    }

    private fun applyDashboardLayout() {
        val settings = (requireActivity().application as VikrSaathiApp).settingsRepository
        val vendorsEnabled = settings.vendorManagementEnabled
        binding.cardVendors.isVisible = vendorsEnabled
        binding.rowMasters.columnCount = if (vendorsEnabled) 3 else 2
        val cardHeight = if (vendorsEnabled) dp(96) else dp(92)
        val iconSize = if (vendorsEnabled) dp(28) else dp(36)
        listOf(binding.cardItems, binding.cardCustomers, binding.cardVendors).forEach { card ->
            card.layoutParams = card.layoutParams.apply { height = cardHeight }
        }
        listOf(binding.iconItems, binding.iconCustomers, binding.iconVendors).forEach { icon ->
            icon.layoutParams = icon.layoutParams.apply {
                width = iconSize
                height = iconSize
            }
        }
        val ordersEnabled = settings.orderManagementEnabled
        binding.textCustomerDues.isVisible = settings.customerBalanceEnabled
        binding.textVendorPayables.isVisible = vendorsEnabled
        binding.cardOrders.isVisible = ordersEnabled
        binding.rowTools.columnCount = if (ordersEnabled) 3 else 2
        val toolHeight = if (ordersEnabled) dp(108) else dp(92)
        val toolIcon = if (ordersEnabled) dp(28) else dp(36)
        listOf(binding.cardStock, binding.cardAnalysis, binding.cardOrders).forEach { card ->
            card.layoutParams = card.layoutParams.apply { height = toolHeight }
        }
        listOf(binding.iconStock, binding.iconAnalysis, binding.iconOrders).forEach { icon ->
            icon.layoutParams = icon.layoutParams.apply {
                width = toolIcon
                height = toolIcon
            }
        }
    }

    private fun openBillsForPeriod(itemName: String = "") {
        val stats = viewModel.periodStats.value ?: return
        findNavController().navigate(
            R.id.action_dashboard_to_bills_history,
            bundleOf(
                "rangeStart" to stats.window.startInclusive,
                "rangeEnd" to stats.window.endExclusive,
                "itemName" to itemName
            )
        )
    }

    private fun renderComparison(
        stats: com.kex.vikrsaathi.data.model.DashboardPeriodStats,
        symbol: String
    ) {
        val comparison = stats.comparison
        val versus = when (stats.period) {
            DashboardPeriod.TODAY -> getString(R.string.dashboard_vs_yesterday)
            DashboardPeriod.WEEK -> getString(R.string.dashboard_vs_previous_week)
            DashboardPeriod.MONTH -> getString(R.string.dashboard_vs_previous_period)
        }
        val text: String
        val color: Int
        when {
            comparison.previousSales == 0.0 && comparison.currentSales == 0.0 -> {
                text = getString(R.string.dashboard_no_sales_either, versus)
                color = R.color.text_secondary_dark
            }
            comparison.salesPercent == null -> {
                text = getString(R.string.dashboard_no_prior_sales, versus)
                color = R.color.text_secondary_dark
            }
            else -> {
                val arrow = when {
                    comparison.salesDelta > 0.0 -> "↑"
                    comparison.salesDelta < 0.0 -> "↓"
                    else -> "→"
                }
                val percent = kotlin.math.abs(comparison.salesPercent)
                val percentText = if (percent % 1.0 < 0.05 || percent % 1.0 > 0.95) {
                    String.format(java.util.Locale.getDefault(), "%.0f%%", percent)
                } else {
                    String.format(java.util.Locale.getDefault(), "%.1f%%", percent)
                }
                text = getString(
                    R.string.dashboard_comparison,
                    arrow,
                    PriceCalculator.formatAmount(kotlin.math.abs(comparison.salesDelta), symbol),
                    percentText,
                    versus
                )
                color = when {
                    comparison.salesDelta > 0.0 -> R.color.balance_receivable
                    comparison.salesDelta < 0.0 -> R.color.balance_payable
                    else -> R.color.text_secondary_dark
                }
            }
        }
        binding.textSalesComparison.text = text
        binding.textSalesComparison.setTextColor(requireContext().getColor(color))
    }

    private fun renderProfit(
        stats: com.kex.vikrsaathi.data.model.DashboardPeriodStats,
        symbol: String
    ) {
        val profit = stats.profit
        if (profit.coverage != GrossProfitCalculator.Coverage.COMPLETE || profit.profitPaise == null) {
            binding.textGrossProfit.text = getString(R.string.dashboard_cost_incomplete)
            binding.textProfitDetail.text = getString(
                R.string.dashboard_cost_incomplete_detail,
                profit.uncoveredUnits
            )
            return
        }
        val rupees = Money.paiseToRupees(profit.profitPaise)
        binding.textGrossProfit.text = PriceCalculator.formatAmount(rupees, symbol)
        val margin = profit.marginPercent
        binding.textProfitDetail.text = if (margin == null) {
            getString(R.string.dashboard_profit_no_margin)
        } else {
            getString(
                R.string.dashboard_profit_margin,
                String.format(java.util.Locale.getDefault(), "%.1f", margin)
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateToolbarTitle(name: String) {
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = name
    }
}
