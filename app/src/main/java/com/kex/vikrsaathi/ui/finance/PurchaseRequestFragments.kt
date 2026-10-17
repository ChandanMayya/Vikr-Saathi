package com.kex.vikrsaathi.ui.finance

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.GoodsReceipt
import com.kex.vikrsaathi.data.entity.GoodsReceiptLine
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.data.repository.PurchaseRequestDetail
import com.kex.vikrsaathi.data.repository.PurchaseRequestSummary
import com.kex.vikrsaathi.data.repository.RequestLineInput
import com.kex.vikrsaathi.databinding.FragmentPurchaseRequestBinding
import com.kex.vikrsaathi.databinding.FragmentPurchaseRequestsBinding
import com.kex.vikrsaathi.databinding.ItemPurchaseRequestBinding
import com.kex.vikrsaathi.databinding.ItemRequestLineBinding
import com.kex.vikrsaathi.databinding.ItemRequestOrderBinding
import com.kex.vikrsaathi.domain.finance.ComparedRequestLine
import com.kex.vikrsaathi.domain.finance.HubOrderLineInput
import com.kex.vikrsaathi.domain.finance.HubReceiptInput
import com.kex.vikrsaathi.domain.finance.LineOffer
import com.kex.vikrsaathi.domain.finance.OrderHubMath
import com.kex.vikrsaathi.domain.finance.OfferState
import com.kex.vikrsaathi.domain.finance.PackMath
import com.kex.vikrsaathi.domain.finance.PurchaseRequestMath
import com.kex.vikrsaathi.domain.finance.QuoteHeader
import com.kex.vikrsaathi.domain.finance.RequestComparison
import com.kex.vikrsaathi.domain.finance.RequestStatus
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import kotlinx.coroutines.launch

class PurchaseRequestListFragment : Fragment() {

    private var _binding: FragmentPurchaseRequestsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: android.os.Bundle?): View {
        _binding = FragmentPurchaseRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post {
            if (!isAdded) return@post
            findNavController().navigate(
                R.id.ordersFragment,
                bundleOf(com.kex.vikrsaathi.ui.orders.OrdersFragment.ARG_HUB_TYPE to com.kex.vikrsaathi.ui.orders.OrdersFragment.HUB_REQUESTS),
                androidx.navigation.navOptions {
                    popUpTo(R.id.purchaseRequestListFragment) { inclusive = true }
                }
            )
        }
    }

    private fun reload() {
        val shown = _binding ?: return
        shown.progressRequests.isVisible = true
        shown.textRequestsEmpty.isVisible = false
        shown.recyclerRequests.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            val rows = app().vendorRepository.requestSummaries()
            val current = _binding ?: return@launch
            current.progressRequests.isVisible = false
            current.textRequestsEmpty.isVisible = rows.isEmpty()
            current.recyclerRequests.isVisible = rows.isNotEmpty()
            current.recyclerRequests.adapter = RequestAdapter(rows) { summary ->
                findNavController().navigate(
                    R.id.purchaseRequestFragment,
                    bundleOf(PurchaseRequestFragment.ARG_REQUEST_ID to summary.request.id)
                )
            }
        }
    }

    private fun app() = requireActivity().application as VikrSaathiApp

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private class RequestAdapter(
    private val rows: List<PurchaseRequestSummary>,
    private val onClick: (PurchaseRequestSummary) -> Unit
) : RecyclerView.Adapter<RequestAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPurchaseRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.binding.textRequestNumber.text = row.request.requestNumber
        holder.binding.textRequestStatus.text = statusLabel(holder.itemView, row.request.status)
        holder.binding.textRequestMeta.text = holder.itemView.context.getString(
            R.string.request_meta,
            FinanceDates.format(row.request.createdAt),
            row.lineCount,
            row.quoteCount
        )
        holder.itemView.setOnClickListener { onClick(row) }
    }

    override fun getItemCount(): Int = rows.size

    class Holder(val binding: ItemPurchaseRequestBinding) : RecyclerView.ViewHolder(binding.root)
}

