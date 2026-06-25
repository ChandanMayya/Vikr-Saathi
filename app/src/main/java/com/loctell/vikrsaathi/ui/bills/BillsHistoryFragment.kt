package com.loctell.vikrsaathi.ui.bills

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.loctell.vikrsaathi.R
import com.loctell.vikrsaathi.VikrSaathiApp
import com.loctell.vikrsaathi.data.model.BillWithDetails
import com.loctell.vikrsaathi.databinding.FragmentBillsHistoryBinding
import com.loctell.vikrsaathi.ui.bill.BillFragment
import com.loctell.vikrsaathi.util.ViewModelFactory

class BillsHistoryFragment : Fragment() {

    private var _binding: FragmentBillsHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillsHistoryViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var adapter: BillHistoryAdapter
    private var allBills: List<BillWithDetails> = emptyList()

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
        adapter = BillHistoryAdapter(
            currencySymbol = app.settingsRepository.currencySymbol,
            onOpen = { bill ->
                findNavController().navigate(
                    R.id.action_bills_history_to_bill,
                    bundleOf(BillFragment.ARG_BILL_ID to bill.bill.id)
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

        viewModel.allBills.observe(viewLifecycleOwner) { bills ->
            allBills = bills
            filterBills(binding.editSearchBills.text?.toString().orEmpty())
        }

        binding.editSearchBills.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterBills(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun filterBills(query: String) {
        val filtered = if (query.isBlank()) {
            allBills
        } else {
            allBills.filter {
                it.bill.billNumber.contains(query, ignoreCase = true) ||
                    it.customer?.name?.contains(query, ignoreCase = true) == true
            }
        }
        adapter.submitList(filtered)
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
