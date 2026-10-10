package com.kex.vikrsaathi.ui.finance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.data.repository.InvoiceLineInput
import com.kex.vikrsaathi.data.repository.OrderLineInput
import com.kex.vikrsaathi.data.repository.QuoteLineInput
import com.kex.vikrsaathi.data.repository.ReceiptLineInput
import com.kex.vikrsaathi.databinding.FragmentPartyLedgerBinding
import com.kex.vikrsaathi.databinding.FragmentPurchasingBinding
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import kotlinx.coroutines.launch

class VendorDetailFragment : Fragment() {

    private var _binding: FragmentPartyLedgerBinding? = null
    private val binding get() = _binding!!
    private val adapter = FinanceRowAdapter()
    private var vendorId: Long = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyLedgerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.VENDORS)
        vendorId = arguments?.getLong(ARG_VENDOR_ID) ?: 0L
        binding.textSignHint.setText(R.string.vendor_balance_sign)
        binding.buttonReturn.setText(R.string.entry_purchase_return)
        binding.recyclerLedger.adapter = adapter
        binding.buttonOpening.setOnClickListener { promptAmount(R.string.opening_balance_action, true) { amount, date, note, shopOwes ->
            val signed = if (shopOwes) -amount else amount
            app().vendorRepository.addOpeningBalance(vendorId, signed, date, note)
        } }
        binding.buttonPayment.setOnClickListener { promptAmount(R.string.add_payment_action, false) { amount, date, note, _ ->
            app().vendorRepository.addPayment(vendorId, amount, date, PaymentMethod.CASH, note)
        } }
        binding.buttonReturn.setOnClickListener { promptAmount(R.string.entry_purchase_return, false, reason = true) { amount, date, note, _ ->
            app().vendorRepository.addPurchaseReturnCredit(vendorId, null, amount, date, note.orEmpty())
        } }
        binding.buttonCorrection.setOnClickListener { promptAmount(R.string.correction_action, true, reason = true) { amount, date, note, shopOwes ->
            val signed = if (shopOwes) -amount else amount
            app().vendorRepository.addCorrection(vendorId, signed, date, note.orEmpty(), null)
        } }
        binding.buttonShareStatement.setText(R.string.purchasing)
        binding.buttonShareStatement.setOnClickListener {
            findNavController().navigate(R.id.purchasingFragment, bundleOf(PurchasingFragment.ARG_VENDOR_ID to vendorId))
        }
        binding.buttonApplyDates.setOnClickListener { reload() }
        binding.textPartyName.setOnLongClickListener {
            archiveOrDelete()
            true
        }
        reload()
    }

    private fun reload() {
        val symbol = app().settingsRepository.currencySymbol
        viewLifecycleOwner.lifecycleScope.launch {
            val vendor = app().vendorRepository.getById(vendorId) ?: return@launch
            val from = if (binding.editFrom.text.isNullOrBlank()) 0L else FinanceUi.parseDateOrToday(binding.editFrom.text?.toString())
            val to = if (binding.editTo.text.isNullOrBlank()) Long.MAX_VALUE else FinanceUi.parseDateOrToday(binding.editTo.text?.toString())
            val statement = app().vendorRepository.statement(vendorId, from, to)
            val balance = app().vendorRepository.balancePaise(vendorId)
            if (_binding == null) return@launch
            binding.textPartyName.text = vendor.name
            binding.textPartyBalance.text = getString(R.string.vendor_balance, FinanceUi.formatPaise(balance, symbol))
            binding.textOpening.text = getString(R.string.ledger_opening, FinanceUi.formatPaise(statement.openingPaise, symbol))
            binding.textClosing.text = getString(R.string.ledger_closing, FinanceUi.formatPaise(statement.closingPaise, symbol))
            adapter.submit(statement.lines.map { line ->
                FinanceRow(
                    FinanceUi.vendorEntryLabel(requireContext(), line.row.entryType),
                    "${FinanceUi.formatDate(line.row.occurredAt)}  ${FinanceUi.formatPaise(line.row.amountPaise, symbol)}  → ${FinanceUi.formatPaise(line.runningBalancePaise, symbol)} ${line.row.note.orEmpty()} ${line.row.reason.orEmpty()}"
                )
            })
        }
    }

    private fun archiveOrDelete() {
        viewLifecycleOwner.lifecycleScope.launch {
            val vendor = app().vendorRepository.getById(vendorId) ?: return@launch
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(vendor.name)
                .setItems(arrayOf(getString(R.string.archive_vendor), getString(R.string.edit_vendor))) { _, which ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (which == 0) {
                            runCatching { app().vendorRepository.deleteIfNoHistory(vendor) }
                                .onFailure {
                                    app().vendorRepository.archive(vendor.id)
                                    toast(FinanceUi.message(requireContext(), it))
                                }
                        }
                    }
                }
                .show()
        }
    }

    private fun promptAmount(
        title: Int,
        direction: Boolean,
        reason: Boolean = false,
        block: suspend (Long, Long, String?, Boolean) -> Unit
    ) {
        val amount = TextInputEditText(requireContext()).apply { hint = getString(R.string.amount_label) }
        val date = TextInputEditText(requireContext()).apply { hint = getString(R.string.date_label) }
        val note = TextInputEditText(requireContext()).apply {
            hint = getString(if (reason) R.string.reason_label else R.string.note_label)
        }
        val shopOwes = android.widget.CheckBox(requireContext()).apply { text = getString(R.string.vendor_owes_shop) }
        val box = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(amount); addView(date); addView(note)
            if (direction) addView(shopOwes)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        block(
                            FinanceUi.parseAmountPaise(amount.text?.toString()),
                            FinanceUi.parseDateOrToday(date.text?.toString()),
                            note.text?.toString(),
                            shopOwes.isChecked
                        )
                    }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
                    reload()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun app() = requireActivity().application as VikrSaathiApp
    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_VENDOR_ID = "vendorId"
    }
}