class PurchaseRequestFragment : Fragment() {

    private var _binding: FragmentPurchaseRequestBinding? = null
    private val binding get() = _binding!!
    private var requestId: Long = 0L
    private var requiredBy: Long? = null
    private val draftLines = mutableListOf<DraftLine>()
    private var loaded: PurchaseRequestDetail? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: android.os.Bundle?): View {
        _binding = FragmentPurchaseRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.VENDORS)
        requestId = arguments?.getLong(ARG_REQUEST_ID) ?: 0L
        binding.buttonRequiredBy.setOnClickListener {
            FinanceDates.showSingle(this, R.string.required_by) { picked ->
                requiredBy = picked
                binding.buttonRequiredBy.text = getString(R.string.required_by_value, FinanceDates.format(picked))
            }
        }
        binding.buttonClearRequiredBy.setOnClickListener {
            requiredBy = null
            binding.buttonRequiredBy.setText(R.string.required_by)
        }
        binding.buttonAddMaterial.setOnClickListener { addMaterial() }
        binding.buttonSaveRequest.setOnClickListener { saveDraft() }
        binding.buttonEditRequest.setOnClickListener { startEdit() }
        binding.buttonAddQuote.setOnClickListener { addQuote() }
        binding.buttonCompare.setOnClickListener { openCompare() }
        binding.buttonAward.setOnClickListener { openCompare() }
        binding.buttonCancelRequest.setOnClickListener { cancelRequest() }
        if (requestId == 0L) showEditor() else reload()
    }

    override fun onResume() {
        super.onResume()
        if (requestId != 0L && _binding?.detailGroup?.isVisible == true) reload()
    }

    private fun showEditor() {
        binding.editorGroup.isVisible = true
        binding.detailGroup.isVisible = false
        binding.progressRequest.isVisible = false
        renderDraftLines()
    }

    private fun reload() {
        binding.progressRequest.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            val detail = app().vendorRepository.requestDetail(requestId)
            val vendors = app().vendorRepository.getAll()
            val receipts = app().vendorRepository.receipts()
            val receiptLines = app().vendorRepository.allReceiptLines()
            val current = _binding ?: return@launch
            current.progressRequest.isVisible = false
            if (detail == null) {
                findNavController().popBackStack()
                return@launch
            }
            loaded = detail
            showDetail(detail, vendors, receipts, receiptLines)
        }
    }

    private fun showDetail(
        detail: PurchaseRequestDetail,
        vendors: List<Vendor>,
        receipts: List<GoodsReceipt>,
        receiptLines: List<GoodsReceiptLine>
    ) {
        binding.editorGroup.isVisible = false
        binding.detailGroup.isVisible = true
        val vendorNames = vendors.associate { it.id to it.name }
        val awarded = awardedBase(detail.orderLines)
        val receiptById = receipts.associateBy { it.id }
        binding.textRequestTitle.text = detail.request.requestNumber
        val orderStatuses = detail.orders.map { order ->
            val orderLines = detail.orderLines.filter { it.orderId == order.id }
            val hubLines = orderLines.map {
                HubOrderLineInput(it.id, it.itemId, it.quantity, it.ratePaise, it.unitsPerBundle)
            }
            OrderHubMath.purchaseOrderStatus(
                hubLines,
                OrderHubMath.receivedQuantities(
                    order.id,
                    hubLines,
                    receiptLines.map { line ->
                        HubReceiptInput(
                            orderId = receiptById[line.receiptId]?.orderId,
                            orderLineId = line.orderLineId,
                            itemId = line.itemId,
                            receivedQty = line.receivedQty,
                            unitsPerBundle = line.unitsPerBundle
                        )
                    }
                )
            )
        }
        bindRequestStatus(detail, orderStatuses)
        binding.textRequestCreated.text = FinanceDates.format(detail.request.createdAt)
        val requiredByDate = detail.request.requiredBy
        binding.groupRequestRequired.isVisible = requiredByDate != null
        if (requiredByDate != null) {
            binding.textRequestRequired.text = FinanceDates.format(requiredByDate)
        }
        binding.groupRequestNotes.isVisible = detail.request.notes.isNotBlank()
        binding.textRequestNotes.text = detail.request.notes
        binding.textRequestItemCount.text = resources.getQuantityString(
            R.plurals.request_item_count,
            detail.lines.size,
            detail.lines.size
        )
        binding.requestLines.removeAllViews()
        val symbol = app().settingsRepository.currencySymbol
        detail.lines.forEach { line ->
            val awardedQty = awarded[line.id] ?: 0
            val open = PurchaseRequestMath.remaining(line.quantity, awardedQty)
            val baseUnit = line.baseUnit.ifBlank { getString(R.string.pack_base_unit) }
            val card = ItemRequestLineBinding.inflate(layoutInflater, binding.requestLines, true)
            card.textRequestItemName.text = line.itemName
            card.textRequestItemSpec.isVisible = line.notes.isNotBlank()
            card.textRequestItemSpec.text = line.notes
            card.textRequestItemQty.text = if (line.enteredQty > 0) {
                val unit = line.orderUnit.ifBlank { baseUnit }
                if (unit.isBlank()) line.enteredQty.toString() else "${line.enteredQty} $unit"
            } else {
                PackMath.formatQuantity(line.quantity, baseUnit, 1)
            }
            val showConversion = line.enteredQty > 0 && line.unitsPerBundle > 1
            card.groupRequestConversion.isVisible = showConversion
            if (showConversion) {
                val orderUnit = line.orderUnit.ifBlank { baseUnit }
                card.textRequestItemConversion.text = getString(
                    R.string.request_line_conversion,
                    line.unitsPerBundle,
                    baseUnit,
                    orderUnit,
                    line.quantity,
                    baseUnit
                )
            }
            val openLabel = PackMath.formatQuantity(open, baseUnit, 1)
            val openText = getString(R.string.request_line_open, openLabel)
            card.textRequestItemOpen.text = if (awardedQty > 0) {
                openText + " · " + getString(
                    R.string.request_line_awarded,
                    PackMath.formatQuantity(awardedQty, baseUnit, 1)
                )
            } else {
                openText
            }
            val showAwardBar = line.quantity > 0 && awardedQty > 0
            card.barRequestAwarded.isVisible = showAwardBar
            if (showAwardBar) {
                card.barRequestAwarded.max = line.quantity
                card.barRequestAwarded.progress = awardedQty.coerceAtMost(line.quantity)
            }
        }
        binding.textQuoteCount.text = getString(R.string.request_quote_count, detail.quotes.size)
        val canOpenCompare = detail.quotes.isNotEmpty()
        binding.imageQuoteOpen.isVisible = canOpenCompare
        binding.groupSupplierQuotes.isClickable = canOpenCompare
        binding.groupSupplierQuotes.isFocusable = canOpenCompare
        binding.groupSupplierQuotes.setOnClickListener(if (canOpenCompare) View.OnClickListener { openCompare() } else null)
        binding.groupSupplierQuotes.contentDescription = getString(R.string.request_quotes_heading) + ". " +
            binding.textQuoteCount.text +
            if (canOpenCompare) ". " + getString(R.string.request_quotes_open_compare) else ""
        val awardedOrders = detail.orders.isNotEmpty()
        binding.textOrdersHeading.isVisible = awardedOrders
        binding.textAwardIntro.isVisible = awardedOrders
        binding.requestOrders.removeAllViews()
        detail.orders.forEach { order ->
            val orderLines = detail.orderLines.filter { it.orderId == order.id }
            val hubLines = orderLines.map {
                HubOrderLineInput(it.id, it.itemId, it.quantity, it.ratePaise, it.unitsPerBundle)
            }
            val received = OrderHubMath.receivedQuantities(
                order.id,
                hubLines,
                receiptLines.map { line ->
                    HubReceiptInput(
                        orderId = receiptById[line.receiptId]?.orderId,
                        orderLineId = line.orderLineId,
                        itemId = line.itemId,
                        receivedQty = line.receivedQty,
                        unitsPerBundle = line.unitsPerBundle
                    )
                }
            )
            val poStatus = OrderHubMath.purchaseOrderStatus(hubLines, received)
            val card = ItemRequestOrderBinding.inflate(layoutInflater, binding.requestOrders, true)
            val vendor = vendorNames[order.vendorId].orEmpty().ifBlank { getString(R.string.orders_removed_vendor) }
            card.textRecordTitle.text = vendor
            card.textRecordBadge.isVisible = true
            card.textRecordBadge.setText(com.kex.vikrsaathi.ui.orders.statusLabel(poStatus))
            card.textRecordBadge.setTextColor(requireContext().getColor(com.kex.vikrsaathi.ui.orders.statusColor(poStatus)))
            val lines = orderLines.joinToString("\n") { line ->
                val qty = if (line.unitsPerBundle <= 1) {
                    if (line.unitName.isBlank()) line.quantity.toString() else "${line.quantity} ${line.unitName}"
                } else {
                    "${line.quantity} ${line.unitName} × ${line.unitsPerBundle}"
                }
                val rate = FinanceUi.formatPaise(line.ratePaise, symbol) +
                    if (line.unitName.isBlank()) "" else "/${line.unitName}"
                "${line.itemName} · $qty · $rate"
            }
            val next = if (OrderHubMath.showReceive(poStatus)) {
                getString(R.string.request_next_receive)
            } else {
                getString(R.string.request_received_full)
            }
            card.textRecordBody.text = order.orderNumber + "\n" + lines + "\n" + next
            val canReceive = OrderHubMath.showReceive(poStatus)
            card.buttonReceiveItems.isVisible = canReceive
            card.buttonReceiveItems.setOnClickListener { openReceipt(order.id) }
            card.root.setOnClickListener { openOrder(order.id) }
            card.root.contentDescription = vendor + ". " + order.orderNumber + ". " + next
        }
        val editable = detail.request.status == RequestStatus.DRAFT &&
            detail.quotes.isEmpty() &&
            detail.orderLines.isEmpty()
        val open = detail.request.status != RequestStatus.CANCELLED &&
            detail.request.status != RequestStatus.AWARDED
        val compared = comparison(detail, vendorNames)
        val canAward = open && compared.rows.any { row ->
            val lineAwarded = awarded[row.requestLineId] ?: 0
            PurchaseRequestMath.remaining(row.requestedQty, lineAwarded) > 0 &&
                row.cells.any { it.state == OfferState.QUOTED && (it.availableQty ?: 0) > 0 }
        }
        binding.buttonCompare.isVisible = false
        binding.buttonAward.isVisible = false
        binding.buttonEditRequest.isVisible = false
        binding.buttonAddQuote.isVisible = false
        binding.buttonCancelRequest.isVisible = detail.orderLines.isEmpty() && open
        binding.textQuoteHint.isVisible = canAward
        when {
            editable -> {
                binding.buttonAddQuote.isVisible = true
                binding.buttonAddQuote.setText(R.string.add_vendor_quote)
                binding.buttonAddQuote.setOnClickListener { addQuote() }
                binding.buttonEditRequest.isVisible = true
                binding.buttonEditRequest.setText(R.string.edit_request)
                binding.buttonEditRequest.setOnClickListener { startEdit() }
            }
            canAward -> {
                binding.buttonAddQuote.isVisible = true
                binding.buttonAddQuote.setText(R.string.compare_and_award)
                binding.buttonAddQuote.setOnClickListener { openCompare() }
                binding.buttonEditRequest.isVisible = true
                binding.buttonEditRequest.setText(R.string.add_vendor_quote)
                binding.buttonEditRequest.setOnClickListener { addQuote() }
            }
            open -> {
                binding.buttonAddQuote.isVisible = true
                binding.buttonAddQuote.setText(R.string.add_vendor_quote)
                binding.buttonAddQuote.setOnClickListener { addQuote() }
            }
        }
    }

    private fun bindRequestStatus(
        detail: PurchaseRequestDetail,
        orderStatuses: List<com.kex.vikrsaathi.domain.finance.HubRecordStatus>
    ) {
        val display = OrderHubMath.requestDisplayStatus(
            detail.request.status,
            detail.quotes.size,
            orderStatuses
        )
        val received = display == com.kex.vikrsaathi.domain.finance.HubRecordStatus.RECEIVED ||
            display == com.kex.vikrsaathi.domain.finance.HubRecordStatus.PARTIALLY_RECEIVED
        if (received) {
            binding.textRequestStatus.setText(com.kex.vikrsaathi.ui.orders.statusLabel(display))
            binding.textRequestStatus.setTextColor(requireContext().getColor(com.kex.vikrsaathi.ui.orders.statusColor(display)))
        } else {
            binding.textRequestStatus.text = statusLabel(binding.root, detail.request.status)
            binding.textRequestStatus.setTextColor(requireContext().getColor(requestStatusColor(detail.request.status)))
        }
    }

    private fun openOrder(orderId: Long) {
        findNavController().navigate(
            R.id.purchaseOrderFragment,
            bundleOf(PurchaseOrderFragment.ARG_ORDER_ID to orderId)
        )
    }

    private fun openReceipt(orderId: Long) {
        findNavController().navigate(
            R.id.goodsReceiptFragment,
            bundleOf(GoodsReceiptFragment.ARG_ORDER_ID to orderId)
        )
    }

    private fun openCompare() {
        findNavController().navigate(
            R.id.purchaseRequestCompareFragment,
            bundleOf(PurchaseRequestCompareFragment.ARG_REQUEST_ID to requestId)
        )
    }

    private fun comparison(detail: PurchaseRequestDetail, vendorNames: Map<Long, String>): RequestComparison =
        purchaseRequestComparison(detail, vendorNames)

    private fun startEdit() {
        val detail = loaded ?: return
        requiredBy = detail.request.requiredBy
        binding.editRequestNotes.setText(detail.request.notes)
        binding.buttonRequiredBy.text = requiredBy?.let {
            getString(R.string.required_by_value, FinanceDates.format(it))
        } ?: getString(R.string.required_by)
        draftLines.clear()
        detail.lines.forEach {
            val entered = if (it.enteredQty > 0) it.enteredQty else it.quantity
            draftLines += DraftLine(
                it.itemId,
                it.itemName,
                it.quantity,
                it.notes,
                it.baseUnit,
                entered,
                it.orderUnit.ifBlank { it.baseUnit },
                it.unitsPerBundle.coerceAtLeast(1)
            )
        }
        showEditor()
    }

    private fun addMaterial() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = app().itemRepository.getAllSync()
            if (items.isEmpty()) {
                toast(R.string.no_master_items)
                return@launch
            }
            val form = FinanceForm.column(this@PurchaseRequestFragment)
            var selected: com.kex.vikrsaathi.data.entity.Item? = null
            var useBulk = false
            var qtyField: com.google.android.material.textfield.TextInputEditText? = null
            var unitField: com.google.android.material.textfield.MaterialAutoCompleteTextView? = null
            val preview = TextView(requireContext()).apply { setPadding(0, 8, 0, 0) }
            fun bulkName(item: com.kex.vikrsaathi.data.entity.Item?) =
                item?.purchaseUnit?.takeIf { it.isNotBlank() && item.unitsPerPurchase > 0 }
            fun refreshPreview() {
                val item = selected
                val entered = qtyField?.text?.toString()?.toIntOrNull() ?: 0
                val bulk = useBulk && bulkName(item) != null
                val factor = if (bulk) item!!.unitsPerPurchase else 1
                val unit = if (bulk) item!!.purchaseUnit else item?.stockUnit().orEmpty()
                preview.text = if (entered <= 0) "" else PackMath.formatQuantity(entered, unit, factor, item?.stockUnit().orEmpty())
            }
            FinanceForm.itemPicker(this@PurchaseRequestFragment, form, getString(R.string.item_name), items) { item ->
                selected = item
                useBulk = false
                val base = item.stockUnit().ifBlank { getString(R.string.pack_base_unit) }
                val labels = listOfNotNull(base, bulkName(item))
                unitField?.setAdapter(android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
                unitField?.setText(base, false)
                refreshPreview()
            }
            val orderUnitField = FinanceForm.picker(
                this@PurchaseRequestFragment,
                form,
                getString(R.string.order_unit),
                listOf(getString(R.string.pack_base_unit))
            ) { index ->
                useBulk = index == 1 && bulkName(selected) != null
                refreshPreview()
            }
            unitField = orderUnitField
            val qty = FinanceForm.text(this@PurchaseRequestFragment, form, getString(R.string.ordered_qty), InputType.TYPE_CLASS_NUMBER, "1")
            qtyField = qty
            qty.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) = refreshPreview()
            })
            form.addView(preview)
            refreshPreview()
            val spec = FinanceForm.text(this@PurchaseRequestFragment, form, getString(R.string.line_spec))
            ask(R.string.add_material, scroll(form)) {
                val item = selected ?: return@ask toast(R.string.select_master_item)
                val entered = qty.text.toString().toIntOrNull() ?: 0
                if (entered <= 0) return@ask toast(R.string.payment_error_request_lines)
                val bulk = useBulk && bulkName(item) != null
                val factor = if (bulk) item.unitsPerPurchase else 1
                val orderUnit = if (bulk) item.purchaseUnit else item.stockUnit()
                val base = PackMath.toBase(entered, factor) ?: return@ask toast(R.string.payment_error_pack)
                draftLines += DraftLine(
                    item.id,
                    item.name,
                    base,
                    spec.text?.toString().orEmpty(),
                    item.stockUnit(),
                    entered,
                    orderUnit,
                    factor
                )
                renderDraftLines()
            }
        }
    }

    private fun renderDraftLines() {
        binding.editorLines.removeAllViews()
        draftLines.forEachIndexed { index, line ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            val label = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                val qtyLabel = orderedLabel(line.enteredQty, line.quantity, line.orderUnit, line.unitsPerBundle, line.baseUnit)
                text = if (line.notes.isBlank()) {
                    "${line.itemName} × $qtyLabel"
                } else {
                    "${line.itemName} × $qtyLabel\n${line.notes}"
                }
            }
            val remove = com.google.android.material.button.MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = getString(R.string.remove_line)
                setOnClickListener {
                    draftLines.removeAt(index)
                    renderDraftLines()
                }
            }
            row.addView(label)
            row.addView(remove)
            binding.editorLines.addView(row)
        }
    }

    private fun saveDraft() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                app().vendorRepository.saveRequest(
                    requestId = if (requestId == 0L) null else requestId,
                    requiredBy = requiredBy,
                    notes = binding.editRequestNotes.text?.toString().orEmpty(),
                    lines = draftLines.map {
                        RequestLineInput(
                            it.itemId,
                            it.itemName,
                            it.quantity,
                            it.notes,
                            it.baseUnit,
                            it.enteredQty,
                            it.orderUnit,
                            it.unitsPerBundle
                        )
                    }
                )
            }.onSuccess {
                findNavController().popBackStack()
            }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
        }
    }

    private fun addQuote() {
        val detail = loaded ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val quoted = detail.quotes.map { it.vendorId }.toSet()
            val vendors = app().vendorRepository.getActive().filter { it.id !in quoted }
            if (vendors.isEmpty()) {
                toast(if (quoted.isEmpty()) R.string.select_vendor else R.string.all_vendors_quoted)
                return@launch
            }
            findNavController().navigate(
                R.id.purchaseRequestQuoteFragment,
                bundleOf(PurchaseRequestQuoteFragment.ARG_REQUEST_ID to requestId)
            )
        }
    }

    private fun cancelRequest() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { app().vendorRepository.cancelRequest(requestId) }
                .onFailure { toast(FinanceUi.message(requireContext(), it)) }
            reload()
        }
    }

    private fun scroll(form: LinearLayout): ScrollView {
        return ScrollView(requireContext()).apply {
            addView(form)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.7f).toInt()
            )
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

    private fun orderedLabel(enteredQty: Int, baseQty: Int, orderUnit: String, factor: Int, baseUnit: String): String {
        return if (enteredQty > 0) {
            PackMath.formatQuantity(enteredQty, orderUnit, factor.coerceAtLeast(1), baseUnit)
        } else {
            PackMath.formatQuantity(baseQty, baseUnit, 1)
        }
    }

    private fun awardedBase(lines: List<com.kex.vikrsaathi.data.entity.PurchaseOrderLine>): Map<Long, Int> =
        awardedRequestBase(lines)

    private fun app() = requireActivity().application as VikrSaathiApp
    private fun toast(res: Int) = Toast.makeText(requireContext(), res, Toast.LENGTH_LONG).show()
    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_REQUEST_ID = "requestId"
    }
}

