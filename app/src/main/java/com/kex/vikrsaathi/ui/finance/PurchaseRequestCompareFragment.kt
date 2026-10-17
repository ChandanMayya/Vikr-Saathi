package com.kex.vikrsaathi.ui.finance

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.data.repository.PurchaseRequestDetail
import com.kex.vikrsaathi.databinding.DialogAwardReviewBinding
import com.kex.vikrsaathi.databinding.FragmentPurchaseRequestCompareBinding
import com.kex.vikrsaathi.databinding.ItemAwardReviewLineBinding
import com.kex.vikrsaathi.databinding.ItemCompareJumpBinding
import com.kex.vikrsaathi.databinding.ItemCompareOfferBinding
import com.kex.vikrsaathi.databinding.ItemCompareTotalBinding
import com.kex.vikrsaathi.databinding.ItemVendorAwardBinding
import com.kex.vikrsaathi.databinding.ItemVendorAwardLineBinding
import com.kex.vikrsaathi.databinding.ItemVendorPreviewLineBinding
import com.kex.vikrsaathi.domain.finance.AwardSelection
import com.kex.vikrsaathi.domain.finance.ComparisonCell
import com.kex.vikrsaathi.domain.finance.MaterialComparisonRow
import com.kex.vikrsaathi.domain.finance.OfferState
import com.kex.vikrsaathi.domain.finance.PackMath
import com.kex.vikrsaathi.domain.finance.PurchaseRequestMath
import com.kex.vikrsaathi.domain.finance.QuoteAvailability
import com.kex.vikrsaathi.domain.finance.RequestComparison
import com.kex.vikrsaathi.domain.finance.RequestStatus
import com.kex.vikrsaathi.domain.finance.VendorQuoteSummary
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import java.text.NumberFormat
import kotlinx.coroutines.launch

class PurchaseRequestCompareFragment : Fragment() {

