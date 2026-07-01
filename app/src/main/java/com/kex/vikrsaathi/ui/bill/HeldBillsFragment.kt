package com.kex.vikrsaathi.ui.bill

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentHeldBillsBinding
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.ViewModelFactory

class HeldBillsFragment : Fragment() {

    private var _binding: FragmentHeldBillsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HeldBillsViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var adapter: HeldBillAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHeldBillsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.HELD_BILLS)
        val app = requireActivity().application as VikrSaathiApp

        adapter = HeldBillAdapter(
            currencySymbol = app.settingsRepository.currencySymbol,
            onResume = { held ->
                findNavController().navigate(
                    R.id.action_held_bills_to_bill,
                    bundleOf(BillFragment.ARG_HELD_DRAFT_ID to held.id)
                )
            },
            onDelete = { held -> confirmDelete(held.id) }
        )
        binding.recyclerHeldBills.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHeldBills.adapter = adapter

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.heldBills.observe(viewLifecycleOwner) { bills ->
            adapter.submitList(bills)
            val isEmpty = bills.isEmpty() && viewModel.loading.value != true
            binding.textEmptyHeldBills.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerHeldBills.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun confirmDelete(draftId: Long) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_held_bill_title)
            .setMessage(R.string.delete_held_bill_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteHeldBill(draftId) {
                    Toast.makeText(requireContext(), R.string.held_bill_deleted, Toast.LENGTH_SHORT).show()
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
