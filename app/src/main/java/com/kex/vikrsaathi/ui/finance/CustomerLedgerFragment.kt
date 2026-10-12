package com.kex.vikrsaathi.ui.finance

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.databinding.FragmentPartyLedgerBinding
import com.kex.vikrsaathi.domain.finance.LedgerStatement
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.FileShareHelper
import kotlinx.coroutines.launch
import java.io.File

class CustomerLedgerFragment : Fragment() {

    private var _binding: FragmentPartyLedgerBinding? = null
    private val binding get() = _binding!!
    private var customerId: Long = 0L
    private lateinit var ledgerRange: FinanceDates.LedgerRange

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyLedgerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.CUSTOMER_LEDGER)
        customerId = arguments?.getLong(ARG_CUSTOMER_ID) ?: 0L
        binding.buttonOpening.setOnClickListener { showOpening() }
        binding.buttonPayment.setOnClickListener { showPayment() }
        binding.buttonReturn.setOnClickListener { showReturn() }
        binding.buttonCorrection.setOnClickListener { showCorrection() }
        binding.buttonShareStatement.setOnClickListener { shareStatement() }
        ledgerRange = FinanceDates.LedgerRange(this, binding.buttonLedgerDates, binding.buttonClearDates) { reload() }
        reload()
    }

    private fun reload() {
        val app = requireActivity().application as VikrSaathiApp
        val symbol = app.settingsRepository.currencySymbol
        viewLifecycleOwner.lifecycleScope.launch {
            val customer = app.customerRepository.getById(customerId) ?: return@launch
            val from = ledgerRange.from ?: 0L
            val to = ledgerRange.to ?: Long.MAX_VALUE
            val statement = app.customerFinanceRepository.statement(customerId, from, to)
            val balance = app.customerFinanceRepository.balancePaise(customerId)
            if (_binding == null) return@launch
            binding.textPartyName.text = customer.name
            binding.textPartyBalance.text = getString(R.string.vendor_balance, FinanceUi.formatBalance(balance, symbol))
            binding.textPartyBalance.setTextColor(requireContext().getColor(FinanceUi.balanceColorRes(balance, customer = true)))
            LedgerLines.show(binding.ledgerLines, statement, symbol, customer = true) { row ->
                customerParticulars(row)
            }
            binding.root.tag = statement
        }
    }

    private fun customerParticulars(row: com.kex.vikrsaathi.domain.finance.LedgerRow): String {
        val parts = mutableListOf(FinanceUi.customerEntryLabel(requireContext(), row.entryType))
        row.method?.let { parts += FinanceUi.paymentLabel(requireContext(), it) }
        row.billId?.let { parts += getString(R.string.ledger_bill_ref, it) }
        row.note?.takeIf { it.isNotBlank() }?.let { parts += it }
        row.reason?.takeIf { it.isNotBlank() }?.let { parts += it }
        return parts.joinToString(" · ")
    }

    private fun showOpening() {
        val form = amountForm(showDirection = true, shopOwesLabel = R.string.shop_owes_customer, owesShopLabel = R.string.owes_shop)
        confirm(R.string.opening_balance_action, form) {
            val app = requireActivity().application as VikrSaathiApp
            val signed = signedAmount(form)
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    app.customerFinanceRepository.addOpeningBalance(
                        customerId, signed, form.date.millis, noteText(form)
                    )
                }.onSuccess {
                    toast(R.string.opening_saved)
                    reload()
                }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
            }
        }
    }

    private fun showPayment() {
        val form = amountForm(showMethod = true)
        confirm(R.string.add_payment_action, form) {
            val app = requireActivity().application as VikrSaathiApp
            val symbol = app.settingsRepository.currencySymbol
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    app.customerFinanceRepository.addPayment(
                        customerId = customerId,
                        amountPaise = FinanceUi.parseAmountPaise(amountText(form)),
                        occurredAt = form.date.millis,
                        method = if (form.methodUpi) PaymentMethod.UPI else PaymentMethod.CASH,
                        referenceNote = noteText(form)
                    )
                }.onSuccess { result ->
                    val allocated = result.plan.lines.sumOf { it.amountPaise }
                    toast(
                        getString(
                            R.string.payment_saved,
                            FinanceUi.formatPaise(allocated, symbol),
                            FinanceUi.formatPaise(result.plan.unallocatedPaise, symbol)
                        )
                    )
                    reload()
                }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
            }
        }
    }

    private fun showReturn() {
        val app = requireActivity().application as VikrSaathiApp
        viewLifecycleOwner.lifecycleScope.launch {
            val catalog = app.itemRepository.getAllSync()
            if (catalog.isEmpty()) {
                toast(getString(R.string.no_master_items))
                return@launch
            }
            var selected: Item? = null
            val form = FinanceForm.column(this@CustomerLedgerFragment)
            val bill = FinanceForm.text(this@CustomerLedgerFragment, form, getString(R.string.bill_number_label), InputType.TYPE_CLASS_NUMBER)
            val qty = FinanceForm.text(this@CustomerLedgerFragment, form, getString(R.string.return_qty), InputType.TYPE_CLASS_NUMBER)
            FinanceForm.itemPicker(this@CustomerLedgerFragment, form, getString(R.string.item_name), catalog) { selected = it }
            val refund = FinanceForm.text(this@CustomerLedgerFragment, form, getString(R.string.money_refunded), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            val credit = FinanceForm.text(this@CustomerLedgerFragment, form, getString(R.string.credit_adjustment), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            val reason = FinanceForm.text(this@CustomerLedgerFragment, form, getString(R.string.reason_label))
            confirm(R.string.sales_return_action, form) {
                val quantity = qty.text?.toString()?.toIntOrNull() ?: 0
                val item = selected
                if (quantity > 0 && item == null) {
                    toast(getString(R.string.select_master_item))
                    return@confirm
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val billId = bill.text?.toString()?.toLongOrNull()
                    val resolved = if (quantity > 0 && item != null) {
                        val billItems = if (billId != null) app.database.billItemDao().getItemsForBill(billId) else emptyList()
                        val match = billItems.firstOrNull { it.itemId == item.id }
                        listOf(
                            com.kex.vikrsaathi.data.repository.ReturnLineInput(
                                itemId = match?.itemId ?: item.id,
                                itemName = match?.itemName ?: item.name,
                                quantity = quantity
                            )
                        )
                    } else {
                        emptyList()
                    }
                    runCatching {
                        app.customerFinanceRepository.addSalesReturn(
                            customerId = customerId,
                            billId = billId,
                            reason = reason.text?.toString().orEmpty(),
                            moneyRefundedPaise = FinanceUi.parseAmountPaise(refund.text?.toString()),
                            creditAdjustmentPaise = FinanceUi.parseAmountPaise(credit.text?.toString()),
                            lines = resolved,
                            occurredAt = System.currentTimeMillis()
                        )
                    }.onSuccess {
                        toast(R.string.saved)
                        reload()
                    }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
                }
            }
        }
    }

    private fun showCorrection() {
        val form = amountForm(showDirection = true, showReason = true, shopOwesLabel = R.string.correction_decrease, owesShopLabel = R.string.correction_increase)
        confirm(R.string.correction_action, form.root) {
            val app = requireActivity().application as VikrSaathiApp
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    app.customerFinanceRepository.addCorrection(
                        customerId = customerId,
                        amountPaise = signedAmount(form),
                        occurredAt = form.date.millis,
                        reason = form.reason?.text?.toString().orEmpty()
                    )
                }.onSuccess {
                    toast(R.string.saved)
                    reload()
                }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
            }
        }
    }

    private fun shareStatement() {
        val app = requireActivity().application as VikrSaathiApp
        val symbol = app.settingsRepository.currencySymbol
        viewLifecycleOwner.lifecycleScope.launch {
            val customer = app.customerRepository.getById(customerId) ?: return@launch
            val statement = app.customerFinanceRepository.statement(customerId, 0L, Long.MAX_VALUE)
            val file = File(requireContext().cacheDir, "statement-${customer.id}.txt")
            file.writeText(renderStatement(customer.name, statement, symbol))
            FileShareHelper.shareFile(requireContext(), file, "text/plain", getString(R.string.statement_subject))
        }
    }

    private fun renderStatement(name: String, statement: LedgerStatement, symbol: String): String {
        val body = statement.lines.joinToString("\n") { line ->
            val type = FinanceUi.customerEntryLabel(requireContext(), line.row.entryType)
            "${FinanceUi.formatDate(line.row.occurredAt)} | $type | ${line.row.method.orEmpty()} | ${line.row.billId ?: ""} | ${FinanceUi.formatPaise(line.row.amountPaise, symbol)} | ${FinanceUi.formatBalance(line.runningBalancePaise, symbol)}"
        }
        return buildString {
            appendLine(name)
            appendLine(getString(R.string.customer_balance_sign))
            appendLine(getString(R.string.ledger_opening, FinanceUi.formatBalance(statement.openingPaise, symbol)))
            appendLine("Date | Type | Method | Bill | Amount | Running")
            appendLine(body)
            appendLine(getString(R.string.ledger_closing, FinanceUi.formatBalance(statement.closingPaise, symbol)))
        }
    }

    private fun amountForm(
        showDirection: Boolean = false,
        showMethod: Boolean = false,
        showReason: Boolean = false,
        owesShopLabel: Int = R.string.owes_shop,
        shopOwesLabel: Int = R.string.shop_owes_customer
    ): AmountForm {
        val root = FinanceForm.column(this)
        val amount = FinanceForm.text(
            this, root, getString(R.string.amount_label),
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val date = FinanceDates.DateField(this)
        root.addView(date.view)
        val note = FinanceForm.text(this, root, getString(R.string.note_label))
        val reason = if (showReason) FinanceForm.text(this, root, getString(R.string.reason_label)) else null
        val direction = if (showDirection) android.widget.CheckBox(requireContext()).apply {
            text = getString(shopOwesLabel)
            root.addView(this)
        } else null
        if (showDirection) {
            root.addView(android.widget.TextView(requireContext()).apply { text = getString(owesShopLabel) })
        }
        val method = if (showMethod) android.widget.CheckBox(requireContext()).apply {
            text = getString(R.string.payment_upi)
            root.addView(this)
        } else null
        return AmountForm(root, amount, date, note, reason, direction, method?.isChecked == true, method)
    }

    private class AmountForm(
        val root: View,
        val amount: TextInputEditText,
        val date: FinanceDates.DateField,
        val note: TextInputEditText,
        val reason: TextInputEditText?,
        val shopOwes: android.widget.CheckBox?,
        var methodUpi: Boolean,
        val methodBox: android.widget.CheckBox?
    )

    private fun signedAmount(form: AmountForm): Long {
        val amount = FinanceUi.parseAmountPaise(form.amount.text?.toString())
        return if (form.shopOwes?.isChecked == true) -amount else amount
    }

    private fun amountText(form: AmountForm) = form.amount.text?.toString()
    private fun noteText(form: AmountForm) = form.note.text?.toString()

    private fun confirm(title: Int, view: View, onOk: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                if (view is android.view.ViewGroup) {
                    // method checkbox read at click time inside callers that use AmountForm
                }
                onOk()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirm(title: Int, form: AmountForm, onOk: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(form.root)
            .setPositiveButton(R.string.save) { _, _ ->
                form.methodUpi = form.methodBox?.isChecked == true
                onOk()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(message: String) {
        if (_binding == null) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun toast(res: Int) = toast(getString(res))

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_CUSTOMER_ID = "customerId"
        fun args(customerId: Long) = bundleOf(ARG_CUSTOMER_ID to customerId)
    }
}