    private var _binding: FragmentPurchaseRequestCompareBinding? = null
    private val binding get() = _binding!!
    private var requestId: Long = 0L
    private var detail: PurchaseRequestDetail? = null
    private var vendors: Map<Long, Vendor> = emptyMap()
    private var awarded: Map<Long, Int> = emptyMap()
    private var comparison: RequestComparison? = null
    private var requestOpen = false
    private var symbol = ""
    private var mode = AwardMode.VENDOR
    private var vendorQuoteId: Long? = null
    private val expandedVendors = mutableSetOf<Long>()
    private val expandedVendorAll = mutableSetOf<Long>()
    private var previewAll = false
    private var filter = ItemFilter.ALL
    private var sort = OfferSort.UNIT_RATE
    private var query = ""
    private var cursor = 0
    private var currentLineId: Long? = null
    private var showingList = false
    private val choices = mutableMapOf<Long, OfferChoice>()
    private val cards = mutableListOf<OfferCard>()
    private val jumpAdapter = JumpAdapter { rowId -> jumpTo(rowId) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: android.os.Bundle?): View {
        _binding = FragmentPurchaseRequestCompareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.VENDORS)
        requestId = arguments?.getLong(ARG_REQUEST_ID) ?: 0L
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { insetView, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            insetView.updatePadding(bottom = (ime - nav).coerceAtLeast(0))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.listCompareItems.layoutManager = LinearLayoutManager(requireContext())
        binding.listCompareItems.adapter = jumpAdapter
        binding.buttonCreateOrders.setOnClickListener {
            if (mode == AwardMode.VENDOR) confirmVendor() else confirmAward()
        }
        binding.buttonModeVendor.setOnClickListener {
            if (mode != AwardMode.VENDOR) {
                mode = AwardMode.VENDOR
                applyMode()
            }
        }
        binding.buttonModeMaterial.setOnClickListener {
            if (mode != AwardMode.MATERIAL) {
                mode = AwardMode.MATERIAL
                applyMode()
            }
        }
        binding.buttonPreviousItem.setOnClickListener { step(-1) }
        binding.buttonNextItem.setOnClickListener { step(1) }
        binding.buttonQuoteTotals.setOnClickListener { showQuoteTotals() }
        binding.chipCompareAll.setOnClickListener { selectFilter(ItemFilter.ALL) }
        binding.chipCompareNeeds.setOnClickListener { selectFilter(ItemFilter.NEEDS_AWARD) }
        binding.chipCompareAwarded.setOnClickListener { selectFilter(ItemFilter.AWARDED) }
        binding.editCompareSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty().trim()
                keepCursor()
                if (binding.editCompareSearch.hasFocus() || showingList) showItemList() else renderPager()
            }
        })
        binding.editCompareSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showItemList() else showPager()
        }
        binding.editCompareSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.editCompareSearch.clearFocus()
                showPager()
                true
            } else {
                false
            }
        }
        val sortLabels = listOf(getString(R.string.compare_sort_unit), getString(R.string.compare_sort_cost))
        binding.inputCompareSort.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sortLabels))
        binding.inputCompareSort.setText(sortLabels.first(), false)
        binding.inputCompareSort.setOnItemClickListener { _, _, position, _ ->
            sort = if (position == 0) OfferSort.UNIT_RATE else OfferSort.ESTIMATED_COST
            renderPager()
        }
        reload()
    }

    private fun reload() {
        binding.progressCompare.isVisible = true
        binding.textCompareEmpty.isVisible = false
        binding.compareBrowser.isVisible = false
        binding.compareActions.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = app().vendorRepository.requestDetail(requestId)
            val vendorRows = app().vendorRepository.getAll()
            val current = _binding ?: return@launch
            current.progressCompare.isVisible = false
            if (loaded == null) {
                findNavController().popBackStack()
                return@launch
            }
            detail = loaded
            vendors = vendorRows.associateBy { it.id }
            awarded = awardedRequestBase(loaded.orderLines)
            symbol = app().settingsRepository.currencySymbol
            comparison = purchaseRequestComparison(loaded, vendors.mapValues { it.value.name })
            requestOpen = loaded.request.status != RequestStatus.CANCELLED &&
                loaded.request.status != RequestStatus.AWARDED
            choices.clear()
            vendorQuoteId = null
            expandedVendors.clear()
            expandedVendorAll.clear()
            cursor = 0
            showLoaded()
        }
    }

    private fun showLoaded() {
        val compared = comparison ?: return
        if (compared.summaries.isEmpty()) {
            binding.textCompareEmpty.isVisible = true
            binding.compareBrowser.isVisible = false
            binding.compareActions.isVisible = false
            return
        }
        binding.textCompareEmpty.isVisible = false
        binding.compareBrowser.isVisible = true
        binding.compareActions.isVisible = true
        updateFilterCounts()
        applyMode()
    }

    private fun applyMode() {
        val current = _binding ?: return
        val vendor = mode == AwardMode.VENDOR
        paintAwardMode(vendor)
        current.vendorScroll.isVisible = vendor
        current.layoutCompareSearch.isVisible = !vendor
        current.compareMaterialFilters.isVisible = !vendor
        current.textModeHint.setText(
            if (vendor) R.string.compare_mode_vendor_hint else R.string.compare_mode_material_hint
        )
        val otherKept = if (vendor) {
            choices.any { it.value.quantity > 0 }
        } else {
            vendorQuoteId != null
        }
        current.textModeKept.isVisible = otherKept
        current.textModeKept.setText(if (vendor) R.string.compare_kept_material else R.string.compare_kept_vendor)
        if (vendor) {
            current.listCompareItems.isVisible = false
            current.compareScroll.isVisible = false
            renderVendors()
        } else if (current.editCompareSearch.hasFocus()) {
            showItemList()
        } else {
            showPager()
        }
        refreshSummary()
    }

    private fun selectFilter(next: ItemFilter) {
        if (filter == next) return
        filter = next
        keepCursor()
        if (binding.editCompareSearch.hasFocus()) showItemList() else showPager()
    }

    private fun keepCursor() {
        val rows = visibleRows()
        val index = currentLineId?.let { id -> rows.indexOfFirst { it.requestLineId == id } } ?: -1
        cursor = if (index >= 0) index else 0
    }

    private fun visibleRows(): List<MaterialComparisonRow> {
        val rows = comparison?.rows.orEmpty()
        val needle = query.lowercase()
        return rows.filter { row ->
            matchesFilter(row) && (needle.isEmpty() ||
                row.itemName.lowercase().contains(needle) ||
                row.notes.lowercase().contains(needle))
        }
    }

    private fun matchesFilter(row: MaterialComparisonRow): Boolean {
        val open = PurchaseRequestMath.remaining(row.requestedQty, awarded[row.requestLineId] ?: 0)
        return when (filter) {
            ItemFilter.ALL -> true
            ItemFilter.NEEDS_AWARD -> open > 0
            ItemFilter.AWARDED -> open == 0
        }
    }

    private fun updateFilterCounts() {
        val rows = comparison?.rows.orEmpty()
        val needs = rows.count { PurchaseRequestMath.remaining(it.requestedQty, awarded[it.requestLineId] ?: 0) > 0 }
        val awardedCount = rows.size - needs
        binding.chipCompareAll.text = getString(R.string.compare_filter_all, rows.size)
        binding.chipCompareNeeds.text = getString(R.string.compare_filter_needs, needs)
        binding.chipCompareAwarded.text = getString(R.string.compare_filter_awarded, awardedCount)
    }

    private fun step(delta: Int) {
        val rows = visibleRows()
        if (rows.isEmpty()) return
        cursor = (cursor + delta).coerceIn(0, rows.lastIndex)
        renderPager()
    }

    private fun jumpTo(rowId: Long) {
        val index = visibleRows().indexOfFirst { it.requestLineId == rowId }
        if (index >= 0) cursor = index
        binding.editCompareSearch.clearFocus()
        showPager()
    }

    private fun showItemList() {
        showingList = true
        val rows = visibleRows()
        jumpAdapter.submit(rows)
        binding.listCompareItems.isVisible = true
        binding.compareScroll.isVisible = false
    }

    private fun showPager() {
        showingList = false
        binding.listCompareItems.isVisible = false
        binding.compareScroll.isVisible = true
        renderPager()
    }

    private fun renderPager() {
        val current = _binding ?: return
        val rows = visibleRows()
        val empty = rows.isEmpty()
        current.textCompareNone.isVisible = empty
        current.cardCompareItem.isVisible = !empty
        current.textOffersHeading.isVisible = !empty
        current.layoutCompareSort.isVisible = !empty
        current.buttonQuoteTotals.isVisible = !empty
        current.compareOffers.isVisible = !empty
        if (empty) {
            current.compareOffers.removeAllViews()
            cards.clear()
            return
        }
        if (cursor > rows.lastIndex) cursor = rows.lastIndex
        val row = rows[cursor]
        currentLineId = row.requestLineId
        val position = getString(R.string.compare_item_position, cursor + 1, rows.size)
        current.textItemPosition.text = position
        current.textItemPosition.contentDescription = position
        current.textItemName.text = row.itemName
        current.textItemNotes.isVisible = row.notes.isNotBlank()
        current.textItemNotes.text = row.notes
        val base = baseUnit(row)
        val given = awarded[row.requestLineId] ?: 0
        val remaining = PurchaseRequestMath.remaining(row.requestedQty, given)
        current.textItemSummary.text = getString(
            R.string.compare_item_summary,
            PackMath.formatQuantity(row.requestedQty, base, 1),
            PackMath.formatQuantity(given, base, 1),
            PackMath.formatQuantity(remaining, base, 1)
        )
        current.buttonPreviousItem.isEnabled = cursor > 0
        current.buttonNextItem.isEnabled = cursor < rows.lastIndex
        current.textOffersHeading.text = getString(R.string.compare_offers_count, row.cells.size)
        bindOffers(row, remaining)
    }

    private fun bindOffers(row: MaterialComparisonRow, remaining: Int) {
        cards.clear()
        binding.compareOffers.removeAllViews()
        val quotedPeers = row.cells.count { cell ->
            cell.state == OfferState.QUOTED &&
                cell.ratePaise != null &&
                (cell.availableQty ?: 0) > 0 &&
                cell.unitsPerBundle > 0
        }
        sortedCells(row, remaining).forEach { cell ->
            val vendor = comparison?.summaries?.firstOrNull { it.quoteId == cell.quoteId }?.vendorName.orEmpty()
            offerCard(row, cell, vendor, remaining, quotedPeers)
        }
        refreshSelection()
    }

    private fun sortedCells(row: MaterialComparisonRow, remaining: Int): List<ComparisonCell> {
        return row.cells.withIndex().sortedWith { left, right ->
            val compared = if (sort == OfferSort.UNIT_RATE) {
                compareUnit(left.value, right.value)
            } else {
                compareEstimate(left.value, right.value, remaining)
            }
            if (compared != 0) compared else left.index.compareTo(right.index)
        }.map { it.value }
    }

    private fun compareUnit(left: ComparisonCell, right: ComparisonCell): Int {
        val leftRank = unitRank(left)
        val rightRank = unitRank(right)
        if (leftRank != rightRank) return leftRank.compareTo(rightRank)
        if (leftRank != 0) return 0
        val leftRate = left.ratePaise ?: return 0
        val rightRate = right.ratePaise ?: return 0
        return PackMath.comparePerBase(leftRate, left.unitsPerBundle, rightRate, right.unitsPerBundle) ?: 0
    }

    private fun compareEstimate(left: ComparisonCell, right: ComparisonCell, remaining: Int): Int {
        val leftCost = if (coversRemaining(left, remaining)) estimatePaise(left, remaining) else null
        val rightCost = if (coversRemaining(right, remaining)) estimatePaise(right, remaining) else null
        return when {
            leftCost == null && rightCost == null -> 0
            leftCost == null -> 1
            rightCost == null -> -1
            else -> leftCost.compareTo(rightCost)
        }
    }

    private fun coversRemaining(cell: ComparisonCell, remaining: Int): Boolean {
        if (cell.state != OfferState.QUOTED || cell.ratePaise == null || cell.unitsPerBundle <= 0) return false
        if (remaining == 0) return true
        return PackMath.coverage(remaining, cell.availableQty ?: 0, cell.unitsPerBundle)?.canCover == true
    }

    private fun unitRank(cell: ComparisonCell): Int {
        val comparable = cell.state == OfferState.QUOTED &&
            cell.ratePaise != null &&
            (cell.availableQty ?: 0) > 0 &&
            cell.unitsPerBundle > 0 &&
            cell.perBasePaise != null
        return if (comparable) 0 else if (cell.state == OfferState.QUOTED) 1 else 2
    }

    private fun offerCard(
        row: MaterialComparisonRow,
        cell: ComparisonCell,
        vendor: String,
        remaining: Int,
        quotedPeers: Int
    ) {
        val offer = ItemCompareOfferBinding.inflate(layoutInflater, binding.compareOffers, true)
        val unit = packageUnit(cell, row)
        val base = baseUnit(row)
        val factor = cell.unitsPerBundle.coerceAtLeast(1)
        val quoted = cell.state == OfferState.QUOTED && cell.ratePaise != null
        offer.textOfferVendor.text = vendor
        val awardedOffer = cell.quoteId in awardedQuotesForLine(row.requestLineId)
        if (awardedOffer) {
            offer.textOfferBadge.isVisible = true
            offer.textOfferBadge.setText(R.string.compare_awarded_badge)
            offer.textOfferBadge.setTextColor(requireContext().getColor(R.color.chart_accepted))
        } else {
            offer.textOfferBadge.isVisible = cell.lowest && quotedPeers >= 2
            offer.textOfferBadge.setText(R.string.compare_lowest_unit)
            offer.textOfferBadge.setTextColor(requireContext().getColor(R.color.text_accent))
        }
        offer.groupQuoted.isVisible = quoted
        offer.textOfferStatus.isVisible = !quoted
        if (!quoted) {
            offer.textOfferStatus.setText(
                if (cell.state == OfferState.UNAVAILABLE) R.string.compare_unavailable else R.string.compare_not_quoted
            )
        } else {
            val rate = cell.ratePaise ?: 0L
            val perBase = cell.perBasePaise
            offer.textUnitRate.text = if (perBase == null) {
                getString(R.string.compare_not_comparable)
            } else {
                QuoteCompareFormat.perUnit(FinanceUi.formatPaise(perBase, symbol), base)
            }
            offer.textPackageLine.text = QuoteCompareFormat.perUnit(FinanceUi.formatPaise(rate, symbol), unit) +
                " · " + QuoteCompareFormat.packageSize(factor, base, unit)
            val available = cell.availableQty ?: 0
            val availableBase = PackMath.toBaseLong(available, factor)
            val availableLabel = if (availableBase != null && availableBase <= Int.MAX_VALUE) {
                PackMath.formatQuantity(availableBase.toInt(), base, 1)
            } else {
                PackMath.formatQuantity(available, unit, 1)
            }
            offer.textAvailable.text = getString(R.string.compare_available_base, availableLabel)
            val coverage = if (remaining > 0) PackMath.coverage(remaining, available, factor) else null
            val estimate = estimatePaise(cell, remaining)
            offer.textEstimate.text = when {
                remaining == 0 -> getString(R.string.compare_nothing_remaining)
                estimate == null -> getString(R.string.compare_not_comparable)
                coverage != null && !coverage.canCover -> getString(
                    R.string.compare_estimate_partial,
                    FinanceUi.formatPaise(estimate, symbol),
                    availableLabel
                )
                else -> getString(R.string.compare_estimate, FinanceUi.formatPaise(estimate, symbol))
            }
            val short = coverage != null && remaining > 0 && !coverage.canCover
            offer.textCoverage.isVisible = short
            if (coverage != null && short && coverage.availableBase <= Int.MAX_VALUE) {
                offer.textCoverage.text = getString(
                    R.string.compare_covers_part,
                    PackMath.formatQuantity(coverage.availableBase.toInt(), base, 1),
                    PackMath.formatQuantity(remaining, base, 1)
                )
            }
            val needed = cell.packagesNeeded
            val excess = cell.excessBase
            val packageLabel = if (needed == null || excess == null) {
                getString(R.string.compare_not_comparable)
            } else {
                QuoteCompareFormat.packages(needed, unit, QuoteCompareFormat.extra(excess, base))
            }
            val full = PackMath.coverage(row.requestedQty, available, factor)
            val fullGoods = if (full == null) {
                null
            } else {
                QuoteCompareFormat.materialGoodsPaise(rate, full.packagesNeeded, full.canCover)
            }
            val goodsLabel = if (fullGoods == null) {
                getString(R.string.compare_not_comparable)
            } else {
                getString(R.string.compare_line_goods) + " " + FinanceUi.formatPaise(fullGoods, symbol)
            }
            offer.textOfferDetails.text = getString(R.string.compare_for_request, packageLabel) + "\n" + goodsLabel
            offer.buttonOfferDetails.setOnClickListener {
                val open = !offer.textOfferDetails.isVisible
                offer.textOfferDetails.isVisible = open
                offer.buttonOfferDetails.setText(if (open) R.string.compare_hide_details else R.string.compare_offer_details)
            }
        }
        val canSelect = quoted && requestOpen && remaining > 0 && (cell.availableQty ?: 0) > 0
        val maxQty = minOf(cell.availableQty ?: 0, remaining / factor).coerceAtLeast(0)
        val prefill = maxQty
        offer.buttonAwardOffer.isVisible = canSelect
        offer.groupAwardQty.isVisible = false
        offer.inputAwardQty.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val choice = choices[row.requestLineId]
                if (choice?.quoteId == cell.quoteId) {
                    choice.quantity = s?.toString()?.toIntOrNull() ?: 0
                    bindCover(offer, choice.quantity, factor, unit, base, remaining, cell.availableQty ?: 0)
                    refreshSummary()
                }
            }
        })
        offer.buttonQtyMinus.setOnClickListener {
            val current = offer.inputAwardQty.text?.toString()?.toIntOrNull() ?: 0
            if (current > 0) offer.inputAwardQty.setText((current - 1).toString())
        }
        offer.buttonQtyPlus.setOnClickListener {
            val current = offer.inputAwardQty.text?.toString()?.toIntOrNull() ?: 0
            if (current < maxQty) offer.inputAwardQty.setText((current + 1).toString())
        }
        offer.inputAwardQty.setOnFocusChangeListener { focused, hasFocus ->
            if (hasFocus) {
                focused.post {
                    focused.requestRectangleOnScreen(android.graphics.Rect(0, 0, focused.width, focused.height), true)
                }
            }
        }
        offer.buttonAwardOffer.setOnClickListener {
            val current = choices[row.requestLineId]
            if (current?.quoteId == cell.quoteId) {
                choices.remove(row.requestLineId)
            } else {
                choices[row.requestLineId] = OfferChoice(cell.quoteId, prefill)
            }
            refreshSelection()
            refreshSummary()
        }
        cards += OfferCard(row.requestLineId, cell.quoteId, offer, canSelect, factor, unit, base, remaining, cell.availableQty ?: 0)
    }

    private fun bindCover(
        offer: ItemCompareOfferBinding,
        quantity: Int,
        factor: Int,
        unit: String,
        base: String,
        remaining: Int,
        available: Int
    ) {
        val covered = PackMath.toBase(quantity, factor)
        offer.textAwardCover.text = if (covered == null) {
            getString(R.string.compare_not_comparable)
        } else {
            getString(
                R.string.compare_covers,
                PackMath.formatQuantity(quantity, unit, 1),
                PackMath.formatQuantity(covered, base, 1)
            )
        }
        val over = covered == null || covered > remaining || quantity > available
        offer.textAwardCover.setTextColor(
            requireContext().getColor(if (quantity > 0 && over) R.color.balance_payable else R.color.text_secondary_dark)
        )
    }

    private fun estimatePaise(cell: ComparisonCell, remaining: Int): Long? {
        val rate = cell.ratePaise ?: return null
        if (cell.state != OfferState.QUOTED || (cell.availableQty ?: 0) <= 0 || cell.unitsPerBundle <= 0) return null
        if (remaining == 0) return 0L
        val coverage = PackMath.coverage(remaining, cell.availableQty ?: 0, cell.unitsPerBundle) ?: return null
        return if (coverage.canCover) {
            QuoteCompareFormat.materialGoodsPaise(rate, coverage.packagesNeeded, true)
        } else {
            PackMath.lineTotalPaise(cell.availableQty ?: 0, rate)
        }
    }

    private fun refreshSelection() {
        val root = _binding?.root ?: return
        val outline = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOutline)
        val primary = MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary)
        cards.forEach { card ->
            val choice = choices[card.requestLineId]
            val selected = choice?.quoteId == card.quoteId
            card.binding.cardOffer.strokeWidth = if (selected) dp(2) else dp(1)
            card.binding.cardOffer.strokeColor = if (selected) primary else outline
            card.binding.buttonAwardOffer.isVisible = card.canSelect && !selected
            card.binding.groupAwardQty.isVisible = selected && card.canSelect
            if (selected && card.binding.inputAwardQty.tag != card.quoteId) {
                card.binding.inputAwardQty.setText(choice.quantity.toString())
                card.binding.inputAwardQty.tag = card.quoteId
                bindCover(
                    card.binding,
                    choice.quantity,
                    card.factor,
                    card.unit,
                    card.base,
                    card.remaining,
                    card.available
                )
            }
            if (!selected) card.binding.inputAwardQty.tag = null
        }
    }

    private fun isAwardedQuote(quoteId: Long): Boolean =
        detail?.orders.orEmpty().any { it.quoteId == quoteId }

    private fun awardedQuotesForLine(requestLineId: Long): Set<Long> {
        val loaded = detail ?: return emptySet()
        val quoteByOrder = loaded.orders.associate { it.id to it.quoteId }
        return loaded.orderLines.mapNotNull { line ->
            if (line.requestLineId == requestLineId) quoteByOrder[line.orderId] else null
        }.toSet()
    }

    private fun renderClosedVendorReference() {
        val current = _binding ?: return
        val loaded = detail ?: return
        val awardedQuoteIds = loaded.orders.mapNotNull { it.quoteId }.toSet()
        comparison?.summaries.orEmpty()
            .sortedBy { summary -> if (summary.quoteId in awardedQuoteIds) 0 else 1 }
            .forEach { summary ->
                val card = ItemVendorAwardBinding.inflate(layoutInflater, current.vendorCards, true)
                val awardedQuote = summary.quoteId in awardedQuoteIds
                val plan = referencePlan(summary)
                card.textVendorName.text = summary.vendorName
                card.textVendorBadge.isVisible = true
                card.textVendorBadge.setText(
                    if (awardedQuote) R.string.compare_awarded_badge else R.string.compare_not_awarded
                )
                card.textVendorBadge.setTextColor(
                    requireContext().getColor(
                        if (awardedQuote) R.color.chart_accepted else R.color.text_secondary_dark
                    )
                )
                card.imageVendorSelect.setImageResource(
                    if (awardedQuote) R.drawable.ic_award_selected else R.drawable.ic_award_blocked
                )
                card.cardVendorAward.isClickable = false
                card.cardVendorAward.isFocusable = false
                if (plan.landedPaise != null) {
                    card.textVendorLanded.text = FinanceUi.formatPaise(plan.landedPaise, symbol)
                    card.textVendorLandedCaption.setText(R.string.compare_landed_caption)
                } else if (plan.lines.any { !it.covered }) {
                    card.textVendorLanded.setText(R.string.compare_vendor_incomplete)
                    card.textVendorLandedCaption.text = ""
                } else {
                    card.textVendorLanded.setText(R.string.compare_not_comparable)
                    card.textVendorLandedCaption.text = ""
                }
                val short = plan.lines.count { !it.covered }
                card.textVendorCoverage.text = if (plan.lines.isEmpty()) {
                    ""
                } else if (short == 0) {
                    getString(R.string.compare_vendor_covered, plan.lines.size, plan.lines.size)
                } else {
                    getString(R.string.compare_vendor_short, plan.lines.size - short, plan.lines.size, short)
                }
                val deliveryWhen = plan.expectedDelivery?.let { FinanceDates.format(it) }
                    ?: loaded.orders.firstOrNull { it.quoteId == summary.quoteId }?.expectedDelivery?.let { FinanceDates.format(it) }
                    ?: getString(R.string.compare_no_delivery)
                card.textVendorDelivery.text = getString(R.string.compare_expected_delivery, deliveryWhen)
                val showCharges = plan.goodsPaise != null && plan.landedPaise != null
                card.groupVendorCharges.isVisible = showCharges
                if (showCharges && plan.goodsPaise != null) {
                    card.textVendorGoods.text = FinanceUi.formatPaise(plan.goodsPaise, symbol)
                    card.textVendorDeliveryCharge.text = FinanceUi.formatPaise(plan.deliveryPaise, symbol)
                    val discount = FinanceUi.formatPaise(plan.discountPaise, symbol)
                    card.textVendorDiscount.text = if (plan.discountPaise > 0L) "\u2212$discount" else discount
                }
                card.buttonVendorDetails.isVisible = plan.lines.isNotEmpty()
                card.buttonVendorDetails.setText(
                    if (short > 0) R.string.compare_missing_items else R.string.compare_view_breakdown
                )
                card.buttonVendorDetails.setOnClickListener {
                    val show = !card.vendorLines.isVisible
                    card.vendorLines.isVisible = show
                    card.buttonVendorDetails.setText(
                        if (show) {
                            R.string.compare_hide_breakdown
                        } else if (short > 0) {
                            R.string.compare_missing_items
                        } else {
                            R.string.compare_view_breakdown
                        }
                    )
                    if (show && card.vendorLines.childCount == 0) bindReferenceLines(card, plan)
                }
                val priceLabel = plan.landedPaise?.let { FinanceUi.formatPaise(it, symbol) }
                    ?: card.textVendorLanded.text.toString()
                card.cardVendorAward.contentDescription = summary.vendorName + ". " + priceLabel + ". " + getString(
                    if (awardedQuote) R.string.compare_awarded_badge else R.string.compare_not_awarded
                )
            }
    }

    private fun referencePlan(summary: VendorQuoteSummary): VendorPlan {
        val quote = detail?.quotes?.firstOrNull { it.id == summary.quoteId }
        val lines = comparison?.rows.orEmpty().map { row ->
            val cell = row.cells.first { it.quoteId == summary.quoteId }
            planVendorLine(row, cell, row.requestedQty)
        }
        val covered = lines.isNotEmpty() && lines.all { it.covered }
        var goods: Long? = if (covered) 0L else null
        if (goods != null) {
            for (line in lines) {
                val cost = line.linePaise
                goods = if (cost == null) {
                    null
                } else {
                    try {
                        Math.addExact(goods!!, cost)
                    } catch (_: ArithmeticException) {
                        null
                    }
                }
                if (goods == null) break
            }
        }
        val landed = goods?.let {
            QuoteCompareFormat.landedPaise(it, summary.deliveryChargesPaise, summary.discountPaise)
        }
        return VendorPlan(
            quoteId = summary.quoteId,
            vendorName = summary.vendorName,
            deliveryPaise = summary.deliveryChargesPaise,
            discountPaise = summary.discountPaise,
            expectedDelivery = quote?.expectedDelivery,
            lines = lines,
            goodsPaise = goods,
            landedPaise = landed,
            selectable = false
        )
    }

    private fun bindReferenceLines(card: ItemVendorAwardBinding, plan: VendorPlan) {
        plan.lines.forEach { line ->
            val row = ItemVendorAwardLineBinding.inflate(layoutInflater, card.vendorLines, true)
            val base = baseUnit(line.row)
            val rate = line.ratePaise?.let {
                QuoteCompareFormat.perUnit(FinanceUi.formatPaise(it, symbol), line.unit)
            }
            val cost = line.linePaise?.let { FinanceUi.formatPaise(it, symbol) }
            val quantity = if (line.covered) {
                PackMath.formatQuantity(line.packages, line.unit, line.factor, base)
            } else {
                PackMath.formatQuantity(line.remaining, base, 1)
            }
            row.textAwardLineName.text = line.row.itemName
            row.textAwardLineMeta.text = listOfNotNull(quantity, rate, cost).joinToString(" · ")
            if (line.covered) {
                row.textAwardLineState.setText(R.string.compare_covered)
                row.textAwardLineState.setTextColor(requireContext().getColor(R.color.chart_accepted))
            } else {
                row.textAwardLineState.text = line.reason.orEmpty()
                row.textAwardLineState.setTextColor(requireContext().getColor(R.color.orange_800))
            }
        }
    }

    private fun outstandingRows(): List<Pair<MaterialComparisonRow, Int>> {
        return comparison?.rows.orEmpty().map { row ->
            row to PurchaseRequestMath.remaining(row.requestedQty, awarded[row.requestLineId] ?: 0)
        }.filter { it.second > 0 }
    }

    private fun vendorPlans(): List<VendorPlan> {
        val compared = comparison ?: return emptyList()
        val open = outstandingRows()
        val plans = compared.summaries.map { summary ->
            val quote = detail?.quotes?.firstOrNull { it.id == summary.quoteId }
            val lines = open.map { (row, remaining) ->
                val cell = row.cells.first { it.quoteId == summary.quoteId }
                planVendorLine(row, cell, remaining)
            }
            val covered = lines.all { it.covered }
            var goods: Long? = if (covered && lines.isNotEmpty()) 0L else null
            if (goods != null) {
                for (line in lines) {
                    val cost = line.linePaise
                    goods = if (cost == null) {
                        null
                    } else {
                        try {
                            Math.addExact(goods!!, cost)
                        } catch (_: ArithmeticException) {
                            null
                        }
                    }
                    if (goods == null) break
                }
            }
            val landed = goods?.let {
                QuoteCompareFormat.landedPaise(it, summary.deliveryChargesPaise, summary.discountPaise)
            }
            VendorPlan(
                quoteId = summary.quoteId,
                vendorName = summary.vendorName,
                deliveryPaise = summary.deliveryChargesPaise,
                discountPaise = summary.discountPaise,
                expectedDelivery = quote?.expectedDelivery,
                lines = lines,
                goodsPaise = goods,
                landedPaise = landed,
                selectable = covered && lines.isNotEmpty()
            )
        }
        return plans.sortedWith(compareBy<VendorPlan> { if (it.selectable && it.landedPaise != null) 0 else if (it.selectable) 1 else 2 }
            .thenBy { it.landedPaise ?: Long.MAX_VALUE }
            .thenBy { it.vendorName.lowercase() })
    }

    private fun planVendorLine(row: MaterialComparisonRow, cell: ComparisonCell, remaining: Int): VendorLinePlan {
        val factor = cell.unitsPerBundle.coerceAtLeast(1)
        val unit = packageUnit(cell, row)
        val base = baseUnit(row)
        fun gap(reason: String) = VendorLinePlan(row, remaining, 0, cell.ratePaise, unit, factor, null, false, reason)
        if (cell.state == OfferState.UNAVAILABLE) return gap(getString(R.string.compare_unavailable))
        if (cell.state != OfferState.QUOTED || cell.ratePaise == null) return gap(getString(R.string.compare_not_quoted))
        if ((cell.availableQty ?: 0) <= 0) return gap(getString(R.string.compare_gap_short, 0, 1))
        if (PackMath.validateTransaction(cell.unitName, cell.unitsPerBundle) != null) {
            return gap(getString(R.string.compare_gap_pack))
        }
        val packages = remaining / factor
        val coveredBase = PackMath.toBase(packages, factor)
        if (packages <= 0 || coveredBase == null || coveredBase != remaining) {
            return gap(getString(R.string.compare_gap_package))
        }
        val available = cell.availableQty ?: 0
        if (packages > available) return gap(getString(R.string.compare_gap_short, available, packages))
        val cost = PackMath.lineTotalPaise(packages, cell.ratePaise)
            ?: return gap(getString(R.string.compare_not_comparable))
        return VendorLinePlan(row, remaining, packages, cell.ratePaise, unit, factor, cost, true, null)
    }

    private fun renderVendors() {
        val current = _binding ?: return
        val open = outstandingRows()
        val plans = vendorPlans()
        if (vendorQuoteId != null && plans.none { it.quoteId == vendorQuoteId && it.selectable }) {
            vendorQuoteId = null
        }
        current.vendorCards.removeAllViews()
        val sameUnit = open.map { baseUnit(it.first) }.distinct().size == 1
        val remainingTotal = open.fold(0L) { sum, pair -> sum + pair.second }
        current.textVendorOverview.isVisible = open.isNotEmpty()
        current.textVendorOverview.text = when {
            open.isEmpty() -> getString(R.string.compare_fully_awarded)
            sameUnit && remainingTotal <= Int.MAX_VALUE -> getString(
                R.string.compare_outstanding,
                open.size,
                NumberFormat.getIntegerInstance().format(remainingTotal) + " " + baseUnit(open.first().first)
            )
            else -> getString(R.string.compare_outstanding_items, open.size)
        }
        val blocked = !requestOpen || open.isEmpty()
        current.textVendorState.isVisible = blocked
        current.textVendorState.setText(
            when {
                detail?.request?.status == RequestStatus.CANCELLED -> R.string.compare_request_closed
                open.isEmpty() -> R.string.compare_fully_awarded
                else -> R.string.compare_request_closed
            }
        )
        if (open.isEmpty()) {
            renderClosedVendorReference()
            bindVendorPreview(null)
            return
        }
        val lowest = QuoteCompareFormat.lowestIndexes(plans.map { if (it.selectable) it.landedPaise else null })
        plans.forEachIndexed { index, plan ->
            val card = ItemVendorAwardBinding.inflate(layoutInflater, current.vendorCards, true)
            val selected = vendorQuoteId == plan.quoteId
            card.textVendorName.text = plan.vendorName
            val awardedQuote = isAwardedQuote(plan.quoteId)
            if (awardedQuote) {
                card.textVendorBadge.isVisible = true
                card.textVendorBadge.setText(R.string.compare_awarded_badge)
                card.textVendorBadge.setTextColor(requireContext().getColor(R.color.chart_accepted))
            } else {
                card.textVendorBadge.isVisible = index in lowest
                card.textVendorBadge.setText(R.string.compare_lowest_complete)
                card.textVendorBadge.setTextColor(requireContext().getColor(R.color.text_accent))
            }
            if (plan.landedPaise != null) {
                card.textVendorLanded.text = FinanceUi.formatPaise(plan.landedPaise, symbol)
                card.textVendorLandedCaption.setText(R.string.compare_landed_caption)
            } else if (!plan.selectable) {
                card.textVendorLanded.setText(R.string.compare_vendor_incomplete)
                card.textVendorLandedCaption.text = ""
            } else {
                card.textVendorLanded.setText(R.string.compare_not_comparable)
                card.textVendorLandedCaption.text = ""
            }
            val short = plan.lines.count { !it.covered }
            card.textVendorCoverage.text = if (short == 0) {
                getString(R.string.compare_vendor_covered, plan.lines.size, plan.lines.size)
            } else {
                getString(R.string.compare_vendor_short, plan.lines.size - short, plan.lines.size, short)
            }
            val deliveryWhen = plan.expectedDelivery?.let { FinanceDates.format(it) }
                ?: getString(R.string.compare_no_delivery)
            card.textVendorDelivery.text = getString(R.string.compare_expected_delivery, deliveryWhen)
            card.groupVendorCharges.isVisible = plan.goodsPaise != null && plan.landedPaise != null
            if (plan.goodsPaise != null) {
                card.textVendorGoods.text = FinanceUi.formatPaise(plan.goodsPaise, symbol)
                card.textVendorDeliveryCharge.text = FinanceUi.formatPaise(plan.deliveryPaise, symbol)
                val discount = FinanceUi.formatPaise(plan.discountPaise, symbol)
                card.textVendorDiscount.text = if (plan.discountPaise > 0L) "−$discount" else discount
            }
            val root = current.root
            val outline = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOutline)
            val primary = MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary)
            val surface = MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface)
            card.cardVendorAward.strokeWidth = if (selected) dp(2) else dp(1)
            card.cardVendorAward.strokeColor = if (selected) primary else outline
            card.cardVendorAward.setCardBackgroundColor(
                if (selected) MaterialColors.compositeARGBWithAlpha(primary, 28) else surface
            )
            card.imageVendorSelect.setImageResource(
                when {
                    !plan.selectable -> R.drawable.ic_award_blocked
                    selected -> R.drawable.ic_award_selected
                    else -> R.drawable.ic_award_unselected
                }
            )
            card.cardVendorAward.isClickable = plan.selectable && requestOpen
            card.cardVendorAward.isFocusable = plan.selectable && requestOpen
            val priceLabel = plan.landedPaise?.let { FinanceUi.formatPaise(it, symbol) }
                ?: if (plan.selectable) getString(R.string.compare_not_comparable) else getString(R.string.compare_vendor_incomplete)
            val selectLabel = if (awardedQuote) {
                getString(R.string.compare_awarded_badge)
            } else if (selected) {
                getString(R.string.compare_select_state)
            } else {
                getString(R.string.compare_unselected_state)
            }
            card.cardVendorAward.contentDescription = plan.vendorName + ". " + priceLabel + ". " + selectLabel
            if (plan.selectable && requestOpen) {
                card.cardVendorAward.setOnClickListener {
                    val choosing = vendorQuoteId != plan.quoteId
                    vendorQuoteId = if (choosing) plan.quoteId else null
                    if (choosing) previewAll = false
                    renderVendors()
                    refreshSummary()
                }
            } else {
                card.cardVendorAward.setOnClickListener(null)
            }
            val expanded = plan.quoteId in expandedVendors
            card.buttonVendorDetails.setText(
                if (expanded) {
                    R.string.compare_hide_breakdown
                } else if (plan.selectable) {
                    R.string.compare_view_breakdown
                } else {
                    R.string.compare_missing_items
                }
            )
            card.vendorLines.isVisible = expanded
            card.buttonVendorDetails.setOnClickListener {
                if (expanded) expandedVendors.remove(plan.quoteId) else expandedVendors.add(plan.quoteId)
                renderVendors()
            }
            if (expanded) bindVendorLines(card, plan)
        }
        bindVendorPreview(plans.firstOrNull { it.quoteId == vendorQuoteId && it.selectable })
    }

    private fun bindVendorPreview(plan: VendorPlan?) {
        val current = _binding ?: return
        current.vendorPreview.isVisible = plan != null
        if (plan == null) return
        current.textVendorPreviewTitle.text = getString(R.string.compare_preview_title, plan.vendorName)
        current.vendorPreviewLines.removeAllViews()
        val showAll = previewAll || plan.lines.size <= 3
        val visible = if (showAll) plan.lines else plan.lines.take(3)
        visible.forEach { line ->
            val row = ItemVendorPreviewLineBinding.inflate(layoutInflater, current.vendorPreviewLines, true)
            row.textPreviewName.text = line.row.itemName
            row.textPreviewQty.text = PackMath.formatQuantity(line.packages, line.unit, 1)
            row.textPreviewRate.text = line.ratePaise?.let { FinanceUi.formatPaise(it, symbol) }.orEmpty()
            row.textPreviewCost.text = line.linePaise?.let { FinanceUi.formatPaise(it, symbol) }.orEmpty()
        }
        if (!showAll) {
            current.vendorPreviewLines.addView(TextView(requireContext()).apply {
                text = getString(R.string.compare_preview_more, plan.lines.size - 3)
                minHeight = dp(48)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setTextColor(context.getColor(R.color.text_accent))
                setOnClickListener {
                    previewAll = true
                    bindVendorPreview(plan)
                }
            })
        }
    }

    private fun paintAwardMode(vendorSelected: Boolean) {
        val current = _binding ?: return
        paintModeChip(
            current.buttonModeVendor,
            current.textModeVendorTitle,
            current.textModeVendorSubtitle,
            vendorSelected,
            getString(R.string.compare_mode_vendor_title) + ". " + getString(R.string.compare_mode_vendor_sub)
        )
        paintModeChip(
            current.buttonModeMaterial,
            current.textModeMaterialTitle,
            current.textModeMaterialSubtitle,
            !vendorSelected,
            getString(R.string.compare_mode_material_title) + ". " + getString(R.string.compare_mode_material_sub)
        )
    }

    private fun paintModeChip(
        chip: View,
        title: TextView,
        subtitle: TextView,
        selected: Boolean,
        description: String
    ) {
        val radius = 12f * resources.displayMetrics.density
        chip.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(
                if (selected) requireContext().getColor(R.color.orange_primary) else Color.TRANSPARENT
            )
        }
        val titleColor = if (selected) R.color.orange_on_primary else R.color.text_primary_dark
        val subtitleColor = if (selected) R.color.orange_on_primary else R.color.text_secondary_dark
        title.setTextColor(requireContext().getColor(titleColor))
        subtitle.setTextColor(requireContext().getColor(subtitleColor))
        chip.contentDescription = description
        chip.isSelected = selected
    }

    private fun bindVendorLines(card: ItemVendorAwardBinding, plan: VendorPlan) {
        val shown = if (plan.selectable) plan.lines else plan.lines.filter { !it.covered }
        val showAll = plan.quoteId in expandedVendorAll || shown.size <= 4
        val visible = if (showAll) shown else shown.take(4)
        visible.forEach { line ->
            val row = ItemVendorAwardLineBinding.inflate(layoutInflater, card.vendorLines, true)
            val base = baseUnit(line.row)
            row.textAwardLineName.text = line.row.itemName
            val remaining = PackMath.formatQuantity(line.remaining, base, 1)
            val rate = line.ratePaise?.let {
                QuoteCompareFormat.perUnit(FinanceUi.formatPaise(it, symbol), line.unit)
            }
            val cost = line.linePaise?.let { FinanceUi.formatPaise(it, symbol) }
            row.textAwardLineMeta.text = listOfNotNull(remaining, rate, cost).joinToString(" · ")
            if (line.covered) {
                row.textAwardLineState.setText(R.string.compare_covered)
                row.textAwardLineState.setTextColor(requireContext().getColor(R.color.chart_accepted))
            } else {
                row.textAwardLineState.text = line.reason.orEmpty()
                row.textAwardLineState.setTextColor(requireContext().getColor(R.color.orange_800))
            }
        }
        if (!showAll) {
            val more = TextView(requireContext()).apply {
                text = getString(R.string.compare_show_all, shown.size)
                minHeight = dp(48)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setTextColor(context.getColor(R.color.text_accent))
                setOnClickListener {
                    expandedVendorAll.add(plan.quoteId)
                    renderVendors()
                }
            }
            card.vendorLines.addView(more)
        }
    }

    private fun refreshSummary() {
        val current = _binding ?: return
        if (mode == AwardMode.VENDOR) {
            refreshVendorSummary()
            return
        }
        current.textSelectionSummary.isVisible = true
        current.buttonCreateOrders.isSingleLine = true
        var count = 0
        var total = 0L
        var priced = true
        val loaded = detail
        choices.forEach { (lineId, choice) ->
            if (choice.quantity <= 0) return@forEach
            count += 1
            val offer = loaded?.quoteLines?.firstOrNull {
                it.quoteId == choice.quoteId && it.requestLineId == lineId
            }
            val lineTotal = offer?.let { PackMath.lineTotalPaise(choice.quantity, it.ratePaise) }
            if (lineTotal == null) {
                priced = false
            } else {
                total = try {
                    Math.addExact(total, lineTotal)
                } catch (_: ArithmeticException) {
                    priced = false
                    total
                }
            }
        }
        val orders = choices.filter { it.value.quantity > 0 }.map { it.value.quoteId }.distinct().size
        val anySelectable = comparison?.rows.orEmpty().any { row ->
            val remaining = PurchaseRequestMath.remaining(row.requestedQty, awarded[row.requestLineId] ?: 0)
            requestOpen && remaining > 0 && row.cells.any {
                it.state == OfferState.QUOTED && (it.availableQty ?: 0) > 0
            }
        }
        val amount = if (priced) FinanceUi.formatPaise(total, symbol) else getString(R.string.compare_not_comparable)
        current.textSelectionSummary.text = when {
            count == 0 -> getString(R.string.compare_selected_none)
            count == 1 && orders == 1 -> getString(R.string.compare_material_one, amount)
            else -> getString(R.string.compare_material_summary, count, orders, amount)
        }
        val finished = outstandingRows().isEmpty()
        val cancelled = detail?.request?.status == RequestStatus.CANCELLED
        current.buttonCreateOrders.isEnabled = anySelectable
        current.buttonCreateOrders.setText(
            when {
                cancelled -> R.string.compare_request_closed
                finished -> R.string.compare_fully_awarded
                !requestOpen -> R.string.compare_request_closed
                else -> R.string.compare_review_orders
            }
        )
    }

    private fun refreshVendorSummary() {
        val current = _binding ?: return
        val open = outstandingRows()
        val plan = vendorPlans().firstOrNull { it.quoteId == vendorQuoteId && it.selectable }
        val showNote = open.isEmpty() || !requestOpen
        current.textSelectionSummary.isVisible = showNote
        current.textSelectionSummary.text = if (open.isEmpty()) {
            getString(R.string.compare_fully_awarded)
        } else {
            getString(R.string.compare_request_closed)
        }
        val finished = open.isEmpty()
        val cancelled = detail?.request?.status == RequestStatus.CANCELLED
        current.buttonCreateOrders.isEnabled = plan != null && requestOpen && open.isNotEmpty()
        current.buttonCreateOrders.maxLines = 2
        current.buttonCreateOrders.isSingleLine = false
        current.buttonCreateOrders.text = when {
            cancelled -> getString(R.string.compare_request_closed)
            finished -> getString(R.string.compare_fully_awarded)
            !requestOpen -> getString(R.string.compare_request_closed)
            plan == null -> getString(R.string.compare_award_vendor_prompt)
            else -> getString(R.string.compare_award_vendor_action, plan.vendorName)
        }
    }

    private fun showQuoteTotals() {
        val compared = comparison ?: return
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad / 2, pad, 0)
        }
        val landed = compared.summaries.map { summary ->
            val goods = summary.goodsPaise ?: return@map null
            QuoteCompareFormat.landedPaise(goods, summary.deliveryChargesPaise, summary.discountPaise)
        }
        val lowest = QuoteCompareFormat.lowestIndexes(landed)
        compared.summaries.forEachIndexed { index, summary ->
            val card = ItemCompareTotalBinding.inflate(layoutInflater, box, true)
            card.textTotalVendor.text = summary.vendorName
            card.textTotalBadge.isVisible = index in lowest
            val goods = summary.goodsPaise
            val total = landed[index]
            val delivery = FinanceUi.formatPaise(summary.deliveryChargesPaise, symbol)
            val discount = FinanceUi.formatPaise(summary.discountPaise, symbol)
            card.textTotalCharges.text = if (goods == null || total == null) {
                getString(R.string.compare_not_comparable) + "\n" + QuoteCompareFormat.openCharges(delivery, discount)
            } else {
                QuoteCompareFormat.charges(
                    FinanceUi.formatPaise(goods, symbol),
                    delivery,
                    discount,
                    FinanceUi.formatPaise(total, symbol)
                )
            }
        }
        val scroll = android.widget.ScrollView(requireContext()).apply {
            addView(box)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.6f).toInt()
            )
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.compare_quote_totals)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun confirmAward() {
        val loaded = detail ?: return
        val selections = mutableListOf<AwardSelection>()
        choices.forEach { (lineId, choice) ->
            if (choice.quantity <= 0) return@forEach
            val line = loaded.lines.firstOrNull { it.id == lineId } ?: return@forEach
            val offer = loaded.quoteLines.firstOrNull {
                it.quoteId == choice.quoteId &&
                    it.requestLineId == lineId &&
                    it.availability == QuoteAvailability.QUOTED
            } ?: return@forEach
            val remaining = PurchaseRequestMath.remaining(line.quantity, awarded[lineId] ?: 0)
            val base = PackMath.toBase(choice.quantity, offer.unitsPerBundle)
            if (base == null || base > remaining || choice.quantity > offer.availableQty) {
                toast(R.string.payment_error_award)
                return
            }
            selections += AwardSelection(line.id, choice.quoteId, choice.quantity)
        }
        if (selections.isEmpty()) {
            toast(R.string.compare_nothing_selected)
            return
        }
        val summary = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad / 2, pad, 0)
        }
        summary.addView(sectionBody(getString(R.string.compare_po_count, selections.map { it.quoteId }.distinct().size)))
        summary.addView(sectionBody(getString(R.string.award_one_po)))
        selections.groupBy { it.quoteId }.forEach { (quoteId, lines) ->
            val quote = loaded.quotes.first { it.id == quoteId }
            summary.addView(sectionTitle(vendors[quote.vendorId]?.name ?: "Vendor"))
            lines.forEach { selection ->
                val line = loaded.lines.first { it.id == selection.requestLineId }
                val offer = loaded.quoteLines.first {
                    it.quoteId == quoteId && it.requestLineId == selection.requestLineId
                }
                val unit = offer.unitName.ifBlank { line.baseUnit }
                val total = PackMath.lineTotalPaise(selection.quantity, offer.ratePaise)
                val price = FinanceUi.formatPaise(offer.ratePaise, symbol) + if (unit.isBlank()) "" else " / $unit"
                val goods = total?.let { " · ${FinanceUi.formatPaise(it, symbol)}" }.orEmpty()
                summary.addView(
                    sectionBody(
                        line.itemName + "\n" +
                            PackMath.formatQuantity(selection.quantity, offer.unitName, offer.unitsPerBundle, line.baseUnit) +
                            " · " + price + goods
                    )
                )
            }
        }
        val scroll = android.widget.ScrollView(requireContext()).apply {
            addView(summary)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.6f).toInt()
            )
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.award_summary_title)
            .setView(scroll)
            .setPositiveButton(R.string.save) { _, _ -> submit(selections) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmVendor() {
        val plan = vendorPlans().firstOrNull { it.quoteId == vendorQuoteId && it.selectable } ?: return
        val selections = plan.lines.map { line ->
            AwardSelection(line.row.requestLineId, plan.quoteId, line.packages)
        }
        val loaded = detail ?: return
        selections.forEach { selection ->
            val line = loaded.lines.firstOrNull { it.id == selection.requestLineId } ?: return
            val offer = loaded.quoteLines.firstOrNull {
                it.quoteId == selection.quoteId &&
                    it.requestLineId == selection.requestLineId &&
                    it.availability == QuoteAvailability.QUOTED
            } ?: return
            val remaining = PurchaseRequestMath.remaining(line.quantity, awarded[selection.requestLineId] ?: 0)
            val base = PackMath.toBase(selection.quantity, offer.unitsPerBundle)
            if (base == null || base > remaining || selection.quantity > offer.availableQty) {
                toast(R.string.payment_error_award)
                return
            }
        }
        val review = DialogAwardReviewBinding.inflate(layoutInflater)
        review.textAwardVendor.text = plan.vendorName
        review.awardVendorHeader.contentDescription =
            plan.vendorName + ". " + getString(R.string.award_review_one_order)
        plan.lines.forEach { line ->
            val row = ItemAwardReviewLineBinding.inflate(layoutInflater, review.awardItemList, true)
            val quantity = PackMath.formatQuantity(line.packages, line.unit, line.factor, baseUnit(line.row))
            val rate = line.ratePaise?.let {
                QuoteCompareFormat.perUnit(FinanceUi.formatPaise(it, symbol), line.unit)
            }.orEmpty()
            val total = line.linePaise?.let { FinanceUi.formatPaise(it, symbol) }.orEmpty()
            row.textAwardItemName.text = line.row.itemName
            row.textAwardItemQty.text = quantity
            row.textAwardItemRate.text = rate
            row.textAwardItemTotal.text = total
            row.root.contentDescription = listOf(line.row.itemName, quantity, rate, total)
                .filter { it.isNotBlank() }
                .joinToString(". ")
        }
        val goods = plan.goodsPaise
        val landed = plan.landedPaise
        val priced = goods != null && landed != null
        review.groupAwardBreakdown.isVisible = priced
        review.textAwardTotal.isVisible = priced
        review.textAwardTotalLabel.isVisible = priced
        review.textAwardIncomparable.isVisible = !priced
        if (goods != null && landed != null) {
            review.textAwardGoods.text = FinanceUi.formatPaise(goods, symbol)
            review.textAwardDelivery.text = FinanceUi.formatPaise(plan.deliveryPaise, symbol)
            review.textAwardDiscount.text = "\u2212" + FinanceUi.formatPaise(plan.discountPaise, symbol)
            review.textAwardTotal.text = FinanceUi.formatPaise(landed, symbol)
        }
        capAwardItemList(review.awardItemScroll, review.awardItemList)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.award_review_title)
            .setView(review.root)
            .setPositiveButton(R.string.award_review_create) { _, _ -> submit(selections) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun capAwardItemList(scroll: View, list: View) {
        val width = (resources.displayMetrics.widthPixels - dp(80)).coerceAtLeast(dp(240))
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        list.measure(widthSpec, heightSpec)
        val reserved = dp(300)
        val maxList = (resources.displayMetrics.heightPixels - reserved).coerceAtLeast(dp(120))
        if (list.measuredHeight > maxList) {
            scroll.layoutParams = scroll.layoutParams.apply { height = maxList }
        }
    }

    private fun submit(selections: List<AwardSelection>) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { app().vendorRepository.awardRequest(requestId, selections) }
                .onSuccess { findNavController().popBackStack() }
                .onFailure { toast(FinanceUi.message(requireContext(), it)) }
        }
    }

    private fun baseUnit(row: MaterialComparisonRow): String =
        row.baseUnit.ifBlank { getString(R.string.pack_base_unit) }

    private fun packageUnit(cell: ComparisonCell, row: MaterialComparisonRow): String =
        cell.unitName.ifBlank { baseUnit(row) }

    private fun sectionTitle(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(context.getColor(R.color.text_primary_dark))
            setPadding(0, dp(16), 0, dp(4))
        }
    }

    private fun sectionBody(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(context.getColor(R.color.text_secondary_dark))
        }
    }

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
        const val ARG_REQUEST_ID = "requestId"
    }
}

