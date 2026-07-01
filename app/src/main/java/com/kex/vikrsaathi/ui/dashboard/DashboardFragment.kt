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
        binding.cardSettings.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_settings)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateToolbarTitle(name: String) {
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = name
    }
}
