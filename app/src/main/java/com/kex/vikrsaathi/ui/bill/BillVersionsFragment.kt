package com.kex.vikrsaathi.ui.bill

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.model.BillVersionDetails
import com.kex.vikrsaathi.databinding.FragmentBillVersionsBinding
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
            }
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
