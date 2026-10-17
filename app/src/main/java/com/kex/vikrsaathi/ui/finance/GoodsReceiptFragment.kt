package com.kex.vikrsaathi.ui.finance

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.PurchaseOrder
import com.kex.vikrsaathi.data.entity.PurchaseOrderLine
import com.kex.vikrsaathi.data.entity.PurchaseRequestLine
import com.kex.vikrsaathi.data.repository.ReceiptLineInput
import com.kex.vikrsaathi.databinding.FragmentGoodsReceiptBinding
import com.kex.vikrsaathi.databinding.ItemReceiptLineBinding
import com.kex.vikrsaathi.domain.finance.FinanceException
import com.kex.vikrsaathi.domain.finance.HubOrderLineInput
import com.kex.vikrsaathi.domain.finance.HubReceiptInput
import com.kex.vikrsaathi.domain.finance.OrderHubMath
import com.kex.vikrsaathi.domain.finance.PackMath
import com.kex.vikrsaathi.domain.finance.PurchaseMath
import com.kex.vikrsaathi.domain.finance.ReceiptQtyInput
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.ui.orders.statusColor
import com.kex.vikrsaathi.ui.orders.statusLabel
import kotlinx.coroutines.launch

class GoodsReceiptFragment : Fragment() {

    private var _binding: FragmentGoodsReceiptBinding? = null
    private val binding get() = _binding!!
    private var orderId: Long = 0L
    private var order: PurchaseOrder? = null
    private var saving = false
    private val cards = mutableListOf<ReceiptCard>()

    override fun onCreateView(inflater: LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: android.os.Bundle?): View {
        _binding = FragmentGoodsReceiptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.VENDORS)
        orderId = arguments?.getLong(ARG_ORDER_ID) ?: 0L
        ViewCompat.setOnApplyWindowInsetsListener(binding.receiptActions) { insetView, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            insetView.updatePadding(bottom = (ime - nav).coerceAtLeast(0) + dp(12))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.buttonCancelReceipt.setOnClickListener { findNavController().popBackStack() }
        binding.buttonSaveReceipt.setOnClickListener { save() }
        reload()
    }

