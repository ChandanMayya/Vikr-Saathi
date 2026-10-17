package com.kex.vikrsaathi.ui.finance

import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.PurchaseRequestLine
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.data.repository.RequestQuoteLineInput
import com.kex.vikrsaathi.databinding.FragmentPurchaseRequestQuoteBinding
import com.kex.vikrsaathi.databinding.ItemQuoteResponseBinding
import com.kex.vikrsaathi.domain.finance.PackMath
import com.kex.vikrsaathi.domain.finance.QuoteAvailability
import kotlinx.coroutines.launch

class PurchaseRequestQuoteFragment : Fragment(R.layout.fragment_purchase_request_quote) {

    private var _binding: FragmentPurchaseRequestQuoteBinding? = null
    private val binding get() = _binding!!
    private var requestId: Long = 0L
    private var vendor: Vendor? = null
    private var validUntil: Long? = null
    private var expectedDelivery: Long? = null
    private val rows = mutableListOf<QuoteItemRow>()
    private var saving = false

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPurchaseRequestQuoteBinding.bind(view)
        requestId = arguments?.getLong(ARG_REQUEST_ID) ?: 0L
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { insetView, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            insetView.updatePadding(bottom = (ime - nav).coerceAtLeast(0))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.buttonCancelQuote.setOnClickListener { findNavController().popBackStack() }
        binding.buttonSaveQuote.setOnClickListener { save() }
        keepVisible(binding.editVendor)
        keepVisible(binding.editDelivery)
        keepVisible(binding.editDiscount)
        keepVisible(binding.editNotes)
        binding.buttonValidUntil.setOnClickListener {
            FinanceDates.showSingle(this, R.string.valid_until) { picked ->
                validUntil = picked
                binding.buttonValidUntil.text = getString(R.string.valid_until) + ": " + FinanceDates.format(picked)
            }
        }
        binding.buttonExpectedDelivery.setOnClickListener {
            FinanceDates.showSingle(this, R.string.expected_delivery_short) { picked ->
                expectedDelivery = picked
                binding.buttonExpectedDelivery.text =
                    getString(R.string.expected_delivery_short) + ": " + FinanceDates.format(picked)
            }
        }
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            val detail = app().vendorRepository.requestDetail(requestId)
            val quotedVendorIds = detail?.quotes?.map { it.vendorId }?.toSet().orEmpty()
            val vendors = app().vendorRepository.getActive().filter { it.id !in quotedVendorIds }
            val items = app().itemRepository.getAllSync().associateBy { it.id }
            val current = _binding ?: return@launch
            if (detail == null || detail.lines.isEmpty()) {
                toast(R.string.payment_error_lines)
                findNavController().popBackStack()
                return@launch
            }
            if (vendors.isEmpty()) {
                toast(if (quotedVendorIds.isEmpty()) R.string.select_vendor else R.string.all_vendors_quoted)
                findNavController().popBackStack()
                return@launch
            }
            val symbol = app().settingsRepository.currencySymbol
            current.layoutDelivery.prefixText = symbol
            current.layoutDiscount.prefixText = symbol
            current.editVendor.setAdapter(
                android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, vendors.map { it.name })
            )
            current.editVendor.setOnItemClickListener { _, _, position, _ ->
                vendor = vendors[position]
                current.layoutVendor.error = null
            }
            if (vendors.size == 1) {
                vendor = vendors.first()
                current.editVendor.setText(vendors.first().name, false)
            }
            current.quoteItems.removeAllViews()
            rows.clear()
            detail.lines.forEachIndexed { index, line ->
                val card = ItemQuoteResponseBinding.inflate(layoutInflater, current.quoteItems, true)
                val pack = PackFields(card.editUnit, card.editFactor, card.textPackPreview)
                val row = QuoteItemRow(line, card, pack, expanded = index == 0)
                rows += row
                card.layoutRate.prefixText = symbol
                card.textItemTitle.text = getString(
                    R.string.quote_item_heading,
                    index + 1,
                    detail.lines.size,
                    line.itemName
                )
                card.textRequested.text = getString(
                    R.string.request_line_requested,
                    orderedLabel(line)
                )
                PackForm.prefill(pack, line.itemId?.let { items[it] })
                if (line.enteredQty > 0 && (line.unitsPerBundle > 1 || line.orderUnit.isNotBlank())) {
                    PackForm.apply(
                        pack,
                        line.orderUnit.ifBlank { line.baseUnit },
                        line.unitsPerBundle.coerceAtLeast(1),
                        locked = false
                    )
                }
                PackForm.watch(pack, listOf(card.editRate, card.editAvailable)) {
                    val factor = pack.factor.text?.toString()?.toIntOrNull() ?: 1
                    val unit = pack.unit.text?.toString().orEmpty()
                    val qty = card.editAvailable.text?.toString()?.toIntOrNull() ?: 0
                    PackForm.setRateHint(card.editRate, unit)
                    card.layoutAvailable.suffixText = unit.trim()
                    pack.preview.text = PackMath.formatQuantity(qty, unit, factor, line.baseUnit)
                }
                card.groupResponse.addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (!isChecked) return@addOnButtonCheckedListener
                    row.mode = when (checkedId) {
                        R.id.buttonQuoted -> MODE_QUOTED
                        R.id.buttonUnavailable -> MODE_UNAVAILABLE
                        else -> MODE_NOT_QUOTED
                    }
                    clearItemError(row)
                    applyRow(row)
                    refreshProgress()
                }
                card.headerResponse.setOnClickListener {
                    row.expanded = !row.expanded
                    applyRow(row)
                }
                listOf(card.editRate, card.editAvailable, card.editUnit, card.editFactor, card.editSpec).forEach {
                    keepVisible(it)
                }
                watchClear(card.editRate, card.layoutRate, row)
                watchClear(card.editAvailable, card.layoutAvailable, row)
                watchClear(card.editUnit, card.layoutUnit, row)
                watchClear(card.editFactor, card.layoutFactor, row)
                applyRow(row)
            }
            refreshProgress()
            current.progressQuote.isVisible = false
            current.quoteScroll.isVisible = true
            current.quoteActions.isVisible = true
        }
    }

    private fun applyRow(row: QuoteItemRow) {
        row.binding.itemBody.isVisible = row.expanded
        row.binding.quotedFields.isVisible = row.mode == MODE_QUOTED
        row.binding.imageExpand.rotation = if (row.expanded) -90f else 90f
        val action = getString(if (row.expanded) R.string.quote_collapse else R.string.quote_expand)
        val status = statusLabel(row)
        paintStatus(row.binding.textItemStatus, status, row.mode)
        row.binding.headerResponse.contentDescription =
            "${row.binding.textItemTitle.text}. $status. $action"
    }

    private fun statusLabel(row: QuoteItemRow): String = when (row.mode) {
        MODE_QUOTED -> getString(R.string.state_quoted)
        MODE_UNAVAILABLE -> getString(R.string.state_unavailable)
        else -> getString(R.string.quote_status_needs)
    }

    private fun paintStatus(view: android.widget.TextView, label: String, mode: Int) {
        val (background, foreground) = when (mode) {
            MODE_QUOTED -> 0xFFE8F5E9.toInt() to requireContext().getColor(R.color.chart_accepted)
            MODE_UNAVAILABLE -> 0xFFEEEEEE.toInt() to 0xFF424242.toInt()
            else -> requireContext().getColor(R.color.orange_100) to requireContext().getColor(R.color.orange_900)
        }
        val density = resources.displayMetrics.density
        view.background = GradientDrawable().apply {
            cornerRadius = 12f * density
            setColor(background)
        }
        view.setTextColor(foreground)
        view.text = label
    }

    private fun refreshProgress() {
        val answered = rows.count { it.mode != MODE_NOT_QUOTED }
        val total = rows.size
        val label = getString(R.string.quote_progress, answered, total)
        binding.textQuoteProgress.text = label
        binding.textQuoteProgress.contentDescription = label
        binding.progressItems.max = total.coerceAtLeast(1)
        binding.progressItems.setProgressCompat(answered, true)
        binding.progressItems.contentDescription = label
    }

    private fun save() {
        if (saving) return
        rows.forEach { clearItemError(it) }
        binding.layoutVendor.error = null
        binding.layoutDelivery.error = null
        binding.layoutDiscount.error = null
        val chosen = vendor ?: return reveal(binding.layoutVendor, getString(R.string.select_vendor))
        val delivery = FinanceUi.parseAmountPaise(binding.editDelivery.text?.toString())
        val discount = FinanceUi.parseAmountPaise(binding.editDiscount.text?.toString())
        if (delivery < 0L) {
            return reveal(binding.layoutDelivery, getString(R.string.quote_error_negative, getString(R.string.delivery_charges)))
        }
        if (discount < 0L) {
            return reveal(binding.layoutDiscount, getString(R.string.quote_error_negative, getString(R.string.quote_discount)))
        }
        val inputs = mutableListOf<RequestQuoteLineInput>()
        for (row in rows) {
            when (row.mode) {
                MODE_QUOTED -> {
                    val rateText = row.binding.editRate.text?.toString()?.trim().orEmpty()
                    val rate = FinanceUi.parseAmountPaise(rateText)
                    if (rateText.isEmpty() || rate < 0L) {
                        return revealItem(row, row.binding.layoutRate, getString(R.string.quote_error_rate, row.line.itemName))
                    }
                    val qty = row.binding.editAvailable.text?.toString()?.toIntOrNull() ?: 0
                    if (qty <= 0) {
                        return revealItem(
                            row,
                            row.binding.layoutAvailable,
                            getString(R.string.quote_error_qty, row.line.itemName)
                        )
                    }
                    val unit = row.pack.unit.text?.toString().orEmpty().trim()
                    val factor = row.pack.factor.text?.toString()?.toIntOrNull()
                    if (factor == null || PackMath.validateTransaction(unit, factor) != null) {
                        val field = if (unit.isBlank()) row.binding.layoutUnit else row.binding.layoutFactor
                        return revealItem(row, field, getString(R.string.quote_error_pack, row.line.itemName))
                    }
                    inputs += RequestQuoteLineInput(
                        requestLineId = row.line.id,
                        availability = QuoteAvailability.QUOTED,
                        ratePaise = rate,
                        availableQty = qty,
                        notes = row.binding.editSpec.text?.toString().orEmpty(),
                        unitName = unit,
                        unitsPerBundle = factor
                    )
                }
                MODE_UNAVAILABLE -> inputs += RequestQuoteLineInput(
                    requestLineId = row.line.id,
                    availability = QuoteAvailability.UNAVAILABLE,
                    ratePaise = 0L,
                    availableQty = 0,
                    notes = ""
                )
            }
        }
        if (inputs.isEmpty()) {
            val first = rows.firstOrNull() ?: return
            return revealItem(first, null, getString(R.string.quote_error_none))
        }
        saving = true
        binding.buttonSaveQuote.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                app().vendorRepository.saveRequestQuote(
                    requestId = requestId,
                    vendorId = chosen.id,
                    quoteDate = System.currentTimeMillis(),
                    validUntil = validUntil,
                    deliveryChargesPaise = delivery,
                    discountPaise = discount,
                    expectedDelivery = expectedDelivery,
                    notes = binding.editNotes.text?.toString().orEmpty(),
                    lines = inputs
                )
            }.onSuccess {
                findNavController().popBackStack()
            }.onFailure {
                saving = false
                _binding?.buttonSaveQuote?.isEnabled = true
                toast(FinanceUi.message(requireContext(), it))
            }
        }
    }

    private fun reveal(layout: TextInputLayout, message: String) {
        layout.error = message
        layout.post {
            layout.requestRectangleOnScreen(Rect(0, 0, layout.width, layout.height), true)
            layout.editText?.requestFocus()
        }
    }

    private fun revealItem(row: QuoteItemRow, layout: TextInputLayout?, message: String) {
        row.expanded = true
        applyRow(row)
        row.binding.textItemError.isVisible = true
        row.binding.textItemError.text = message
        layout?.error = message
        row.binding.root.post {
            row.binding.root.requestRectangleOnScreen(
                Rect(0, 0, row.binding.root.width, row.binding.root.height),
                true
            )
            layout?.editText?.requestFocus()
        }
    }

    private fun clearItemError(row: QuoteItemRow) {
        row.binding.textItemError.isVisible = false
        row.binding.layoutRate.error = null
        row.binding.layoutAvailable.error = null
        row.binding.layoutUnit.error = null
        row.binding.layoutFactor.error = null
    }

    private fun watchClear(edit: TextInputEditText, layout: TextInputLayout, row: QuoteItemRow) {
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (layout.error != null) clearItemError(row)
            }
        })
    }

    private fun keepVisible(view: View) {
        view.setOnFocusChangeListener { focused, hasFocus ->
            if (hasFocus) {
                focused.post {
                    focused.requestRectangleOnScreen(Rect(0, 0, focused.width, focused.height), true)
                }
            }
        }
    }

    private fun orderedLabel(line: PurchaseRequestLine): String {
        return if (line.enteredQty > 0) {
            PackMath.formatQuantity(line.enteredQty, line.orderUnit, line.unitsPerBundle.coerceAtLeast(1), line.baseUnit)
        } else {
            PackMath.formatQuantity(line.quantity, line.baseUnit, 1)
        }
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
        private const val MODE_NOT_QUOTED = 0
        private const val MODE_QUOTED = 1
        private const val MODE_UNAVAILABLE = 2
    }
}

private class QuoteItemRow(
    val line: PurchaseRequestLine,
    val binding: ItemQuoteResponseBinding,
    val pack: PackFields,
    var mode: Int = 0,
    var expanded: Boolean
)
