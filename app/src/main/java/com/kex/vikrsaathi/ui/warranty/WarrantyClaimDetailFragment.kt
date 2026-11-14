package com.kex.vikrsaathi.ui.warranty

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.android.material.datepicker.MaterialDatePicker
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.repository.WarrantyClaimOutcome
import com.kex.vikrsaathi.databinding.FragmentWarrantyClaimDetailBinding
import com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.domain.bill.WarrantySlip
import com.kex.vikrsaathi.ui.bill.BillFragment
import com.kex.vikrsaathi.util.PdfPrintHelper
import com.kex.vikrsaathi.util.ViewModelFactory
import com.kex.vikrsaathi.util.WarrantySlipPdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WarrantyClaimDetailFragment : Fragment() {
    private var _binding: FragmentWarrantyClaimDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WarrantyServiceViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }
    private var claimId: Long = 0L
    private var sentDay: Long = 0L
    private var responseDay: Long = 0L
    private var handoverDay: Long = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWarrantyClaimDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        claimId = arguments?.getLong(ARG_CLAIM_ID, 0L) ?: 0L
        val initialDay = today()
        sentDay = initialDay
        responseDay = initialDay
        handoverDay = initialDay
        val labels = WarrantyClaimPolicy.statuses.map { WarrantyClaimPolicy.label(it) }
        binding.autoClaimStatus.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )
        binding.autoClaimStatus.setOnItemClickListener { _, _, position, _ ->
            showOutcome(WarrantyClaimPolicy.statuses[position])
        }
        val outcomeLabels = WarrantyClaimPolicy.outcomes.map { WarrantyClaimPolicy.outcomeLabel(it) }
        binding.autoProviderOutcome.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, outcomeLabels)
        )
        binding.editServiceSent.setOnClickListener { pickDate(sentDay) { sentDay = it; showDay(binding.editServiceSent, it) } }
        binding.editProviderResponse.setOnClickListener { pickDate(responseDay) { responseDay = it; showDay(binding.editProviderResponse, it) } }
        binding.editHandoverDate.setOnClickListener { pickDate(handoverDay) { handoverDay = it; showDay(binding.editHandoverDate, it) } }
        binding.buttonRecordService.setOnClickListener { recordService() }
        binding.buttonSaveProviderOutcome.setOnClickListener { saveProviderOutcome() }
        binding.buttonMarkReturned.setOnClickListener {
            binding.layoutHandoverForm.isVisible = !binding.layoutHandoverForm.isVisible
        }
        binding.buttonConfirmHandover.setOnClickListener { confirmHandover() }
        viewModel.claimLoaded.observe(viewLifecycleOwner) { renderClaim() }
        viewModel.claim.observe(viewLifecycleOwner) { renderClaim() }
        viewModel.events.observe(viewLifecycleOwner) { events ->
            binding.textStatusHistory.text = events.joinToString("\n") { WarrantySlip.eventLine(it) }
        }
        viewModel.billExists.observe(viewLifecycleOwner) { exists ->
            if (viewModel.claimLoaded.value != true) return@observe
            val claim = viewModel.claim.value
            val present = claim != null && claim.id == claimId
            binding.buttonOpenBill.isVisible = exists && present
            binding.textDetailBillMissing.isVisible = present && !exists
        }
        binding.buttonOpenBill.setOnClickListener {
            val billId = viewModel.claim.value?.billId ?: return@setOnClickListener
            findNavController().navigate(
                R.id.action_warranty_detail_to_bill,
                bundleOf(
                    BillFragment.ARG_BILL_ID to billId,
                    BillFragment.ARG_READ_ONLY to true
                )
            )
        }
        binding.buttonUpdateStatus.setOnClickListener {
            val status = statusCode(binding.autoClaimStatus.text?.toString().orEmpty()) ?: return@setOnClickListener
            val note = binding.editClaimOutcome.text?.toString().orEmpty()
            if (WarrantyClaimPolicy.needsResolution(status) && note.isBlank()) {
                binding.layoutClaimOutcome.error = WarrantyClaimPolicy.RESOLUTION_REQUIRED
                return@setOnClickListener
            }
            binding.layoutClaimOutcome.error = null
            binding.buttonUpdateStatus.isEnabled = false
            viewModel.updateStatus(claimId, status, note) { outcome ->
                if (_binding == null) return@updateStatus
                binding.buttonUpdateStatus.isEnabled = true
                if (outcome is WarrantyClaimOutcome.NotAllowed) {
                    binding.layoutClaimOutcome.error = outcome.message
                }
            }
        }
        binding.buttonPrintSlip.setOnClickListener {
            val claim = viewModel.claim.value ?: return@setOnClickListener
            val shopName = viewModel.shopName
            val appContext = requireContext().applicationContext
            binding.buttonPrintSlip.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val file = withContext(Dispatchers.IO) {
                    WarrantySlipPdfGenerator.generate(appContext, claim, shopName)
                }
                if (_binding == null) return@launch
                binding.buttonPrintSlip.isEnabled = true
                PdfPrintHelper.print(requireContext(), file, claim.reference)
            }
        }
        viewModel.loadClaim(claimId)
    }

    private fun renderClaim() {
        if (_binding == null || viewModel.claimLoaded.value != true) return
        val claim = viewModel.claim.value
        val missing = claim == null || claim.id != claimId
        binding.textDetailMissing.isVisible = missing
        binding.cardClaimDetail.isVisible = !missing
        binding.cardServiceCentre.isVisible = !missing
        binding.cardProviderOutcome.isVisible = !missing
        binding.cardClaimStatus.isVisible = !missing
        binding.buttonPrintSlip.isVisible = !missing
        binding.cardHandover.isVisible = !missing && !claim?.providerOutcome.isNullOrBlank()
        if (claim == null || missing) {
            binding.textDetailMissing.text = getString(R.string.warranty_bill_missing)
            return
        }
            binding.textDetailReference.text = claim.reference
            binding.textDetailStatus.text = WarrantyClaimPolicy.label(claim.status)
            val outcome = claim.providerOutcome
            binding.textDetailProviderOutcome.isVisible = !outcome.isNullOrBlank()
            if (!outcome.isNullOrBlank()) {
                binding.textDetailProviderOutcome.text = WarrantyClaimPolicy.outcomeLabel(outcome)
                val color = if (outcome == WarrantyClaimPolicy.DECLINED) R.color.balance_payable else R.color.balance_receivable
                binding.textDetailProviderOutcome.setTextColor(requireContext().getColor(color))
            }
            binding.textDetailBody.text = WarrantySlip.rows(claim, viewModel.shopName)
                .joinToString("\n") { "${it.label}: ${it.value}" }
            val outcomeRecorded = !outcome.isNullOrBlank()
            binding.layoutClaimStatus.isVisible = !outcomeRecorded
            binding.buttonUpdateStatus.isVisible = !outcomeRecorded
            binding.autoClaimStatus.setText(WarrantyClaimPolicy.label(claim.status), false)
            if (outcomeRecorded) {
                binding.layoutClaimOutcome.isVisible = false
            } else {
                showOutcome(claim.status)
            }
            if (binding.editClaimOutcome.text.isNullOrBlank()) {
                binding.editClaimOutcome.setText(claim.resolutionNote)
            }
            binding.editServiceCentre.setText(claim.serviceCentre)
            binding.editServiceReference.setText(claim.serviceReference)
            sentDay = claim.serviceSentEpochDay ?: today()
            showDay(binding.editServiceSent, sentDay)
            if (!outcome.isNullOrBlank()) {
                binding.autoProviderOutcome.setText(WarrantyClaimPolicy.outcomeLabel(outcome), false)
            }
            binding.editProviderNote.setText(claim.providerOutcomeNote)
            responseDay = claim.providerResponseEpochDay ?: today()
            showDay(binding.editProviderResponse, responseDay)
            val returned = claim.handoverEpochDay
            handoverDay = returned ?: today()
            showDay(binding.editHandoverDate, handoverDay)
            binding.editHandoverRecipient.setText(claim.handoverRecipient)
            binding.editHandoverNote.setText(claim.handoverNote)
            binding.layoutHandoverForm.isVisible = false
            if (returned != null) {
                binding.textHandoverState.text =
                    "${WarrantyClaimPolicy.HANDOVER_RETURNED} · ${WarrantyPolicy.formatDate(returned)}"
                binding.textHandoverState.setTextColor(requireContext().getColor(R.color.text_secondary_dark))
                binding.buttonMarkReturned.setText(R.string.warranty_update_handover)
            } else {
                binding.textHandoverState.setText(R.string.warranty_not_returned)
                binding.textHandoverState.setTextColor(requireContext().getColor(R.color.text_accent))
                binding.buttonMarkReturned.setText(R.string.warranty_mark_returned)
            }
        val exists = viewModel.billExists.value == true
        binding.buttonOpenBill.isVisible = exists
        binding.textDetailBillMissing.isVisible = !exists
    }

    private fun recordService() {
        binding.buttonRecordService.isEnabled = false
        viewModel.recordServiceCentre(
            claimId,
            binding.editServiceCentre.text?.toString().orEmpty(),
            binding.editServiceReference.text?.toString().orEmpty(),
            sentDay
        ) { finish(binding.buttonRecordService, it, null) }
    }

    private fun saveProviderOutcome() {
        val outcome = WarrantyClaimPolicy.outcomes.firstOrNull {
            WarrantyClaimPolicy.outcomeLabel(it) == binding.autoProviderOutcome.text?.toString().orEmpty()
        }
        if (outcome == null) {
            binding.layoutProviderNote.error = "Choose a provider outcome."
            return
        }
        val note = binding.editProviderNote.text?.toString().orEmpty()
        if (outcome == WarrantyClaimPolicy.OTHER && note.isBlank()) {
            binding.layoutProviderNote.error = WarrantyClaimPolicy.OUTCOME_NOTE_REQUIRED
            return
        }
        binding.layoutProviderNote.error = null
        binding.buttonSaveProviderOutcome.isEnabled = false
        viewModel.saveProviderOutcome(claimId, outcome, note, responseDay) {
            finish(binding.buttonSaveProviderOutcome, it, binding.layoutProviderNote)
        }
    }

    private fun confirmHandover() {
        binding.buttonConfirmHandover.isEnabled = false
        viewModel.saveHandover(
            claimId,
            handoverDay,
            binding.editHandoverRecipient.text?.toString().orEmpty(),
            binding.editHandoverNote.text?.toString().orEmpty()
        ) { finish(binding.buttonConfirmHandover, it, null) }
    }

    private fun finish(
        button: View,
        outcome: WarrantyClaimOutcome,
        errorLayout: com.google.android.material.textfield.TextInputLayout?
    ) {
        if (_binding == null) return
        button.isEnabled = true
        if (outcome is WarrantyClaimOutcome.NotAllowed) {
            if (outcome.message == WarrantyClaimPolicy.SAVE_IN_PROGRESS || errorLayout == null) {
                android.widget.Toast.makeText(requireContext(), outcome.message, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                errorLayout.error = outcome.message
            }
        }
    }

    private fun today(): Long = WarrantyClaimPolicy.today(System.currentTimeMillis())

    private fun showDay(field: com.google.android.material.textfield.TextInputEditText, day: Long) {
        field.setText(WarrantyPolicy.formatDate(day))
    }

    private fun pickDate(current: Long, onPicked: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(current * 86_400_000L)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            onPicked(selection / 86_400_000L)
        }
        picker.show(parentFragmentManager, "warranty_date")
    }

    private fun showOutcome(status: String) {
        binding.layoutClaimOutcome.isVisible = WarrantyClaimPolicy.needsResolution(status)
    }

    private fun statusCode(label: String): String? =
        WarrantyClaimPolicy.statuses.firstOrNull { WarrantyClaimPolicy.label(it) == label }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_CLAIM_ID = "claimId"
    }
}