private data class DraftLine(
    val itemId: Long?,
    val itemName: String,
    val quantity: Int,
    val notes: String,
    val baseUnit: String = "",
    val enteredQty: Int = 0,
    val orderUnit: String = "",
    val unitsPerBundle: Int = 1
)

internal fun purchaseRequestComparison(
    detail: PurchaseRequestDetail,
    vendorNames: Map<Long, String>
): RequestComparison {
    val quotes = detail.quotes.map { quote ->
        QuoteHeader(
            quoteId = quote.id,
            vendorId = quote.vendorId,
            vendorName = vendorNames[quote.vendorId] ?: "Vendor",
            deliveryChargesPaise = quote.deliveryChargesPaise,
            discountPaise = quote.discountPaise,
            expectedDelivery = quote.expectedDelivery,
            notes = quote.notes
        )
    }
    val offers = detail.quoteLines.mapNotNull { line ->
        val requestLineId = line.requestLineId ?: return@mapNotNull null
        LineOffer(
            quoteId = line.quoteId,
            requestLineId = requestLineId,
            availability = line.availability,
            ratePaise = line.ratePaise,
            availableQty = line.availableQty,
            itemId = line.itemId,
            itemName = line.itemName,
            unitName = line.unitName,
            unitsPerBundle = line.unitsPerBundle
        )
    }
    return PurchaseRequestMath.compare(
        detail.lines.map {
            ComparedRequestLine(
                it.id,
                it.itemName,
                it.quantity,
                it.notes,
                it.baseUnit,
                it.enteredQty,
                it.orderUnit,
                it.unitsPerBundle
            )
        },
        quotes,
        offers
    )
}

