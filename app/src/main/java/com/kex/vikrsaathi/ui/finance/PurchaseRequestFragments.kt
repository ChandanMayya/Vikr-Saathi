package com.kex.vikrsaathi.ui.finance

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import com.kex.vikrsaathi.data.entity.PurchaseRequestLine
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.data.repository.PurchaseRequestDetail
import com.kex.vikrsaathi.data.repository.PurchaseRequestSummary
import com.kex.vikrsaathi.data.repository.RequestLineInput
import com.kex.vikrsaathi.data.repository.RequestQuoteLineInput
import com.kex.vikrsaathi.databinding.FragmentPurchaseRequestBinding
import com.kex.vikrsaathi.databinding.FragmentPurchaseRequestsBinding
import com.kex.vikrsaathi.databinding.ItemPurchaseRequestBinding
import com.kex.vikrsaathi.domain.finance.AwardSelection
import com.kex.vikrsaathi.domain.finance.ComparedRequestLine
import com.kex.vikrsaathi.domain.finance.LineOffer
import com.kex.vikrsaathi.domain.finance.OfferState
import com.kex.vikrsaathi.domain.finance.PurchaseRequestMath
import com.kex.vikrsaathi.domain.finance.QuoteAvailability
import com.kex.vikrsaathi.domain.finance.QuoteHeader
import com.kex.vikrsaathi.domain.finance.RequestComparison
import com.kex.vikrsaathi.domain.finance.RequestStatus
import com.kex.vikrsaathi.ui.analysis.RankedBarItem
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
        installHelpMenu(HelpScreen.VENDORS)
        binding.recyclerRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.buttonNewRequest.setOnClickListener {
            findNavController().navigate(R.id.purchaseRequestFragment, bundleOf(PurchaseRequestFragment.ARG_REQUEST_ID to 0L))
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
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
        binding.buttonAward.setOnClickListener { award() }
        binding.buttonCancelRequest.setOnClickListener { cancelRequest() }
        if (requestId == 0L) showEditor() else reload()
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
            val current = _binding ?: return@launch
            current.progressRequest.isVisible = false
            if (detail == null) {
                findNavController().popBackStack()
                return@launch
            }
            loaded = detail
            showDetail(detail, vendors)
        }
    }

    private fun showDetail(detail: PurchaseRequestDetail, vendors: List<Vendor>) {
        binding.editorGroup.isVisible = false
        binding.detailGroup.isVisible = true
        val vendorNames = vendors.associate { it.id to it.name }
        val symbol = app().settingsRepository.currencySymbol
        val awarded = detail.orderLines.groupBy { it.requestLineId }.mapValues { (_, lines) -> lines.sumOf { it.quantity } }
        binding.textRequestTitle.text = detail.request.requestNumber
        val meta = buildString {
            append(statusLabel(binding.root, detail.request.status))
            append(" · ")
            append(FinanceDates.format(detail.request.createdAt))
            detail.request.requiredBy?.let {
                append(" · ")
                append(getString(R.string.required_by_value, FinanceDates.format(it)))
            }
            if (detail.request.notes.isNotBlank()) {
                append("\n")
                append(detail.request.notes)
            }
        }
        binding.textRequestStatus.text = meta
        binding.textRequestLines.text = buildString {
            detail.lines.forEach { line ->
                val open = PurchaseRequestMath.remaining(line.quantity, awarded[line.id] ?: 0)
                appendLine("${line.itemName} × ${line.quantity} · ${getString(R.string.request_remaining, open)}")
                if (line.notes.isNotBlank()) appendLine(line.notes)
            }
        }.trimEnd()
        binding.textRequestOrders.isVisible = detail.orders.isNotEmpty()
        binding.textRequestOrders.text = buildString {
            appendLine(getString(R.string.request_orders))
            detail.orders.forEach { order ->
                appendLine("${order.orderNumber} · ${vendorNames[order.vendorId].orEmpty()}")
            }
        }.trimEnd()
        val editable = detail.request.status == RequestStatus.DRAFT &&
            detail.quotes.isEmpty() &&
            detail.orderLines.isEmpty()
        val open = detail.request.status != RequestStatus.CANCELLED &&
            detail.request.status != RequestStatus.AWARDED
        binding.buttonEditRequest.isVisible = editable
        binding.buttonAddQuote.isVisible = open
        binding.buttonCancelRequest.isVisible = detail.orderLines.isEmpty() && open
        val comparison = comparison(detail, vendorNames)
        val canAward = open && comparison.rows.any { row ->
            val lineAwarded = awarded[row.requestLineId] ?: 0
            PurchaseRequestMath.remaining(row.requestedQty, lineAwarded) > 0 &&
                row.cells.any { it.state == OfferState.QUOTED && (it.availableQty ?: 0) > 0 }
        }
        binding.buttonAward.isVisible = canAward
        bindComparison(comparison, symbol)
    }

    private fun comparison(detail: PurchaseRequestDetail, vendorNames: Map<Long, String>): RequestComparison {
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
                itemName = line.itemName
            )
        }
        return PurchaseRequestMath.compare(
            detail.lines.map { ComparedRequestLine(it.id, it.itemName, it.quantity, it.notes) },
            quotes,
            offers
        )
    }

    private fun bindComparison(comparison: RequestComparison, symbol: String) {
        val show = comparison.summaries.isNotEmpty()
        binding.layoutCompareItem.isVisible = show
        binding.chartRates.isVisible = false
        binding.textCompare.isVisible = show
        if (!show) return
        binding.textCompare.text = buildString {
            comparison.summaries.forEach { summary ->
                appendLine(summary.vendorName)
                appendLine(
                    getString(
                        R.string.compare_charges,
                        FinanceUi.formatPaise(summary.deliveryChargesPaise, symbol),
                        FinanceUi.formatPaise(summary.discountPaise, symbol)
                    )
                )
                appendLine(
                    if (summary.goodsPaise == null) {
                        getString(R.string.compare_not_comparable)
                    } else {
                        getString(R.string.compare_goods, FinanceUi.formatPaise(summary.goodsPaise, symbol))
                    }
                )
            }
            comparison.rows.forEach { row ->
                appendLine("${row.itemName} × ${row.requestedQty}")
                row.cells.forEach { cell ->
                    val vendor = comparison.summaries.first { it.quoteId == cell.quoteId }.vendorName
                    append("  ")
                    append(vendor)
                    append(": ")
                    when (cell.state) {
                        OfferState.NOT_QUOTED -> append(getString(R.string.compare_not_quoted))
                        OfferState.UNAVAILABLE -> append(getString(R.string.compare_unavailable))
                        OfferState.QUOTED -> {
                            append(FinanceUi.formatPaise(cell.ratePaise ?: 0L, symbol))
                            append(" · ")
                            append(cell.availableQty ?: 0)
                            if (cell.lowest) {
                                append(" ")
                                append(getString(R.string.compare_lowest))
                            }
                        }
                    }
                    appendLine()
                }
            }
        }.trimEnd()
        val names = comparison.rows.map { it.itemName }
        binding.inputCompareItem.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names))
        binding.inputCompareItem.setOnItemClickListener { _, _, position, _ ->
            showRateChart(comparison, position, symbol)
        }
        if (names.isNotEmpty()) {
            binding.inputCompareItem.setText(names.first(), false)
            showRateChart(comparison, 0, symbol)
        }
    }

    private fun showRateChart(comparison: RequestComparison, index: Int, symbol: String) {
        val row = comparison.rows.getOrNull(index) ?: return
        val quoted = row.cells.mapNotNull { cell ->
            val rate = cell.ratePaise
            if (cell.state != OfferState.QUOTED || rate == null || (cell.availableQty ?: 0) <= 0) return@mapNotNull null
            val vendor = comparison.summaries.first { it.quoteId == cell.quoteId }.vendorName
            vendor to rate
        }
        binding.chartRates.isVisible = quoted.isNotEmpty()
        if (quoted.isEmpty()) return
        val highest = quoted.maxOf { it.second }.coerceAtLeast(1L)
        binding.chartRates.setItems(quoted.map { (vendor, rate) ->
            RankedBarItem(vendor, rate.toFloat() / highest.toFloat(), FinanceUi.formatPaise(rate, symbol))
        })
    }

    private fun startEdit() {
        val detail = loaded ?: return
        requiredBy = detail.request.requiredBy
        binding.editRequestNotes.setText(detail.request.notes)
        binding.buttonRequiredBy.text = requiredBy?.let {
            getString(R.string.required_by_value, FinanceDates.format(it))
        } ?: getString(R.string.required_by)
        draftLines.clear()
        detail.lines.forEach {
            draftLines += DraftLine(it.itemId, it.itemName, it.quantity, it.notes)
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
            FinanceForm.itemPicker(this@PurchaseRequestFragment, form, getString(R.string.item_name), items) { selected = it }
            val qty = FinanceForm.text(this@PurchaseRequestFragment, form, getString(R.string.ordered_qty), InputType.TYPE_CLASS_NUMBER, "1")
            val spec = FinanceForm.text(this@PurchaseRequestFragment, form, getString(R.string.line_spec))
            ask(R.string.add_material, scroll(form)) {
                val item = selected ?: return@ask toast(R.string.select_master_item)
                val quantity = qty.text.toString().toIntOrNull() ?: 0
                if (quantity <= 0) return@ask toast(R.string.payment_error_request_lines)
                draftLines += DraftLine(item.id, item.name, quantity, spec.text?.toString().orEmpty())
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
                text = if (line.notes.isBlank()) {
                    "${line.itemName} × ${line.quantity}"
                } else {
                    "${line.itemName} × ${line.quantity}\n${line.notes}"
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
                    lines = draftLines.map { RequestLineInput(it.itemId, it.itemName, it.quantity, it.notes) }
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
            val form = FinanceForm.column(this@PurchaseRequestFragment)
            var vendor: Vendor? = null
            FinanceForm.picker(this@PurchaseRequestFragment, form, getString(R.string.vendors), vendors.map { it.name }) {
                vendor = vendors[it]
            }
            var validUntil: Long? = null
            var expectedDelivery: Long? = null
            form.addView(dateButton(R.string.valid_until) { validUntil = it })
            form.addView(dateButton(R.string.expected_delivery_short) { expectedDelivery = it })
            val delivery = FinanceForm.text(
                this@PurchaseRequestFragment, form, getString(R.string.delivery_charges),
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED, "0"
            )
            val discount = FinanceForm.text(
                this@PurchaseRequestFragment, form, getString(R.string.quote_discount),
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED, "0"
            )
            val notes = FinanceForm.text(this@PurchaseRequestFragment, form, getString(R.string.request_notes))
            val drafts = detail.lines.map { line -> QuoteDraft(line) }
            drafts.forEach { draft ->
                form.addView(TextView(requireContext()).apply {
                    text = "${draft.line.itemName} × ${draft.line.quantity}"
                    setPadding(0, 16, 0, 0)
                })
                FinanceForm.picker(
                    this@PurchaseRequestFragment,
                    form,
                    getString(R.string.quote_line_state),
                    listOf(
                        getString(R.string.state_not_quoted),
                        getString(R.string.state_quoted),
                        getString(R.string.state_unavailable)
                    )
                ) { draft.mode = it }
                draft.rate = FinanceForm.text(
                    this@PurchaseRequestFragment, form, getString(R.string.rate_label),
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                )
                draft.qty = FinanceForm.text(
                    this@PurchaseRequestFragment, form, getString(R.string.available_qty),
                    InputType.TYPE_CLASS_NUMBER
                )
                draft.note = FinanceForm.text(this@PurchaseRequestFragment, form, getString(R.string.line_spec))
            }
            ask(R.string.add_vendor_quote, scroll(form)) {
                val chosen = vendor ?: return@ask toast(R.string.select_vendor)
                val inputs = mutableListOf<RequestQuoteLineInput>()
                drafts.forEach { draft ->
                    when (draft.mode) {
                        1 -> {
                            val rateText = draft.rate.text?.toString()?.trim().orEmpty()
                            if (rateText.isEmpty()) return@ask toast(R.string.payment_error_quote)
                            inputs += RequestQuoteLineInput(
                                requestLineId = draft.line.id,
                                availability = QuoteAvailability.QUOTED,
                                ratePaise = FinanceUi.parseAmountPaise(rateText),
                                availableQty = draft.qty.text.toString().toIntOrNull() ?: 0,
                                notes = draft.note.text?.toString().orEmpty()
                            )
                        }
                        2 -> inputs += RequestQuoteLineInput(
                            requestLineId = draft.line.id,
                            availability = QuoteAvailability.UNAVAILABLE,
                            ratePaise = 0L,
                            availableQty = 0,
                            notes = draft.note.text?.toString().orEmpty()
                        )
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        app().vendorRepository.saveRequestQuote(
                            requestId = requestId,
                            vendorId = chosen.id,
                            quoteDate = System.currentTimeMillis(),
                            validUntil = validUntil,
                            deliveryChargesPaise = FinanceUi.parseAmountPaise(delivery.text?.toString()),
                            discountPaise = FinanceUi.parseAmountPaise(discount.text?.toString()),
                            expectedDelivery = expectedDelivery,
                            notes = notes.text?.toString().orEmpty(),
                            lines = inputs
                        )
                    }.onFailure { toast(FinanceUi.message(requireContext(), it)) }
                    reload()
                }
            }
        }
    }

    private fun award() {
        val detail = loaded ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val vendors = app().vendorRepository.getAll().associateBy { it.id }
            val symbol = app().settingsRepository.currencySymbol
            val awarded = detail.orderLines.groupBy { it.requestLineId }.mapValues { (_, lines) -> lines.sumOf { it.quantity } }
            val form = FinanceForm.column(this@PurchaseRequestFragment)
            val rows = mutableListOf<AwardDraft>()
            detail.lines.forEach { line ->
                val open = PurchaseRequestMath.remaining(line.quantity, awarded[line.id] ?: 0)
                if (open <= 0) return@forEach
                val options = detail.quoteLines.mapNotNull { quoteLine ->
                    if (quoteLine.requestLineId != line.id) return@mapNotNull null
                    if (quoteLine.availability != QuoteAvailability.QUOTED || quoteLine.availableQty <= 0) return@mapNotNull null
                    val quote = detail.quotes.firstOrNull { it.id == quoteLine.quoteId } ?: return@mapNotNull null
                    AwardOption(
                        quoteId = quote.id,
                        vendorName = vendors[quote.vendorId]?.name ?: "Vendor",
                        ratePaise = quoteLine.ratePaise,
                        availableQty = quoteLine.availableQty
                    )
                }
                form.addView(TextView(requireContext()).apply {
                    text = "${line.itemName} · ${getString(R.string.request_remaining, open)}"
                    setPadding(0, 16, 0, 0)
                })
                if (options.isEmpty()) return@forEach
                val draft = AwardDraft(line, open, options)
                rows += draft
                val labels = listOf(getString(R.string.award_skip)) + options.map {
                    "${it.vendorName} · ${FinanceUi.formatPaise(it.ratePaise, symbol)} · ${it.availableQty}"
                }
                FinanceForm.picker(this@PurchaseRequestFragment, form, getString(R.string.award_vendor), labels) { index ->
                    draft.option = options.getOrNull(index - 1)
                    val choice = draft.option
                    if (choice != null) {
                        draft.qty.setText(minOf(open, choice.availableQty).toString())
                    }
                }
                draft.qty = FinanceForm.text(
                    this@PurchaseRequestFragment, form, getString(R.string.award_quantity),
                    InputType.TYPE_CLASS_NUMBER, open.toString()
                )
            }
            if (rows.isEmpty()) return@launch toast(R.string.payment_error_nothing)
            ask(R.string.award_materials, scroll(form)) {
                val selections = mutableListOf<AwardSelection>()
                rows.forEach { row ->
                    val option = row.option ?: return@forEach
                    val quantity = row.qty.text.toString().toIntOrNull() ?: 0
                    if (quantity <= 0) return@forEach
                    if (quantity > row.remaining || quantity > option.availableQty) {
                        toast(R.string.payment_error_award)
                        return@ask
                    }
                    selections += AwardSelection(row.line.id, option.quoteId, quantity)
                }
                if (selections.isEmpty()) return@ask toast(R.string.payment_error_nothing)
                val summary = buildString {
                    selections.groupBy { it.quoteId }.forEach { (quoteId, lines) ->
                        val quote = detail.quotes.first { it.id == quoteId }
                        appendLine(vendors[quote.vendorId]?.name ?: "Vendor")
                        lines.forEach { selection ->
                            val line = detail.lines.first { it.id == selection.requestLineId }
                            val offer = detail.quoteLines.first {
                                it.quoteId == quoteId && it.requestLineId == selection.requestLineId
                            }
                            appendLine("  ${line.itemName} × ${selection.quantity} · ${FinanceUi.formatPaise(offer.ratePaise, symbol)}")
                        }
                    }
                }.trimEnd()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.award_summary_title)
                    .setMessage(summary)
                    .setPositiveButton(R.string.save) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatching { app().vendorRepository.awardRequest(requestId, selections) }
                                .onFailure { toast(FinanceUi.message(requireContext(), it)) }
                            reload()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun cancelRequest() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { app().vendorRepository.cancelRequest(requestId) }
                .onFailure { toast(FinanceUi.message(requireContext(), it)) }
            reload()
        }
    }

    private fun dateButton(labelRes: Int, onPicked: (Long) -> Unit): com.google.android.material.button.MaterialButton {
        return com.google.android.material.button.MaterialButton(
            requireContext(),
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = getString(labelRes)
            setOnClickListener {
                FinanceDates.showSingle(this@PurchaseRequestFragment, labelRes) { picked ->
                    text = getString(labelRes) + ": " + FinanceDates.format(picked)
                    onPicked(picked)
                }
            }
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
    val notes: String
)

private class QuoteDraft(val line: PurchaseRequestLine) {
    var mode: Int = 0
    lateinit var rate: com.google.android.material.textfield.TextInputEditText
    lateinit var qty: com.google.android.material.textfield.TextInputEditText
    lateinit var note: com.google.android.material.textfield.TextInputEditText
}

private data class AwardOption(
    val quoteId: Long,
    val vendorName: String,
    val ratePaise: Long,
    val availableQty: Int
)

private class AwardDraft(
    val line: PurchaseRequestLine,
    val remaining: Int,
    val options: List<AwardOption>
) {
    var option: AwardOption? = null
    lateinit var qty: com.google.android.material.textfield.TextInputEditText
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
