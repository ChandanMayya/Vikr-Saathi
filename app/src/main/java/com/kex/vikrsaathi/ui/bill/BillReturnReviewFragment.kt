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
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentBillReturnReviewBinding
import com.kex.vikrsaathi.domain.finance.RefundMethod
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.ui.finance.FinanceUi
import com.kex.vikrsaathi.util.PriceCalculator
import com.kex.vikrsaathi.util.ViewModelFactory

class BillReturnReviewFragment : Fragment() {

    private var _binding: FragmentBillReturnReviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillReturnViewModel by viewModels({
        findNavController().getBackStackEntry(R.id.billReturnFragment)
    }) {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBillReturnReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val state = viewModel.form.value
        render(state?.reason.orEmpty(), state?.note.orEmpty())
        binding.buttonPostReturn.setOnClickListener {
            binding.buttonPostReturn.isEnabled = false
            val current = viewModel.form.value
            viewModel.post(
                reason = current?.reason.orEmpty(),
                note = current?.note.orEmpty(),
                onSuccess = { returnId ->
                    Toast.makeText(requireContext(), R.string.return_posted, Toast.LENGTH_SHORT).show()
                    findNavController().navigate(
                        R.id.action_review_to_returns,
                        bundleOf(
                            BillReturnsFragment.ARG_BILL_ID to findReturnBillId(),
                            BillReturnsFragment.ARG_POSTED_ID to returnId
                        )
                    )
                },
                onError = { error ->
                    binding.buttonPostReturn.isEnabled = true
                    Toast.makeText(requireContext(), FinanceUi.message(requireContext(), error), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun findReturnBillId(): Long {
        return findNavController().getBackStackEntry(R.id.billReturnFragment).arguments?.getLong("billId") ?: -1L
    }

    private fun render(reason: String, note: String) {
        val state = viewModel.form.value ?: return
        val preview = viewModel.preview()
        val symbol = viewModel.currencySymbol
        val body = buildString {
            appendLine(state.billNumber)
            appendLine(getString(R.string.return_header_meta, state.dateLabel, state.customerName.ifBlank {
                getString(R.string.return_walk_in)
            }))
            appendLine()
            state.lines.filter { it.quantity > 0 }.forEach { line ->
                val value = preview?.lines?.firstOrNull { it.billItemId == line.billItemId }?.valuePaise ?: 0L
                appendLine("${line.name} × ${line.quantity} · ${money(value)}")
                appendLine(
                    if (line.restock) getString(R.string.return_restock_effect) else getString(R.string.return_damaged_effect)
                )
            }
            appendLine()
            appendLine("${getString(R.string.return_value)} ${money(preview?.totalPaise ?: 0L)}")
            if (preview != null && preview.billRoundOffPaise != 0L) {
                appendLine(getString(R.string.return_round_off_note))
            }
            val plan = viewModel.exchangePlan()
            if (plan == null) {
                appendLine(refundLabel(state.refundMethod) + " " + money(preview?.totalPaise ?: 0L))
                appendLine(
                    if (state.refundMethod == RefundMethod.STORE_CREDIT) {
                        getString(R.string.return_credit_note)
                    } else {
                        getString(R.string.return_cash_note)
                    }
                )
            } else {
                appendLine("${getString(R.string.return_replacement_total)} ${money(viewModel.replacementPaise())}")
                state.replacements.forEach { item ->
                    appendLine("${item.name} × ${item.quantity} · ${PriceCalculator.formatAmount(item.unitPrice * item.quantity, symbol)}")
                }
                when {
                    plan.customerPaysPaise > 0L -> appendLine("${getString(R.string.return_customer_pays)} ${money(plan.customerPaysPaise)}")
                    plan.shopRefundsPaise > 0L -> appendLine("${getString(R.string.return_shop_refunds)} ${money(plan.shopRefundsPaise)}")
                    else -> appendLine(getString(R.string.return_even))
                }
            }
            if (reason.isNotBlank()) appendLine(reason)
            if (note.isNotBlank()) appendLine(note)
        }
        binding.textReviewBody.text = body.trim()
    }

    private fun refundLabel(method: String): String = when (method) {
        RefundMethod.UPI -> getString(R.string.refund_upi)
        RefundMethod.STORE_CREDIT -> getString(R.string.refund_store_credit)
        else -> getString(R.string.refund_cash)
    }

    private fun money(paise: Long): String {
        return PriceCalculator.formatAmount(Money.paiseToRupees(paise), viewModel.currencySymbol)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
