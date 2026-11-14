package com.kex.vikrsaathi.ui.warranty

import android.os.Bundle
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
import com.kex.vikrsaathi.data.entity.WarrantyClaim
import com.kex.vikrsaathi.databinding.FragmentWarrantyClaimsBinding
import com.kex.vikrsaathi.databinding.ItemWarrantyClaimBinding
import com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy
import com.kex.vikrsaathi.domain.bill.WarrantySlip
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.ViewModelFactory

class WarrantyClaimsFragment : Fragment() {
    private var _binding: FragmentWarrantyClaimsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WarrantyServiceViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWarrantyClaimsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        installHelpMenu(HelpScreen.WARRANTY_CLAIMS)
        val adapter = ClaimAdapter { claim ->
            findNavController().navigate(
                R.id.action_warranty_claims_to_detail,
                bundleOf(WarrantyClaimDetailFragment.ARG_CLAIM_ID to claim.id)
            )
        }
        binding.recyclerWarrantyClaims.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerWarrantyClaims.adapter = adapter
        viewModel.claims.observe(viewLifecycleOwner) { claims ->
            adapter.submit(claims)
            binding.textClaimsEmpty.isVisible = claims.isEmpty()
        }
        viewModel.refreshClaims()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private class ClaimAdapter(
        private val onClick: (WarrantyClaim) -> Unit
    ) : RecyclerView.Adapter<ClaimAdapter.Holder>() {
        private val items = mutableListOf<WarrantyClaim>()

        fun submit(claims: List<WarrantyClaim>) {
            items.clear()
            items.addAll(claims)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemWarrantyClaimBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(binding, onClick)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        class Holder(
            private val binding: ItemWarrantyClaimBinding,
            private val onClick: (WarrantyClaim) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(claim: WarrantyClaim) {
                val context = binding.root.context
                binding.textClaimReference.text = claim.reference
                binding.textClaimStatus.text = WarrantyClaimPolicy.label(claim.status)
                val statusColor = when (claim.status) {
                    WarrantyClaimPolicy.RESOLVED -> R.color.balance_receivable
                    WarrantyClaimPolicy.REJECTED -> R.color.balance_payable
                    else -> R.color.text_accent
                }
                binding.textClaimStatus.setTextColor(context.getColor(statusColor))
                binding.textClaimCustomer.text = claim.customerName.ifBlank {
                    context.getString(R.string.return_walk_in)
                }
                binding.textClaimPhone.isVisible = claim.customerPhone.isNotBlank()
                binding.textClaimPhone.text = claim.customerPhone
                binding.textClaimItem.text = claim.itemName
                binding.textClaimInvoice.text = claim.billNumber
                binding.textClaimReceived.text = WarrantySlip.formatWhen(claim.createdAt)
                binding.layoutClaimIssue.isVisible = claim.issue.isNotBlank()
                binding.textClaimIssue.text = claim.issue
                val outcome = claim.providerOutcome
                binding.textClaimOutcome.isVisible = !outcome.isNullOrBlank()
                if (!outcome.isNullOrBlank()) {
                    binding.textClaimOutcome.text = WarrantyClaimPolicy.outcomeLabel(outcome)
                    val outcomeColor = if (outcome == WarrantyClaimPolicy.DECLINED) {
                        R.color.balance_payable
                    } else {
                        R.color.balance_receivable
                    }
                    binding.textClaimOutcome.setTextColor(context.getColor(outcomeColor))
                }
                val returned = claim.handoverEpochDay != null
                binding.textClaimHandover.text = WarrantyClaimPolicy.handoverLabel(returned, claim.status)
                val handoverColor = if (returned) R.color.text_secondary_dark else R.color.text_accent
                binding.textClaimHandover.setTextColor(context.getColor(handoverColor))
                binding.root.setOnClickListener { onClick(claim) }
            }
        }
    }
}
