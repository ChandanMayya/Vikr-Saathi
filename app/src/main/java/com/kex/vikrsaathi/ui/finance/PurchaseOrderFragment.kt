package com.kex.vikrsaathi.ui.finance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.PurchaseOrder
import com.kex.vikrsaathi.data.entity.PurchaseOrderLine
import com.kex.vikrsaathi.databinding.FragmentPurchaseOrderBinding
import com.kex.vikrsaathi.databinding.ItemRecordCardBinding
import com.kex.vikrsaathi.domain.finance.HubOrderLineInput
import com.kex.vikrsaathi.domain.finance.HubReceiptInput
import com.kex.vikrsaathi.domain.finance.OrderHubMath
import com.kex.vikrsaathi.domain.finance.PackMath
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.ui.orders.statusColor
import com.kex.vikrsaathi.ui.orders.statusLabel
import kotlinx.coroutines.launch

class PurchaseOrderFragment : Fragment() {

    private var _binding: FragmentPurchaseOrderBinding? = null
    private val binding get() = _binding!!
    private var orderId: Long = 0L
    private var promptReceipt = false
    private var order: PurchaseOrder? = null
    private var lines: List<PurchaseOrderLine> = emptyList()
    private var receivedByLine: Map<Long, Int> = emptyMap()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && arguments?.getBoolean(ARG_START_RECEIPT) == true) {
            promptReceipt = true
            arguments?.putBoolean(ARG_START_RECEIPT, false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: android.os.Bundle?): View {
        _binding = FragmentPurchaseOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.VENDORS)
        orderId = arguments?.getLong(ARG_ORDER_ID) ?: 0L
        binding.buttonReceiveGoods.setOnClickListener { receiveGoods() }
        binding.textPoRequest.setOnClickListener {
            val requestId = order?.requestId ?: return@setOnClickListener
            findNavController().navigate(
                R.id.purchaseRequestFragment,
                bundleOf(PurchaseRequestFragment.ARG_REQUEST_ID to requestId)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        binding.progressOrder.isVisible = true
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
            val requestNumber = loaded.requestId?.let { id ->
                repository.requestSummaries().firstOrNull { it.request.id == id }?.request?.requestNumber
            }
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
            val status = OrderHubMath.purchaseOrderStatus(hubLines, received)
            val symbol = app().settingsRepository.currencySymbol
            order = loaded
            lines = orderLines
            receivedByLine = received
            current.progressOrder.isVisible = false
            current.textPoNumber.text = loaded.orderNumber
            current.textPoStatus.setText(statusLabel(status))
            current.textPoStatus.setTextColor(requireContext().getColor(statusColor(status)))
            current.textPoMeta.text = listOf(
                vendor?.name ?: getString(R.string.orders_removed_vendor),
                FinanceDates.format(loaded.createdAt)
            ).joinToString(" · ")
            val amount = if (orderLines.isEmpty()) null else orderLines.sumOf { it.ratePaise * it.quantity.toLong() }
            current.textPoAmount.isVisible = amount != null
            if (amount != null) current.textPoAmount.text = FinanceUi.formatPaise(amount, symbol)
            current.textPoRequest.isVisible = requestNumber != null
            current.textPoRequest.text = requestNumber?.let { getString(R.string.po_linked_request, it) }
            current.poLines.removeAllViews()
            orderLines.forEach { line ->
                lineCard(current.poLines, line, received[line.id] ?: 0)
            }
            val orderReceipts = receipts.filter { it.orderId == loaded.id }
            current.textReceiptHeading.isVisible = orderReceipts.isNotEmpty()
            current.poReceipts.removeAllViews()
            orderReceipts.forEach { receipt ->
                infoCard(current.poReceipts, receipt.receiptNumber, FinanceDates.format(receipt.receivedAt))
            }
            val invoices = repository.invoices().filter { it.orderId == loaded.id }
            current.textInvoiceHeading.isVisible = invoices.isNotEmpty()
            current.poInvoices.removeAllViews()
            invoices.forEach { invoice ->
                infoCard(
                    current.poInvoices,
                    invoice.invoiceNumber,
                    FinanceDates.format(invoice.invoiceDate) + " · " + FinanceUi.formatPaise(invoice.totalPaise, symbol)
                )
            }
            current.buttonReceiveGoods.isVisible = OrderHubMath.showReceive(status)
            if (promptReceipt && OrderHubMath.showReceive(status)) {
                promptReceipt = false
                receiveGoods()
            }
        }
    }

    private fun lineCard(parent: ViewGroup, line: PurchaseOrderLine, receivedBase: Int) {
        val orderedBase = PackMath.toBase(line.quantity, line.unitsPerBundle)
        val remaining = if (orderedBase == null) null else (orderedBase - receivedBase).coerceAtLeast(0)
        val body = listOf(
            getString(R.string.po_ordered, packageLabel(line.quantity, line.unitName, line.unitsPerBundle)),
            getString(R.string.po_received, baseLabel(receivedBase, line.unitName, line.unitsPerBundle)),
            if (remaining == null) {
                getString(R.string.compare_not_comparable)
            } else {
                getString(R.string.po_remaining, baseLabel(remaining, line.unitName, line.unitsPerBundle))
            }
        ).joinToString("\n")
        infoCard(parent, line.itemName, body)
    }

    private fun infoCard(parent: ViewGroup, title: String, body: String) {
        val card = ItemRecordCardBinding.inflate(layoutInflater, parent, true)
        card.textRecordTitle.text = title
        card.textRecordBody.text = body
    }

    private fun receiveGoods() {
        if (order == null) return
        findNavController().navigate(
            R.id.goodsReceiptFragment,
            bundleOf(GoodsReceiptFragment.ARG_ORDER_ID to orderId)
        )
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

    private fun app() = requireActivity().application as VikrSaathiApp

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_ORDER_ID = "orderId"
        const val ARG_START_RECEIPT = "startReceipt"
    }
}