private enum class AwardMode { VENDOR, MATERIAL }

private enum class ItemFilter { ALL, NEEDS_AWARD, AWARDED }

private data class VendorLinePlan(
    val row: MaterialComparisonRow,
    val remaining: Int,
    val packages: Int,
    val ratePaise: Long?,
    val unit: String,
    val factor: Int,
    val linePaise: Long?,
    val covered: Boolean,
    val reason: String?
)

private data class VendorPlan(
    val quoteId: Long,
    val vendorName: String,
    val deliveryPaise: Long,
    val discountPaise: Long,
    val expectedDelivery: Long?,
    val lines: List<VendorLinePlan>,
    val goodsPaise: Long?,
    val landedPaise: Long?,
    val selectable: Boolean
)

private enum class OfferSort { UNIT_RATE, ESTIMATED_COST }

private data class OfferChoice(val quoteId: Long, var quantity: Int)

private class OfferCard(
    val requestLineId: Long,
    val quoteId: Long,
    val binding: ItemCompareOfferBinding,
    val canSelect: Boolean,
    val factor: Int,
    val unit: String,
    val base: String,
    val remaining: Int,
    val available: Int
)

private class JumpAdapter(
    private val onPick: (Long) -> Unit
) : RecyclerView.Adapter<JumpAdapter.Holder>() {

    private var rows: List<MaterialComparisonRow> = emptyList()

    fun submit(next: List<MaterialComparisonRow>) {
        rows = next
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemCompareJumpBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.binding.textJumpName.text = row.itemName
        val base = row.baseUnit.ifBlank { holder.itemView.context.getString(R.string.pack_base_unit) }
        val meta = if (row.notes.isBlank()) {
            PackMath.formatQuantity(row.requestedQty, base, 1)
        } else {
            row.notes
        }
        holder.binding.textJumpMeta.text = meta
        holder.itemView.contentDescription = row.itemName
        holder.itemView.setOnClickListener { onPick(row.requestLineId) }
    }

    override fun getItemCount(): Int = rows.size

    class Holder(val binding: ItemCompareJumpBinding) : RecyclerView.ViewHolder(binding.root)
}