class PurchasingFragment : Fragment() {

    private var _binding: FragmentPurchasingBinding? = null
    private val binding get() = _binding!!
    private var vendorId: Long = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPurchasingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.VENDORS)
        vendorId = arguments?.getLong(ARG_VENDOR_ID) ?: 0L
        binding.buttonNewOrder.setOnClickListener { createOrder(null) }
        binding.buttonNewReceipt.setOnClickListener { createReceipt() }
        binding.buttonNewInvoice.setOnClickListener { createInvoice() }
        binding.buttonNewQuote.setOnClickListener { createQuote() }
        binding.buttonCompareQuotes.setOnClickListener { compare() }
        reload()
    }

    private fun reload() {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = app()
            val symbol = app.settingsRepository.currencySymbol
            val orders = app.vendorRepository.orders().filter { vendorId == 0L || it.vendorId == vendorId }
            val receipts = app.vendorRepository.receipts().filter { vendorId == 0L || it.vendorId == vendorId }
            val invoices = app.vendorRepository.invoices().filter { vendorId == 0L || it.vendorId == vendorId }
            val quotes = app.vendorRepository.quotes().filter { vendorId == 0L || it.vendorId == vendorId }
            val text = buildString {
                appendLine(getString(R.string.quote_landed_hint))
                appendLine()
                appendLine("Orders")
                orders.forEach { order ->
                    appendLine("${order.orderNumber} vendor ${order.vendorId} quote ${order.quoteId ?: "-"} ${order.status}")
                    app.vendorRepository.orderLines(order.id).forEach { line ->
                        appendLine("  ${line.itemName} x${line.quantity} @ ${FinanceUi.formatPaise(line.ratePaise, symbol)}")
                    }
                }
                appendLine()
                appendLine("Receipts")
                receipts.forEach { receipt ->
                    appendLine("${receipt.receiptNumber} order ${receipt.orderId ?: "direct"}")
                    app.vendorRepository.receiptLines(receipt.id).forEach { line ->
                        appendLine("  ${line.itemName} received ${line.receivedQty} short ${line.shortQty} damaged ${line.damagedQty} rejected ${line.rejectedQty} accepted ${line.acceptedQty}")
                    }
                }
                appendLine()
                appendLine("Invoices")
                invoices.forEach { invoice ->
                    appendLine("${invoice.invoiceNumber} ${FinanceUi.formatPaise(invoice.totalPaise, symbol)}")
                    val recon = app.vendorRepository.reconcile(invoice.orderId, invoice.receiptId, invoice.id)
                    recon.forEach { row ->
                        appendLine("  " + getString(R.string.recon_line, row.itemName, row.orderedQty, row.acceptedQty, row.invoicedQty))
                    }
                }
                appendLine()
                appendLine("Quotes")
                quotes.forEach { quote ->
                    appendLine("Quote ${quote.id} delivery ${FinanceUi.formatPaise(quote.deliveryChargesPaise, symbol)}")
                    app.vendorRepository.quoteLines(quote.id).forEach { line ->
                        appendLine("  ${line.itemName} x${line.availableQty} @ ${FinanceUi.formatPaise(line.ratePaise, symbol)}")
                    }
                }
            }
            if (_binding == null) return@launch
            binding.textPurchaseBody.text = text
        }
    }

    private fun createOrder(quoteId: Long?) {
        val item = TextInputEditText(requireContext()).apply { hint = getString(R.string.item_name) }
        val qty = TextInputEditText(requireContext()).apply { hint = getString(R.string.ordered_qty); setText("1") }
        val rate = TextInputEditText(requireContext()).apply { hint = getString(R.string.rate_label) }
        val notes = TextInputEditText(requireContext()).apply { hint = getString(R.string.note_label) }
        ask(R.string.new_purchase_order, item, qty, rate, notes) {
            viewLifecycleOwner.lifecycleScope.launch {
                val lines = if (quoteId != null) {
                    app().vendorRepository.quoteLines(quoteId).map {
                        OrderLineInput(it.itemId, it.itemName, it.availableQty.coerceAtLeast(1), it.ratePaise)
                    }
                } else {
                    listOf(OrderLineInput(null, item.text.toString(), qty.text.toString().toIntOrNull() ?: 1, FinanceUi.parseAmountPaise(rate.text?.toString())))
                }
                runCatching {
                    app().vendorRepository.saveOrder(vendorId, null, notes.text?.toString().orEmpty(), quoteId, lines)
                }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
                reload()
            }
        }
    }

    private fun createReceipt() {
        val order = TextInputEditText(requireContext()).apply { hint = getString(R.string.link_order_optional) }
        val item = TextInputEditText(requireContext()).apply { hint = getString(R.string.item_name) }
        val received = TextInputEditText(requireContext()).apply { hint = getString(R.string.received_qty) }
        val damaged = TextInputEditText(requireContext()).apply { hint = getString(R.string.damaged_qty); setText("0") }
        val rejected = TextInputEditText(requireContext()).apply { hint = getString(R.string.rejected_qty); setText("0") }
        ask(R.string.new_goods_receipt, order, item, received, damaged, rejected) {
            viewLifecycleOwner.lifecycleScope.launch {
                val orderId = order.text?.toString()?.toLongOrNull()
                val ordered = if (orderId != null) {
                    app().vendorRepository.orderLines(orderId).firstOrNull()?.quantity ?: 0
                } else 0
                val itemId = app().vendorRepository.orderLines(orderId ?: -1).firstOrNull()?.itemId
                runCatching {
                    app().vendorRepository.saveReceipt(
                        vendorId = vendorId,
                        orderId = orderId,
                        notes = "",
                        receivedAt = System.currentTimeMillis(),
                        lines = listOf(
                            ReceiptLineInput(
                                orderLineId = null,
                                itemId = itemId,
                                itemName = item.text?.toString().orEmpty(),
                                orderedQty = ordered,
                                receivedQty = received.text.toString().toIntOrNull() ?: 0,
                                damagedQty = damaged.text.toString().toIntOrNull() ?: 0,
                                rejectedQty = rejected.text.toString().toIntOrNull() ?: 0
                            )
                        )
                    )
                }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
                reload()
            }
        }
    }

    private fun createInvoice() {
        val number = TextInputEditText(requireContext()).apply { hint = getString(R.string.invoice_number_label) }
        val item = TextInputEditText(requireContext()).apply { hint = getString(R.string.item_name) }
        val qty = TextInputEditText(requireContext()).apply { hint = getString(R.string.ordered_qty); setText("1") }
        val rate = TextInputEditText(requireContext()).apply { hint = getString(R.string.rate_label) }
        val delivery = TextInputEditText(requireContext()).apply { hint = getString(R.string.delivery_charges); setText("0") }
        ask(R.string.new_purchase_invoice, number, item, qty, rate, delivery) {
            viewLifecycleOwner.lifecycleScope.launch {
                val quantity = qty.text.toString().toIntOrNull() ?: 1
                val ratePaise = FinanceUi.parseAmountPaise(rate.text?.toString())
                runCatching {
                    app().vendorRepository.saveInvoice(
                        vendorId = vendorId,
                        invoiceNumber = number.text?.toString().orEmpty(),
                        invoiceDate = System.currentTimeMillis(),
                        orderId = null,
                        receiptId = null,
                        deliveryChargesPaise = FinanceUi.parseAmountPaise(delivery.text?.toString()),
                        otherChargesPaise = 0L,
                        discountPaise = 0L,
                        notes = "",
                        lines = listOf(InvoiceLineInput(null, item.text.toString(), quantity, ratePaise))
                    )
                }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
                reload()
            }
        }
    }

    private fun createQuote() {
        val item = TextInputEditText(requireContext()).apply { hint = getString(R.string.item_name) }
        val rate = TextInputEditText(requireContext()).apply { hint = getString(R.string.rate_label) }
        val qty = TextInputEditText(requireContext()).apply { hint = getString(R.string.available_qty); setText("1") }
        val delivery = TextInputEditText(requireContext()).apply { hint = getString(R.string.delivery_charges); setText("0") }
        val discount = TextInputEditText(requireContext()).apply { hint = getString(R.string.quote_discount); setText("0") }
        ask(R.string.new_quote, item, rate, qty, delivery, discount) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    app().vendorRepository.saveQuote(
                        vendorId = vendorId,
                        quoteDate = System.currentTimeMillis(),
                        validUntil = null,
                        deliveryChargesPaise = FinanceUi.parseAmountPaise(delivery.text?.toString()),
                        discountPaise = FinanceUi.parseAmountPaise(discount.text?.toString()),
                        expectedDelivery = null,
                        notes = "",
                        lines = listOf(
                            QuoteLineInput(
                                null,
                                item.text.toString(),
                                FinanceUi.parseAmountPaise(rate.text?.toString()),
                                qty.text.toString().toIntOrNull() ?: 1
                            )
                        )
                    )
                }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
                reload()
            }
        }
    }

    private fun compare() {
        viewLifecycleOwner.lifecycleScope.launch {
            val quotes = app().vendorRepository.quotes().map { it.id }
            val (rows, totals) = app().vendorRepository.compare(quotes)
            val symbol = app().settingsRepository.currencySymbol
            val text = buildString {
                appendLine(getString(R.string.quote_landed_hint))
                rows.forEach { row ->
                    appendLine(row.itemName)
                    row.rateByQuoteId.forEach { (id, rate) ->
                        appendLine("  quote $id ${FinanceUi.formatPaise(rate, symbol)}")
                    }
                }
                totals.forEach { total ->
                    appendLine("${total.vendorName}: landed ${FinanceUi.formatPaise(total.landedPaise, symbol)} (goods ${FinanceUi.formatPaise(total.goodsPaise, symbol)} + delivery ${FinanceUi.formatPaise(total.deliveryChargesPaise, symbol)} - discount ${FinanceUi.formatPaise(total.discountPaise, symbol)})")
                }
            }
            if (_binding == null) return@launch
            binding.textPurchaseBody.text = text
            val best = totals.minByOrNull { it.landedPaise }
            if (best != null) {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(text)
                    .setPositiveButton(R.string.create_order_from_quote) { _, _ -> createOrder(best.quoteId) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun ask(title: Int, vararg fields: TextInputEditText, onSave: () -> Unit) {
        val box = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            fields.forEach { addView(it) }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ -> onSave() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun app() = requireActivity().application as VikrSaathiApp
    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_VENDOR_ID = "vendorId"
    }
}
