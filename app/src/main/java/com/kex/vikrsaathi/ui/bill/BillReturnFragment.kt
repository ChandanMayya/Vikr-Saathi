package com.kex.vikrsaathi.ui.bill

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentBillReturnBinding
import com.kex.vikrsaathi.databinding.ItemReturnLineBinding
import com.kex.vikrsaathi.domain.finance.RefundMethod
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.util.PriceCalculator
import com.kex.vikrsaathi.util.ViewModelFactory
import kotlinx.coroutines.launch

class BillReturnFragment : Fragment() {

    private var _binding: FragmentBillReturnBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillReturnViewModel by viewModels({
        findNavController().getBackStackEntry(R.id.billReturnFragment)
    }) {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBillReturnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.radioRefundCash.isChecked = true
        binding.radioDueCash.isChecked = true
        binding.groupRefund.setOnCheckedChangeListener { _, checkedId ->
            val method = when (checkedId) {
                R.id.radioRefundUpi -> RefundMethod.UPI
                R.id.radioRefundCredit -> RefundMethod.STORE_CREDIT
                else -> RefundMethod.CASH
            }
            viewModel.setRefundMethod(method)
        }
        binding.groupDue.setOnCheckedChangeListener { _, checkedId ->
            val choice = when (checkedId) {
                R.id.radioDueUpi -> "UPI"
                R.id.radioDueCredit -> "ACCOUNT"
                else -> "CASH"
            }
            viewModel.setDueChoice(choice)
        }
        binding.buttonAddCustomer.setOnClickListener { showCustomerDialog() }
        binding.buttonAddReplacement.setOnClickListener { showItemDialog() }
        binding.buttonReviewReturn.setOnClickListener {
            if (viewModel.preview() == null) {
                Toast.makeText(requireContext(), R.string.return_select_qty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.rememberText(
                binding.editReturnReason.text?.toString().orEmpty(),
                binding.editReturnNote.text?.toString().orEmpty()
            )
            findNavController().navigate(R.id.action_return_to_review)
        }
        viewModel.form.observe(viewLifecycleOwner) { render(it) }
        val billId = arguments?.getLong(ARG_BILL_ID, -1L) ?: -1L
        viewModel.load(billId)
    }

    private fun render(state: ReturnFormState) {
        binding.textReturnMissing.isVisible = state.missing
        binding.cardReturnHeader.isVisible = !state.missing
        binding.textReturnBill.text = state.billNumber
        val customer = state.customerName.ifBlank { getString(R.string.return_walk_in) }
        binding.textReturnMeta.text = getString(R.string.return_header_meta, state.dateLabel, customer)
        renderLines(state)
        val preview = viewModel.preview()
        binding.textReturnValue.text = money(preview?.totalPaise ?: 0L)
        val walkIn = state.billCustomerId == null
        binding.textStoreCreditHint.isVisible = walkIn && state.attachedCustomerId == null
        binding.buttonAddCustomer.isVisible = walkIn
        binding.textAttachedCustomer.isVisible = state.attachedCustomerName.isNotBlank()
        binding.textAttachedCustomer.text = state.attachedCustomerName
        val exchanging = state.replacements.isNotEmpty()
        binding.groupDue.isVisible = exchanging
        binding.textExchangeSummary.isVisible = exchanging
        if (exchanging) {
            val plan = viewModel.exchangePlan()
            val replacement = viewModel.replacementPaise()
            binding.radioDueCredit.text = if ((preview?.totalPaise ?: 0L) > replacement) {
                getString(R.string.refund_store_credit)
            } else {
                getString(R.string.payment_credit)
            }
            binding.textExchangeSummary.text = when {
                plan == null -> ""
                plan.customerPaysPaise > 0L -> getString(R.string.return_customer_pays) + " " + money(plan.customerPaysPaise)
                plan.shopRefundsPaise > 0L -> getString(R.string.return_shop_refunds) + " " + money(plan.shopRefundsPaise)
                else -> getString(R.string.return_even)
            }
        }
        binding.buttonReviewReturn.isEnabled = !state.posting && preview != null
    }

    private fun renderLines(state: ReturnFormState) {
        val container = binding.containerReturnLines
        if (container.childCount != state.lines.size) {
            container.removeAllViews()
            state.lines.forEach { line ->
                val row = ItemReturnLineBinding.inflate(layoutInflater, container, false)
                row.root.tag = line.billItemId
                container.addView(row.root)
            }
        }
        state.lines.forEach { line ->
            val row = ItemReturnLineBinding.bind(container.findViewWithTag(line.billItemId))
            row.textLineName.text = line.name
            row.textLineMeta.text = getString(R.string.return_sold_line, line.sold, line.already, line.eligible)
            row.textLinePrice.text = getString(
                R.string.return_unit_price,
                PriceCalculator.formatAmount(line.unitPrice, viewModel.currencySymbol)
            )
            row.textLineQty.text = line.quantity.toString()
            val portion = viewModel.preview()?.lines?.firstOrNull { it.billItemId == line.billItemId }?.valuePaise
            row.textLinePortion.text = if (line.quantity > 0 && portion != null) money(portion) else ""
            row.buttonQtyMinus.setOnClickListener { viewModel.setQuantity(line.billItemId, line.quantity - 1) }
            row.buttonQtyPlus.setOnClickListener { viewModel.setQuantity(line.billItemId, line.quantity + 1) }
            row.checkRestock.setOnCheckedChangeListener(null)
            row.checkRestock.isChecked = line.restock
            row.checkRestock.isEnabled = line.itemId != null
            row.checkRestock.setOnCheckedChangeListener { _, checked ->
                viewModel.setRestock(line.billItemId, checked)
            }
        }
        renderReplacements(state)
    }

    private fun renderReplacements(state: ReturnFormState) {
        val container = binding.containerReplacements
        if (container.childCount != state.replacements.size) {
            container.removeAllViews()
            state.replacements.forEach { item ->
                val row = ItemReturnLineBinding.inflate(layoutInflater, container, false)
                row.root.tag = "rep-${item.key}"
                row.checkRestock.isVisible = false
                container.addView(row.root)
            }
        }
        state.replacements.forEach { item ->
            val row = ItemReturnLineBinding.bind(container.findViewWithTag("rep-${item.key}"))
            row.textLineName.text = item.name
            row.textLineMeta.text = PriceCalculator.formatAmount(item.unitPrice, viewModel.currencySymbol)
            row.textLinePrice.text = ""
            row.textLineQty.text = item.quantity.toString()
            row.textLinePortion.text = PriceCalculator.formatAmount(item.unitPrice * item.quantity, viewModel.currencySymbol)
            row.buttonQtyMinus.setOnClickListener { viewModel.setReplacementQuantity(item.key, item.quantity - 1) }
            row.buttonQtyPlus.setOnClickListener { viewModel.setReplacementQuantity(item.key, item.quantity + 1) }
        }
    }

    private fun showCustomerDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.return_customer_name)
            minHeight = (48 * resources.displayMetrics.density).toInt()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.return_add_customer)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val name = input.text?.toString().orEmpty()
                    val id = viewModel.createCustomer(name)
                    if (id == null) return@launch
                    viewModel.attachCustomer(id, name.trim())
                    Toast.makeText(requireContext(), R.string.return_customer_added, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showItemDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.item_name)
            minHeight = (48 * resources.displayMetrics.density).toInt()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.return_add_replacement)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val matches = viewModel.searchItems(input.text?.toString().orEmpty())
                    val item = matches.firstOrNull()
                    if (item == null) {
                        Toast.makeText(requireContext(), R.string.no_master_items, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    viewModel.addReplacement(item.id, item.name, item.mrp)
                }
            }
            .show()
    }

    private fun money(paise: Long): String {
        return PriceCalculator.formatAmount(Money.paiseToRupees(paise), viewModel.currencySymbol)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_BILL_ID = "billId"
    }
}
