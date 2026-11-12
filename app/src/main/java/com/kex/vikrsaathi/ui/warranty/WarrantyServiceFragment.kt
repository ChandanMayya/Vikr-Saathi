package com.kex.vikrsaathi.ui.warranty

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.model.WarrantySaleLine
import com.kex.vikrsaathi.databinding.FragmentWarrantyServiceBinding
import com.kex.vikrsaathi.databinding.ItemWarrantySaleBinding
import com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.domain.bill.WarrantySlip
import com.kex.vikrsaathi.util.ViewModelFactory

class WarrantyServiceFragment : Fragment() {
    private var _binding: FragmentWarrantyServiceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WarrantyServiceViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }
    private var query = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWarrantyServiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = SaleAdapter { line -> onSaleSelected(line) }
        binding.recyclerWarrantySales.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerWarrantySales.adapter = adapter
        binding.buttonViewClaims.setOnClickListener {
            findNavController().navigate(R.id.action_warranty_to_claims)
        }
        binding.editWarrantySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                binding.textWarrantyBlocked.isVisible = false
                viewModel.search(query)
            }
        })
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressWarrantySearch.isVisible = loading
        }
        viewModel.lines.observe(viewLifecycleOwner) { lines ->
            adapter.submit(lines)
            val empty = lines.isEmpty() && viewModel.loading.value != true
            binding.textWarrantyEmpty.isVisible = empty
            binding.textWarrantyEmpty.setText(
                if (query.isBlank()) R.string.warranty_sales_empty else R.string.warranty_sales_none
            )
        }
        viewModel.search("")
    }

    private fun onSaleSelected(line: WarrantySaleLine) {
        val coverage = WarrantyClaimPolicy.coverage(
            line.snapshot(),
            WarrantyClaimPolicy.today(System.currentTimeMillis())
        )
        val blocked = WarrantyClaimPolicy.blockedMessage(coverage)
        if (blocked != null) {
            binding.textWarrantyBlocked.text = blocked
            binding.textWarrantyBlocked.isVisible = true
            return
        }
        binding.textWarrantyBlocked.isVisible = false
        findNavController().navigate(
            R.id.action_warranty_to_claim,
            bundleOf(WarrantyClaimFragment.ARG_BILL_ITEM_ID to line.billItemId)
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private class SaleAdapter(
        private val onClick: (WarrantySaleLine) -> Unit
    ) : RecyclerView.Adapter<SaleAdapter.Holder>() {
        private val items = mutableListOf<WarrantySaleLine>()

        fun submit(lines: List<WarrantySaleLine>) {
            items.clear()
            items.addAll(lines)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemWarrantySaleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(binding, onClick)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        class Holder(
            private val binding: ItemWarrantySaleBinding,
            private val onClick: (WarrantySaleLine) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(line: WarrantySaleLine) {
                val snapshot = line.snapshot()
                val coverage = WarrantyClaimPolicy.coverage(
                    snapshot,
                    WarrantyClaimPolicy.today(System.currentTimeMillis())
                )
                binding.textSaleItem.text = line.itemName
                val customer = line.customerName?.trim().orEmpty()
                binding.textSaleCustomer.isVisible = customer.isNotEmpty()
                binding.textSaleCustomer.text = customer
                binding.textSaleBill.text = "${line.billNumber} · ${WarrantySlip.formatWhen(line.saleDateMillis)}"
                val warranty = snapshot?.let { snap ->
                    listOfNotNull(
                        WarrantyPolicy.extraLine(snap),
                        WarrantyPolicy.validUntil(snap)
                    ).joinToString("\n")
                }.orEmpty()
                binding.textSaleWarranty.isVisible = warranty.isNotBlank()
                binding.textSaleWarranty.text = warranty
                binding.textSaleState.text = WarrantyClaimPolicy.stateLabel(coverage)
                val color = when (coverage) {
                    WarrantyClaimPolicy.Coverage.ACTIVE -> R.color.balance_receivable
                    WarrantyClaimPolicy.Coverage.EXPIRED -> R.color.balance_payable
                    WarrantyClaimPolicy.Coverage.NONE -> R.color.text_secondary_dark
                }
                binding.textSaleState.setTextColor(binding.root.context.getColor(color))
                binding.root.setOnClickListener { onClick(line) }
            }
        }
    }
}
