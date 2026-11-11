package com.kex.vikrsaathi.ui.bill

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentBillReturnsBinding
import com.kex.vikrsaathi.databinding.ItemBillReturnBinding
import com.kex.vikrsaathi.domain.finance.RefundMethod
import com.kex.vikrsaathi.ui.finance.FinanceUi
import com.kex.vikrsaathi.util.ViewModelFactory

class BillReturnsFragment : Fragment() {

    private var _binding: FragmentBillReturnsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillReturnsViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBillReturnsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = ReturnAdapter { row -> confirmVoid(row) }
        binding.recyclerReturns.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReturns.adapter = adapter
        viewModel.header.observe(viewLifecycleOwner) { binding.textReturnsHeader.text = it }
        viewModel.rows.observe(viewLifecycleOwner) { rows ->
            binding.textReturnsEmpty.isVisible = rows.isEmpty()
            adapter.submit(rows)
        }
        val billId = arguments?.getLong(ARG_BILL_ID, -1L) ?: -1L
        viewModel.load(billId)
    }

    private fun confirmVoid(row: BillReturnRow) {
        if (row.record.voidedAt != null || row.record.voidsReturnId != null) return
        if (row.record.exchangeBillId != null) {
            Toast.makeText(requireContext(), R.string.return_error_exchange_void, Toast.LENGTH_LONG).show()
            return
        }
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.return_reason_optional)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.return_void_title)
            .setMessage(R.string.return_void_message)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.return_void) { _, _ ->
                val reason = input.text?.toString().orEmpty().ifBlank { getString(R.string.return_void) }
                viewModel.void(
                    row.record.id,
                    reason,
                    onDone = { Toast.makeText(requireContext(), R.string.return_voided, Toast.LENGTH_SHORT).show() },
                    onError = { error ->
                        Toast.makeText(requireContext(), FinanceUi.message(requireContext(), error), Toast.LENGTH_LONG).show()
                    }
                )
            }
            .show()
    }

    private inner class ReturnAdapter(
        private val onVoid: (BillReturnRow) -> Unit
    ) : RecyclerView.Adapter<ReturnAdapter.Holder>() {
        private var rows: List<BillReturnRow> = emptyList()

        fun submit(next: List<BillReturnRow>) {
            rows = next
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(ItemBillReturnBinding.inflate(layoutInflater, parent, false))
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(rows[position])

        inner class Holder(private val binding: ItemBillReturnBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(row: BillReturnRow) {
                val record = row.record
                val voided = record.voidedAt != null || record.voidsReturnId != null
                binding.textReturnWhen.text = viewModel.formatWhen(record.occurredAt) +
                    if (voided) " · ${getString(R.string.return_voided)}" else ""
                val lines = row.lines.joinToString("\n") { line ->
                    val stock = if (line.restock) {
                        getString(R.string.return_restock_effect)
                    } else {
                        getString(R.string.return_damaged_effect)
                    }
                    "${line.itemName} × ${line.quantity} · ${viewModel.money(line.lineValuePaise)} · $stock"
                }
                val settlement = when {
                    record.exchangeBillId != null -> {
                        val number = row.replacementNumber.orEmpty()
                        getString(R.string.payment_exchange) + if (number.isBlank()) "" else " · $number"
                    }
                    record.refundMethod == RefundMethod.STORE_CREDIT ->
                        getString(R.string.refund_store_credit) + " " + viewModel.money(record.creditAdjustmentPaise)
                    record.moneyRefundedPaise > 0L ->
                        viewModel.settlementLabel(record) + " " + viewModel.money(record.moneyRefundedPaise)
                    else -> viewModel.money(record.returnValuePaise)
                }
                binding.textReturnSummary.text = listOf(lines, settlement, record.reason, record.note.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                binding.buttonVoidReturn.isVisible = !voided
                binding.buttonVoidReturn.setOnClickListener { onVoid(row) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_BILL_ID = "billId"
        const val ARG_POSTED_ID = "postedReturnId"
    }
}
