package com.loctell.vikrsaathi.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.loctell.vikrsaathi.R
import com.loctell.vikrsaathi.VikrSaathiApp
import com.loctell.vikrsaathi.databinding.FragmentDashboardBinding
import com.loctell.vikrsaathi.util.ViewModelFactory

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
        val app = requireActivity().application as VikrSaathiApp
        viewModel.refresh(app.settingsRepository)

        viewModel.shopName.observe(viewLifecycleOwner) { name ->
            binding.textShopName.text = name
        }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.skeletonLoader.root.visibility = if (loading) View.VISIBLE else View.GONE
            binding.dashboardGrid.visibility = if (loading) View.GONE else View.VISIBLE
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
