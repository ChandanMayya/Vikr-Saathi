package com.kex.vikrsaathi.ui.finance

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import com.kex.vikrsaathi.data.repository.ReceiptLineInput
import com.kex.vikrsaathi.databinding.FragmentPartyLedgerBinding
import com.kex.vikrsaathi.databinding.FragmentPurchasingBinding
import com.kex.vikrsaathi.databinding.ItemRecordCardBinding
import com.kex.vikrsaathi.domain.finance.HubOrderLineInput
import com.kex.vikrsaathi.domain.finance.HubReceiptInput
import com.kex.vikrsaathi.domain.finance.OrderHubMath
import com.kex.vikrsaathi.domain.finance.PackMath
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
        binding.buttonShareStatement.setText(R.string.purchase_history)
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
            findNavController().navigate(R.id.ordersFragment)
        }
        binding.buttonNewReceipt.setOnClickListener { createReceipt() }
        binding.buttonNewInvoice.setOnClickListener { createInvoice() }
        if (savedInstanceState == null && arguments?.getString(ARG_START) == START_RECEIPT) {
            val linkedOrder = arguments?.getLong(ARG_ORDER_ID) ?: 0L
            if (linkedOrder != 0L) {
                findNavController().navigate(
                    R.id.purchaseOrderFragment,
                    bundleOf(
                        PurchaseOrderFragment.ARG_ORDER_ID to linkedOrder,
                        PurchaseOrderFragment.ARG_START_RECEIPT to true
                    )
                )
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
            val requestNumbers = app.vendorRepository.requestSummaries().associate { it.request.id to it.request.requestNumber }
            val orderNumbers = orders.associate { it.id to it.orderNumber }
            if (_binding == null) return@launch
            binding.historyGroup.removeAllViews()
            addHistorySection(R.string.history_orders, orders.map { order ->
                HistoryRow(order.orderNumber, FinanceDates.format(order.createdAt)) {
                    findNavController().navigate(
                        R.id.purchaseOrderFragment,
                        bundleOf(PurchaseOrderFragment.ARG_ORDER_ID to order.id)
                    )
                }
            })
            addHistorySection(R.string.history_receipts, receipts.map { receipt ->
                val link = receipt.orderId?.let { orderNumbers[it] } ?: getString(R.string.direct_receipt_label)
                HistoryRow(receipt.receiptNumber, FinanceDates.format(receipt.receivedAt) + " · " + link, null)
            })
            addHistorySection(R.string.history_invoices, invoices.map { invoice ->
                HistoryRow(
                    invoice.invoiceNumber,
                    FinanceDates.format(invoice.invoiceDate) + " · " + FinanceUi.formatPaise(invoice.totalPaise, symbol),
                    null
                )
            })
            addHistorySection(R.string.history_quotes, quotes.map { quote ->
                val title = quote.requestId?.let { requestNumbers[it] }?.let { getString(R.string.linked_request_quote, it) }
                    ?: getString(R.string.legacy_quote)
                HistoryRow(title, FinanceDates.format(quote.quoteDate), null)
            })
        }
    }

    private fun addHistorySection(titleRes: Int, rows: List<HistoryRow>) {
        if (rows.isEmpty()) return
        binding.historyGroup.addView(TextView(requireContext()).apply {
            text = getString(titleRes)
            setTextColor(context.getColor(R.color.text_primary_dark))
            textSize = 14f
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        })
        rows.forEach { row ->
            val card = ItemRecordCardBinding.inflate(layoutInflater, binding.historyGroup, true)
            card.textRecordTitle.text = row.title
            card.textRecordBody.text = row.body
            if (row.onClick != null) card.root.setOnClickListener { row.onClick.invoke() }
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
            var pack: PackFields? = null
            var receivedField: com.google.android.material.textfield.TextInputEditText? = null
            var damagedField: com.google.android.material.textfield.TextInputEditText? = null
            var rejectedField: com.google.android.material.textfield.TextInputEditText? = null
            val linesByOrder = orders.associate { it.id to app().vendorRepository.orderLines(it.id) }
            val form = FinanceForm.column(this@PurchasingFragment)
            fun matchedLine() = selectedOrder?.let { linesByOrder[it.id] }?.firstOrNull { it.itemId != null && it.itemId == selectedItem?.id }
            fun refreshPack() {
                val fields = pack ?: return
                val line = matchedLine()
                if (line != null) {
                    PackForm.apply(fields, line.unitName, line.unitsPerBundle, locked = true)
                } else {
                    PackForm.prefill(fields, selectedItem)
                }
                val unit = fields.unit.text?.toString().orEmpty()
                val factor = fields.factor.text?.toString()?.toIntOrNull() ?: 1
                listOf(
                    receivedField to R.string.received_qty,
                    damagedField to R.string.damaged_qty,
                    rejectedField to R.string.rejected_qty
                ).forEach { (field, label) ->
                    val layout = field?.parent as? com.google.android.material.textfield.TextInputLayout ?: return@forEach
                    layout.hint = if (unit.isBlank()) getString(label) else "${getString(label)} ($unit)"
                }
                val receivedQty = receivedField?.text?.toString()?.toIntOrNull() ?: 0
                val damagedQty = damagedField?.text?.toString()?.toIntOrNull() ?: 0
                val rejectedQty = rejectedField?.text?.toString()?.toIntOrNull() ?: 0
                val accepted = (receivedQty - damagedQty - rejectedQty).coerceAtLeast(0)
                val acceptedBase = PackMath.toBase(accepted, factor)
                fields.preview.text = buildString {
                    append(PackMath.formatQuantity(receivedQty, unit, factor, selectedItem?.stockUnit().orEmpty()))
                    append(" · ")
                    append(getString(R.string.pack_accepted_base, acceptedBase ?: 0, selectedItem?.stockUnit()?.ifBlank { unit }.orEmpty()))
                }
            }
            val orderLabels = listOf(getString(R.string.purchase_order_none)) + orders.map { it.orderNumber }
            val orderField = FinanceForm.picker(this@PurchasingFragment, form, getString(R.string.link_order_optional), orderLabels) { index ->
                selectedOrder = if (index == 0) null else orders[index - 1]
                refreshPack()
            }
            val prefill = orders.firstOrNull { it.id == prefillOrderId }
            if (prefill != null) {
                selectedOrder = prefill
                orderField.setText(prefill.orderNumber, false)
            }
            val itemField = FinanceForm.itemPicker(this@PurchasingFragment, form, getString(R.string.item_name), items) {
                selectedItem = it
                refreshPack()
            }
            if (prefill != null) {
                val lines = linesByOrder[prefill.id].orEmpty()
                val receipts = app().vendorRepository.receipts().associateBy { it.id }
                val receivedByLine = OrderHubMath.receivedQuantities(
                    prefill.id,
                    lines.map { HubOrderLineInput(it.id, it.itemId, it.quantity, it.ratePaise, it.unitsPerBundle) },
                    app().vendorRepository.allReceiptLines().map { line ->
                        HubReceiptInput(
                            orderId = receipts[line.receiptId]?.orderId,
                            orderLineId = line.orderLineId,
                            itemId = line.itemId,
                            receivedQty = line.receivedQty,
                            unitsPerBundle = line.unitsPerBundle
                        )
                    }
                )
                val next = lines.firstOrNull { line ->
                    val orderedBase = PackMath.toBase(line.quantity, line.unitsPerBundle) ?: Int.MAX_VALUE
                    (receivedByLine[line.id] ?: 0) < orderedBase
                } ?: lines.firstOrNull()
                val item = next?.itemId?.let { id -> items.firstOrNull { it.id == id } }
                if (item != null) {
                    selectedItem = item
                    itemField.setText(item.name, false)
                }
            }
            val received = FinanceForm.text(this@PurchasingFragment, form, getString(R.string.received_qty), InputType.TYPE_CLASS_NUMBER)
            val damaged = FinanceForm.text(this@PurchasingFragment, form, getString(R.string.damaged_qty), InputType.TYPE_CLASS_NUMBER, "0")
            val rejected = FinanceForm.text(this@PurchasingFragment, form, getString(R.string.rejected_qty), InputType.TYPE_CLASS_NUMBER, "0")
            receivedField = received
            damagedField = damaged
            rejectedField = rejected
            val packFields = PackForm.add(this@PurchasingFragment, form)
            pack = packFields
            PackForm.watch(packFields, listOf(received, damaged, rejected)) { refreshPack() }
            ask(R.string.new_goods_receipt, form) {
                val item = selectedItem ?: return@ask toast(R.string.select_master_item)
                val chosen = PackForm.read(packFields) ?: return@ask toast(R.string.payment_error_pack)
                viewLifecycleOwner.lifecycleScope.launch {
                    val order = selectedOrder
                    val match = order?.let { linesByOrder[it.id] }?.firstOrNull { it.itemId == item.id }
                    val unitName = match?.unitName ?: chosen.first
                    val factor = match?.unitsPerBundle ?: chosen.second
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
                                    rejectedQty = rejected.text.toString().toIntOrNull() ?: 0,
                                    unitName = unitName,
                                    unitsPerBundle = factor
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
            var pack: PackFields? = null
            val form = FinanceForm.column(this)
            val number = FinanceForm.text(this, form, getString(R.string.invoice_number_label))
            FinanceForm.itemPicker(this, form, getString(R.string.item_name), items) { item ->
                selected = item
                pack?.let { PackForm.prefill(it, item) }
            }
            val qty = FinanceForm.text(this, form, getString(R.string.ordered_qty), InputType.TYPE_CLASS_NUMBER, "1")
            val rate = FinanceForm.text(this, form, getString(R.string.rate_label), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            val packFields = PackForm.add(this, form)
            pack = packFields
            PackForm.watch(packFields, listOf(qty, rate)) {
                val unit = packFields.unit.text?.toString().orEmpty()
                val factor = packFields.factor.text?.toString()?.toIntOrNull() ?: 1
                PackForm.setRateHint(rate, unit)
                val quantity = qty.text?.toString()?.toIntOrNull() ?: 0
                val total = PackMath.lineTotalPaise(quantity, FinanceUi.parseAmountPaise(rate.text?.toString()))
                packFields.preview.text = PackMath.formatQuantity(quantity, unit, factor, selected?.stockUnit().orEmpty()) +
                    (total?.let { " · ${FinanceUi.formatPaise(it, app().settingsRepository.currencySymbol)}" }.orEmpty())
            }
            val delivery = FinanceForm.text(this, form, getString(R.string.delivery_charges), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, "0")
            ask(R.string.new_purchase_invoice, form) {
                val item = selected ?: return@ask toast(R.string.select_master_item)
                val chosen = PackForm.read(packFields) ?: return@ask toast(R.string.payment_error_pack)
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
                            lines = listOf(
                                InvoiceLineInput(item.id, item.name, quantity, ratePaise, chosen.first, chosen.second)
                            )
                        )
                    }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
                    reload()
                }
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

private data class HistoryRow(val title: String, val body: String, val onClick: (() -> Unit)?)
