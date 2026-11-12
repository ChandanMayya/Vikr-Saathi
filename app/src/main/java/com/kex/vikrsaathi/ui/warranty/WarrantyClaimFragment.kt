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
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.repository.WarrantyClaimOutcome
import com.kex.vikrsaathi.databinding.FragmentWarrantyClaimBinding
import com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.util.ViewModelFactory

class WarrantyClaimFragment : Fragment() {
    private var _binding: FragmentWarrantyClaimBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WarrantyServiceViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWarrantyClaimBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val billItemId = arguments?.getLong(ARG_BILL_ITEM_ID, 0L) ?: 0L
        viewModel.loadLine(billItemId)
        viewModel.selectedLine.observe(viewLifecycleOwner) { line ->
            if (line == null || line.billItemId != billItemId) {
                showBlocked(getString(R.string.warranty_bill_missing))
                return@observe
            }
            val coverage = WarrantyClaimPolicy.coverage(
                line.snapshot(),
                WarrantyClaimPolicy.today(System.currentTimeMillis())
            )
            val blocked = WarrantyClaimPolicy.blockedMessage(coverage)
            if (blocked != null) {
                showBlocked(blocked)
                return@observe
            }
            binding.textClaimUnavailable.isVisible = false
            binding.cardClaimSummary.isVisible = true
            binding.cardNewClaim.isVisible = true
            binding.textClaimItem.text = line.itemName
            binding.textClaimInvoice.text = line.billNumber
            val until = line.snapshot()?.let { WarrantyPolicy.validUntil(it) }.orEmpty()
            binding.textClaimUntil.isVisible = until.isNotBlank()
            binding.textClaimUntil.text = until
        }
        binding.buttonSaveClaim.setOnClickListener {
            val issue = binding.editClaimIssue.text?.toString().orEmpty()
            if (issue.isBlank()) {
                binding.layoutClaimIssue.error = WarrantyClaimPolicy.ISSUE_REQUIRED
                return@setOnClickListener
            }
            binding.layoutClaimIssue.error = null
            binding.buttonSaveClaim.isEnabled = false
            viewModel.saveClaim(
                billItemId = billItemId,
                issue = issue,
                condition = binding.editClaimCondition.text?.toString().orEmpty(),
                note = binding.editClaimNote.text?.toString().orEmpty()
            ) { outcome ->
                if (_binding == null) return@saveClaim
                binding.buttonSaveClaim.isEnabled = true
                when (outcome) {
                    is WarrantyClaimOutcome.Saved -> findNavController().navigate(
                        R.id.action_warranty_claim_to_detail,
                        bundleOf(WarrantyClaimDetailFragment.ARG_CLAIM_ID to outcome.claimId)
                    )
                    is WarrantyClaimOutcome.NotAllowed -> {
                        if (outcome.message == WarrantyClaimPolicy.ISSUE_REQUIRED) {
                            binding.layoutClaimIssue.error = outcome.message
                        } else {
                            showBlocked(outcome.message)
                        }
                    }
                }
            }
        }
    }

    private fun showBlocked(message: String) {
        binding.textClaimUnavailable.text = message
        binding.textClaimUnavailable.isVisible = true
        binding.cardClaimSummary.isVisible = false
        binding.cardNewClaim.isVisible = false
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_BILL_ITEM_ID = "billItemId"
    }
}