internal fun awardedRequestBase(lines: List<com.kex.vikrsaathi.data.entity.PurchaseOrderLine>): Map<Long, Int> {
    val totals = mutableMapOf<Long, Int>()
    lines.forEach { line ->
        val id = line.requestLineId ?: return@forEach
        totals[id] = PackMath.addBase(totals[id] ?: 0, line.quantity, line.unitsPerBundle)
    }
    return totals
}

internal fun comparableQuoteCount(comparison: RequestComparison): Int {
    return comparison.summaries.count { summary ->
        comparison.rows.any { row ->
            row.cells.any { cell ->
                cell.quoteId == summary.quoteId &&
                    cell.state == OfferState.QUOTED &&
                    cell.ratePaise != null &&
                    (cell.availableQty ?: 0) > 0 &&
                    cell.unitsPerBundle > 0
            }
        }
    }
}

private fun requestStatusColor(status: String): Int = when (status) {
    RequestStatus.AWARDED -> R.color.chart_accepted
    RequestStatus.CANCELLED -> R.color.text_secondary_dark
    else -> R.color.text_accent
}

private fun statusLabel(view: View, status: String): String {
    val res = when (status) {
        RequestStatus.QUOTING -> R.string.request_status_quoting
        RequestStatus.PARTIALLY_AWARDED -> R.string.request_status_partial
        RequestStatus.AWARDED -> R.string.request_status_awarded
        RequestStatus.CANCELLED -> R.string.request_status_cancelled
        else -> R.string.request_status_draft
    }
    return view.context.getString(res)
}
