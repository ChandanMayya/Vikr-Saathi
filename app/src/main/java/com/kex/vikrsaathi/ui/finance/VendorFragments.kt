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
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.PurchaseOrder
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.data.repository.InvoiceLineInput
import com.kex.vikrsaathi.data.repository.OrderLineInput
import com.kex.vikrsaathi.data.repository.QuoteLineInput
import com.kex.vikrsaathi.data.repository.ReceiptLineInput
import com.kex.vikrsaathi.databinding.FragmentPartyLedgerBinding
import com.kex.vikrsaathi.databinding.FragmentPurchasingBinding
import com.kex.vikrsaathi.domain.finance.HubOrderLineInput
import com.kex.vikrsaathi.domain.finance.HubReceiptInput
import com.kex.vikrsaathi.domain.finance.OrderHubMath
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import kotlinx.coroutines.launch

class VendorDetailFragment : Fragment() {

    private var _binding: FragmentPartyLedgerBinding? = null
    private val binding get() = _binding!!
    private var vendorId: Long = 0L
    private lateinit var ledgerRange: FinanceDates.LedgerRange

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
        ledgerRange = FinanceDates.LedgerRange(this, binding.buttonLedgerDates, binding.buttonClearDates) { reload() }
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
            val from = ledgerRange.from ?: 0L
            val to = ledgerRange.to ?: Long.MAX_VALUE
            val statement = app().vendorRepository.statement(vendorId, from, to)
            val balance = app().vendorRepository.balancePaise(vendorId)
            if (_binding == null) return@launch
            binding.textPartyName.text = vendor.name
            binding.textPartyBalance.text = getString(R.string.vendor_balance, FinanceUi.formatBalance(balance, symbol))
            binding.textPartyBalance.setTextColor(requireContext().getColor(FinanceUi.balanceColorRes(balance, customer = false)))
            LedgerLines.show(binding.ledgerLines, statement, symbol, customer = false) { row ->
                vendorParticulars(row)
            }
        }
    }

    private fun vendorParticulars(row: com.kex.vikrsaathi.domain.finance.LedgerRow): String {
        val parts = mutableListOf(FinanceUi.vendorEntryLabel(requireContext(), row.entryType))
        row.method?.let { parts += FinanceUi.paymentLabel(requireContext(), it) }
        row.billId?.let { parts += getString(R.string.ledger_invoice_ref, it) }
        row.note?.takeIf { it.isNotBlank() }?.let { parts += it }
        row.reason?.takeIf { it.isNotBlank() }?.let { parts += it }
        return parts.joinToString(" · ")
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
        val box = FinanceForm.column(this)
        val amount = FinanceForm.text(
            this, box, getString(R.string.amount_label),
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val date = FinanceDates.DateField(this)
        box.addView(date.view)
        val note = FinanceForm.text(
            this, box, getString(if (reason) R.string.reason_label else R.string.note_label)
        )
        val shopOwes = android.widget.CheckBox(requireContext()).apply { text = getString(R.string.vendor_owes_shop) }
        if (direction) box.addView(shopOwes)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        block(
                            FinanceUi.parseAmountPaise(amount.text?.toString()),
                            date.millis,
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
        binding.buttonPurchaseRequests.setOnClickListener {
            findNavController().navigate(R.id.purchaseRequestListFragment)
        }
        binding.buttonNewOrder.setOnClickListener { createOrder(null) }
        binding.buttonNewReceipt.setOnClickListener { createReceipt() }
        binding.buttonNewInvoice.setOnClickListener { createInvoice() }
        binding.buttonNewQuote.setOnClickListener { createQuote() }
        binding.buttonCompareQuotes.setOnClickListener { compare() }
        if (savedInstanceState == null) {
            when (arguments?.getString(ARG_START)) {
                START_RECEIPT -> {
                    val orderId = arguments?.getLong(ARG_ORDER_ID) ?: 0L
                    if (orderId != 0L) createReceipt(orderId)
                }
                START_ORDER -> createOrder(null)
            }
        }
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
                    val kind = if (quote.requestId == null) "legacy" else "request ${quote.requestId}"
                    appendLine("Quote ${quote.id} $kind delivery ${FinanceUi.formatPaise(quote.deliveryChargesPaise, symbol)}")
                    app.vendorRepository.quoteLines(quote.id).forEach { line ->
                        if (line.availability == com.kex.vikrsaathi.domain.finance.QuoteAvailability.UNAVAILABLE) {
                            appendLine("  ${line.itemName} unavailable")
                        } else {
                            appendLine("  ${line.itemName} x${line.availableQty} @ ${FinanceUi.formatPaise(line.ratePaise, symbol)}")
                        }
                    }
                }
            }
            if (_binding == null) return@launch
            binding.textPurchaseBody.text = text
        }
    }

    private fun createOrder(quoteId: Long?) {
        if (quoteId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val lines = app().vendorRepository.quoteLines(quoteId).map {
                    OrderLineInput(it.itemId, it.itemName, it.availableQty.coerceAtLeast(1), it.ratePaise)
                }
                runCatching {
                    app().vendorRepository.saveOrder(vendorId, null, "", quoteId, lines)
                }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
                reload()
            }
            return
        }
        withItems { items ->
            var selected: Item? = null
            val form = FinanceForm.column(this)
            FinanceForm.itemPicker(this, form, getString(R.string.item_name), items) { selected = it }
            val qty = FinanceForm.text(this, form, getString(R.string.ordered_qty), InputType.TYPE_CLASS_NUMBER, "1")
            val rate = FinanceForm.text(this, form, getString(R.string.rate_label), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            val notes = FinanceForm.text(this, form, getString(R.string.note_label))
            ask(R.string.new_purchase_order, form) {
                val item = selected ?: return@ask toast(R.string.select_master_item)
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        app().vendorRepository.saveOrder(
                            vendorId,
                            null,
                            notes.text?.toString().orEmpty(),
                            null,
                            listOf(
                                OrderLineInput(
                                    item.id,
                                    item.name,
                                    qty.text.toString().toIntOrNull() ?: 1,
                                    FinanceUi.parseAmountPaise(rate.text?.toString())
                                )
                            )
                        )
                    }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
                    reload()
                }
            }
        }
    }

    private fun createReceipt(prefillOrderId: Long = 0L) {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = app().itemRepository.getAllSync()
            if (items.isEmpty()) {
                toast(R.string.no_master_items)
                return@launch
            }
            val orders = app().vendorRepository.orders().filter { it.vendorId == vendorId }
            var selectedItem: Item? = null
            var selectedOrder: PurchaseOrder? = null
            val form = FinanceForm.column(this@PurchasingFragment)
            val orderLabels = listOf(getString(R.string.purchase_order_none)) + orders.map { it.orderNumber }
            val orderField = FinanceForm.picker(this@PurchasingFragment, form, getString(R.string.link_order_optional), orderLabels) { index ->
                selectedOrder = if (index == 0) null else orders[index - 1]
            }
            val prefill = orders.firstOrNull { it.id == prefillOrderId }
            if (prefill != null) {
                selectedOrder = prefill
                orderField.setText(prefill.orderNumber, false)
            }
            val itemField = FinanceForm.itemPicker(this@PurchasingFragment, form, getString(R.string.item_name), items) { selectedItem = it }
            if (prefill != null) {
                val lines = app().vendorRepository.orderLines(prefill.id)
                val receipts = app().vendorRepository.receipts().associateBy { it.id }
                val receivedByLine = OrderHubMath.receivedQuantities(
                    prefill.id,
                    lines.map { HubOrderLineInput(it.id, it.itemId, it.quantity, it.ratePaise) },
                    app().vendorRepository.allReceiptLines().map { line ->
                        HubReceiptInput(
                            orderId = receipts[line.receiptId]?.orderId,
                            orderLineId = line.orderLineId,
                            itemId = line.itemId,
                            receivedQty = line.receivedQty
                        )
                    }
                )
                val next = lines.firstOrNull { (receivedByLine[it.id] ?: 0) < it.quantity } ?: lines.firstOrNull()
                val item = next?.itemId?.let { id -> items.firstOrNull { it.id == id } }
                if (item != null) {
                    selectedItem = item
                    itemField.setText(item.name, false)
                }
            }
            val received = FinanceForm.text(this@PurchasingFragment, form, getString(R.string.received_qty), InputType.TYPE_CLASS_NUMBER)
            val damaged = FinanceForm.text(this@PurchasingFragment, form, getString(R.string.damaged_qty), InputType.TYPE_CLASS_NUMBER, "0")
            val rejected = FinanceForm.text(this@PurchasingFragment, form, getString(R.string.rejected_qty), InputType.TYPE_CLASS_NUMBER, "0")
            ask(R.string.new_goods_receipt, form) {
                val item = selectedItem ?: return@ask toast(R.string.select_master_item)
                viewLifecycleOwner.lifecycleScope.launch {
                    val order = selectedOrder
                    val match = order?.let { chosen ->
                        app().vendorRepository.orderLines(chosen.id).firstOrNull { it.itemId == item.id }
                    }
                    runCatching {
                        app().vendorRepository.saveReceipt(
                            vendorId = vendorId,
                            orderId = order?.id,
                            notes = "",
                            receivedAt = System.currentTimeMillis(),
                            lines = listOf(
                                ReceiptLineInput(
                                    orderLineId = match?.id,
                                    itemId = item.id,
                                    itemName = item.name,
                                    orderedQty = match?.quantity ?: 0,
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
    }

    private fun createInvoice() {
        withItems { items ->
            var selected: Item? = null
            val form = FinanceForm.column(this)
            val number = FinanceForm.text(this, form, getString(R.string.invoice_number_label))
            FinanceForm.itemPicker(this, form, getString(R.string.item_name), items) { selected = it }
            val qty = FinanceForm.text(this, form, getString(R.string.ordered_qty), InputType.TYPE_CLASS_NUMBER, "1")
            val rate = FinanceForm.text(this, form, getString(R.string.rate_label), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            val delivery = FinanceForm.text(this, form, getString(R.string.delivery_charges), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, "0")
            ask(R.string.new_purchase_invoice, form) {
                val item = selected ?: return@ask toast(R.string.select_master_item)
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
                            lines = listOf(InvoiceLineInput(item.id, item.name, quantity, ratePaise))
                        )
                    }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
                    reload()
                }
            }
        }
    }

    private fun createQuote() {
        withItems { items ->
            var selected: Item? = null
            val form = FinanceForm.column(this)
            FinanceForm.itemPicker(this, form, getString(R.string.item_name), items) { selected = it }
            val rate = FinanceForm.text(this, form, getString(R.string.rate_label), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            val qty = FinanceForm.text(this, form, getString(R.string.available_qty), InputType.TYPE_CLASS_NUMBER, "1")
            val delivery = FinanceForm.text(this, form, getString(R.string.delivery_charges), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, "0")
            val discount = FinanceForm.text(this, form, getString(R.string.quote_discount), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, "0")
            ask(R.string.new_quote, form) {
                val item = selected ?: return@ask toast(R.string.select_master_item)
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
                                    item.id,
                                    item.name,
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
    }

    private fun compare() {
        viewLifecycleOwner.lifecycleScope.launch {
            val quotes = app().vendorRepository.quotes()
                .filter { it.requestId == null && (vendorId == 0L || it.vendorId == vendorId) }
                .map { it.id }
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

    private fun withItems(block: (List<Item>) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = app().itemRepository.getAllSync()
            if (items.isEmpty()) {
                toast(R.string.no_master_items)
                return@launch
            }
            block(items)
        }
    }

    private fun ask(title: Int, form: View, onSave: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(form)
            .setPositiveButton(R.string.save) { _, _ -> onSave() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(res: Int) = toast(getString(res))

    private fun app() = requireActivity().application as VikrSaathiApp

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_VENDOR_ID = "vendorId"
        const val ARG_ORDER_ID = "orderId"
        const val ARG_START = "startAction"
        const val START_RECEIPT = "receipt"
        const val START_ORDER = "order"
    }
}