    private fun reload() {
        binding.progressReceipt.isVisible = true
        binding.receiptScroll.isVisible = false
        binding.receiptActions.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = app().vendorRepository
            val loaded = repository.orders().firstOrNull { it.id == orderId }
            val current = _binding ?: return@launch
            if (loaded == null) {
                findNavController().popBackStack()
                return@launch
            }
            val orderLines = repository.orderLines(loaded.id)
            val vendor = repository.getById(loaded.vendorId)
            val receipts = repository.receipts()
            val receiptById = receipts.associateBy { it.id }
            val hubLines = orderLines.map { HubOrderLineInput(it.id, it.itemId, it.quantity, it.ratePaise, it.unitsPerBundle) }
            val received = OrderHubMath.receivedQuantities(
                loaded.id,
                hubLines,
                repository.allReceiptLines().map { line ->
                    HubReceiptInput(
                        orderId = receiptById[line.receiptId]?.orderId,
                        orderLineId = line.orderLineId,
                        itemId = line.itemId,
                        receivedQty = line.receivedQty,
                        unitsPerBundle = line.unitsPerBundle
                    )
                }
            )
            val requestLines = loaded.requestId?.let { repository.requestDetail(it)?.lines }.orEmpty()
                .associateBy { it.id }
            val items = app().itemRepository.getAllSync().associateBy { it.id }
            val status = OrderHubMath.purchaseOrderStatus(hubLines, received)
            val open = orderLines.filter { line ->
                val orderedBase = PackMath.toBase(line.quantity, line.unitsPerBundle) ?: return@filter false
                (received[line.id] ?: 0) < orderedBase
            }
            if (!OrderHubMath.showReceive(status) || open.isEmpty()) {
                findNavController().popBackStack()
                return@launch
            }
            order = loaded
            current.progressReceipt.isVisible = false
            current.receiptScroll.isVisible = true
            current.receiptActions.isVisible = true
            current.textReceiptOrder.text = loaded.orderNumber
            current.textReceiptVendor.text = vendor?.name ?: getString(R.string.orders_removed_vendor)
            current.textReceiptStatus.setText(statusLabel(status))
            current.textReceiptStatus.setTextColor(requireContext().getColor(statusColor(status)))
            current.textReceiptItemCount.text = resources.getQuantityString(
                R.plurals.request_item_count,
                orderLines.size,
                orderLines.size
            )
            val started = orderLines.count { (received[it.id] ?: 0) > 0 }
            current.textReceiptProgress.text = getString(R.string.receipt_progress, started, orderLines.size)
            current.barReceiptProgress.progress = receivedPercent(orderLines, received)
            current.textReceiptLinesHeading.text = getString(R.string.receipt_items_heading, open.size)
            current.receiptLines.removeAllViews()
            cards.clear()
            open.forEach { line ->
                cards += bindLine(current, line, received[line.id] ?: 0, requestLines[line.requestLineId], items[line.itemId])
            }
        }
    }

    private fun bindLine(
        parent: FragmentGoodsReceiptBinding,
        line: PurchaseOrderLine,
        receivedBase: Int,
        requestLine: PurchaseRequestLine?,
        item: Item?
    ): ReceiptCard {
        val card = ItemReceiptLineBinding.inflate(layoutInflater, parent.receiptLines, true)
        val factor = line.unitsPerBundle.coerceAtLeast(1)
        val orderedBase = PackMath.toBase(line.quantity, factor) ?: 0
        val remainingBase = (orderedBase - receivedBase).coerceAtLeast(0)
        val remainingPackages = remainingBase / factor
        val baseUnit = acceptedUnit(line, requestLine, item)
        card.textLineName.text = line.itemName
        val spec = requestLine?.notes?.trim().orEmpty()
        card.textLineSpec.isVisible = spec.isNotBlank()
        card.textLineSpec.text = spec
        card.textLineOrdered.text = getString(R.string.po_ordered, orderedLabel(line.quantity, line.unitName, factor, baseUnit))
        card.textLineRemaining.text = getString(
            R.string.receipt_previously,
            baseLabel(receivedBase, line.unitName, factor),
            baseLabel(remainingBase, line.unitName, factor)
        )
        card.layoutReceived.hint = quantityHint(R.string.received_qty, line.unitName)
        card.layoutDamaged.hint = quantityHint(R.string.damaged_qty, line.unitName)
        card.layoutRejected.hint = quantityHint(R.string.rejected_qty, line.unitName)
        card.imageLineExpand.rotation = 90f
        card.editReceived.setText(remainingPackages.toString())
        card.editDamaged.setText("0")
        card.editRejected.setText("0")
        val draft = ReceiptCard(line, card, baseUnit)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                card.textLineError.isVisible = false
                refreshAccepted(draft)
            }
        }
        listOf(card.editReceived, card.editDamaged, card.editRejected).forEach { field ->
            field.addTextChangedListener(watcher)
            field.setOnFocusChangeListener { focused, hasFocus ->
                if (hasFocus) {
                    focused.post {
                        focused.requestRectangleOnScreen(android.graphics.Rect(0, 0, focused.width, focused.height), true)
                    }
                }
            }
        }
        card.receiptLineHeader.setOnClickListener { toggle(draft) }
        refreshAccepted(draft)
        return draft
    }

    private fun toggle(card: ReceiptCard) {
        val show = !card.binding.groupLineEntry.isVisible
        card.binding.groupLineEntry.isVisible = show
        card.binding.imageLineExpand.rotation = if (show) 90f else 0f
        card.binding.imageLineExpand.contentDescription = getString(
            if (show) R.string.receipt_collapse else R.string.receipt_expand
        )
    }

    private fun refreshAccepted(card: ReceiptCard) {
        val received = qty(card.binding.editReceived)
        val damaged = qty(card.binding.editDamaged)
        val rejected = qty(card.binding.editRejected)
        val accepted = (received - damaged - rejected).coerceAtLeast(0)
        val base = PackMath.toBase(accepted, card.line.unitsPerBundle) ?: 0
        card.binding.textAccepted.text = getString(R.string.pack_accepted_base, base, card.baseUnit)
        card.binding.textAcceptedMath.text = getString(R.string.receipt_accepted_math, received, damaged, rejected)
    }

    private fun save() {
        if (saving) return
        val loaded = order ?: return
        var invalid: ReceiptCard? = null
        cards.forEach { card ->
            val message = quantityError(card)
            card.binding.textLineError.isVisible = message != null
            card.binding.textLineError.text = message.orEmpty()
            if (message != null && invalid == null) invalid = card
        }
        val problem = invalid
        if (problem != null) {
            problem.binding.groupLineEntry.isVisible = true
            problem.binding.root.requestRectangleOnScreen(
                android.graphics.Rect(0, 0, problem.binding.root.width, problem.binding.root.height),
                true
            )
            return
        }
        val inputs = cards.mapNotNull { card ->
            val received = qty(card.binding.editReceived)
            val damaged = qty(card.binding.editDamaged)
            val rejected = qty(card.binding.editRejected)
            if (received == 0 && damaged == 0 && rejected == 0) return@mapNotNull null
            ReceiptLineInput(
                orderLineId = card.line.id,
                itemId = card.line.itemId,
                itemName = card.line.itemName,
                orderedQty = card.line.quantity,
                receivedQty = received,
                damagedQty = damaged,
                rejectedQty = rejected,
                unitName = card.line.unitName,
                unitsPerBundle = card.line.unitsPerBundle
            )
        }
        if (inputs.isEmpty()) {
            toast(R.string.compare_nothing_selected)
            return
        }
        saving = true
        binding.buttonSaveReceipt.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                app().vendorRepository.saveReceipt(
                    vendorId = loaded.vendorId,
                    orderId = loaded.id,
                    notes = "",
                    receivedAt = System.currentTimeMillis(),
                    lines = inputs
                )
            }.onSuccess {
                findNavController().popBackStack()
            }.onFailure {
                saving = false
                _binding?.buttonSaveReceipt?.isEnabled = true
                toast(FinanceUi.message(requireContext(), it))
            }
        }
    }

    private fun quantityError(card: ReceiptCard): String? {
        val received = qty(card.binding.editReceived)
        val damaged = qty(card.binding.editDamaged)
        val rejected = qty(card.binding.editRejected)
        if (received == 0 && damaged == 0 && rejected == 0) return null
        return try {
            PurchaseMath.receiptQuantities(
                ReceiptQtyInput(card.line.quantity, received, damaged, rejected)
            )
            null
        } catch (error: FinanceException) {
            FinanceUi.message(requireContext(), error)
        }
    }

    private fun receivedPercent(lines: List<PurchaseOrderLine>, received: Map<Long, Int>): Int {
        var orderedSum = 0L
        var receivedSum = 0L
        lines.forEach { line ->
            val ordered = PackMath.toBase(line.quantity, line.unitsPerBundle)?.toLong() ?: return@forEach
            if (ordered <= 0L) return@forEach
            orderedSum += ordered
            receivedSum += (received[line.id] ?: 0).toLong().coerceIn(0L, ordered)
        }
        if (orderedSum == 0L) return 0
        return ((receivedSum * 100L) / orderedSum).toInt().coerceIn(0, 100)
    }

    private fun acceptedUnit(line: PurchaseOrderLine, requestLine: PurchaseRequestLine?, item: Item?): String {
        if (line.unitsPerBundle <= 1) {
            return line.unitName.ifBlank { getString(R.string.pack_base_unit) }
        }
        val named = requestLine?.baseUnit?.trim().orEmpty().ifBlank { item?.stockUnit()?.trim().orEmpty() }
        return named.ifBlank { getString(R.string.pack_base_unit) }
    }

    private fun orderedLabel(quantity: Int, unit: String, factor: Int, baseUnit: String): String {
        val entered = packageLabel(quantity, unit, factor)
        val base = baseUnit.trim()
        return if (factor > 1 && base.isNotBlank() && base != getString(R.string.pack_base_unit)) {
            "$entered $base"
        } else {
            entered
        }
    }

    private fun quantityHint(labelRes: Int, unit: String): String {
        val label = getString(labelRes)
        return if (unit.isBlank()) label else "$label ($unit)"
    }

    private fun packageLabel(quantity: Int, unit: String, factor: Int): String {
        val entered = if (unit.isBlank()) quantity.toString() else "$quantity ${unit.trim()}"
        return if (factor <= 1) entered else "$entered × $factor"
    }

    private fun baseLabel(base: Int, unit: String, factor: Int): String {
        if (factor <= 1) return packageLabel(base, unit, 1)
        val packs = base / factor
        val extra = base % factor
        val packsLabel = packageLabel(packs, unit, 1)
        return if (extra == 0) packsLabel else "$packsLabel + $extra"
    }

    private fun qty(field: android.widget.EditText): Int = field.text?.toString()?.toIntOrNull() ?: 0

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun app() = requireActivity().application as VikrSaathiApp
    private fun toast(res: Int) = Toast.makeText(requireContext(), res, Toast.LENGTH_LONG).show()
    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    override fun onDestroyView() {
        cards.clear()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_ORDER_ID = "orderId"
    }
}

private class ReceiptCard(
    val line: PurchaseOrderLine,
    val binding: ItemReceiptLineBinding,
    val baseUnit: String
)
