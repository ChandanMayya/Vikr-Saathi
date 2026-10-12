package com.kex.vikrsaathi.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentDashboardBinding
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

        viewModel.todayStats.observe(viewLifecycleOwner) { stats ->
            val symbol = viewModel.currencySymbol.value.orEmpty().ifBlank { "₹" }
            binding.textTodaySalesAmount.text =
                PriceCalculator.formatAmount(stats.totalSales, symbol)
            binding.textTodayBillCount.text = if (stats.billCount > 0) {
                getString(R.string.dashboard_bills_today, stats.billCount)
            } else {
                getString(R.string.dashboard_bills_today_none)
            }
            val hasTopItems = stats.topItems.isNotEmpty()
            binding.topItemsChart.isVisible = hasTopItems
            binding.textTopItemsEmpty.isVisible = !hasTopItems
            if (hasTopItems) {
                binding.topItemsChart.setItems(stats.topItems)
            }
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
        binding.textCustomerDues.isVisible = settings.customerBalanceEnabled
        binding.textVendorPayables.isVisible = vendorsEnabled
        binding.cardOrders.isVisible = vendorsEnabled
        binding.rowTools.columnCount = if (vendorsEnabled) 3 else 2
        val toolHeight = if (vendorsEnabled) dp(108) else dp(92)
        val toolIcon = if (vendorsEnabled) dp(28) else dp(36)
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
