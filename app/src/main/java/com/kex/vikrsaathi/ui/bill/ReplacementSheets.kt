package com.kex.vikrsaathi.ui.bill

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.databinding.BottomSheetAddReplacementBinding
import com.kex.vikrsaathi.databinding.ItemReplacementChoiceBinding
import com.kex.vikrsaathi.domain.bill.ReplacementRatePolicy
import com.kex.vikrsaathi.util.PriceCalculator
import java.util.Locale

interface ReplacementSheetHost {
    fun loadReplacementItems(query: String, onResult: (List<Item>) -> Unit)
    fun replacementChoice(item: Item): ReplacementChoice
    fun currencySymbol(): String
    fun addApprovedReplacement(
        item: Item,
        name: String,
        rate: Double,
        discountPercent: Double,
        quantity: Int
    )
}

class AddReplacementBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddReplacementBinding? = null
    private val binding get() = _binding!!
    private val host get() = parentFragment as ReplacementSheetHost

    private var selected: Item? = null
    private var choice: ReplacementChoice? = null
    private var quantity = 1
    private var writingSelection = false
    private var searchGeneration = 0
    private lateinit var adapter: MasterItemAdapter

    override fun getTheme(): Int = R.style.ThemeOverlay_VikrSaathi_BottomSheet

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddReplacementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = MasterItemAdapter(requireContext(), host.currencySymbol())
        binding.autoChooseItem.setAdapter(adapter)
        binding.autoChooseItem.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) ?: return@setOnItemClickListener
            select(item)
        }
        binding.autoChooseItem.doAfterTextChanged { text ->
            if (writingSelection) return@doAfterTextChanged
            val typed = text?.toString().orEmpty()
            if (selected != null && typed == selected?.name) return@doAfterTextChanged
            clearSelection()
            loadMatches(typed, showDropdown = true)
        }
        binding.autoChooseItem.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) return@setOnFocusChangeListener
            loadMatches(binding.autoChooseItem.text?.toString().orEmpty(), showDropdown = true)
        }
        binding.buttonClearItem.setOnClickListener {
            writingSelection = true
            binding.autoChooseItem.setText("", false)
            writingSelection = false
            clearSelection()
        }
        binding.buttonQtyMinus.setOnClickListener { setQuantity(quantity - 1) }
        binding.buttonQtyPlus.setOnClickListener { setQuantity(quantity + 1) }
        binding.editUnitRate.doAfterTextChanged { refreshSummary() }
        binding.editDiscount.doAfterTextChanged { refreshSummary() }
        binding.buttonCancel.setOnClickListener { dismiss() }
        binding.buttonAddItem.setOnClickListener { confirm() }
        setQuantity(1)
    }

    override fun onStart() {
        super.onStart()
        prepareSheet(binding.scrollAddReplacement)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadMatches(query: String, showDropdown: Boolean) {
        val generation = ++searchGeneration
        host.loadReplacementItems(query) { matches ->
            if (_binding == null || generation != searchGeneration) return@loadReplacementItems
            adapter.submit(matches)
            binding.layoutChooseItem.helperText = if (query.isNotBlank() && matches.isEmpty()) {
                getString(R.string.no_master_items)
            } else {
                null
            }
            if (showDropdown && binding.autoChooseItem.hasFocus()) binding.autoChooseItem.showDropDown()
        }
    }

    private fun select(item: Item) {
        selected = item
        choice = host.replacementChoice(item)
        writingSelection = true
        binding.autoChooseItem.setText(item.name, false)
        writingSelection = false
        binding.autoChooseItem.dismissDropDown()
        val current = choice ?: return
        binding.cardSelectedItem.isVisible = true
        binding.textSelectedName.text = current.name
        val symbol = host.currencySymbol()
        binding.textSelectedRate.text = getString(
            R.string.return_item_master_label,
            PriceCalculator.formatAmount(current.master.net, symbol)
        )
        binding.sectionPrice.isVisible = true
        binding.rowQuantity.isVisible = true
        binding.layoutUnitRate.prefixText = symbol
        binding.layoutUnitRate.error = null
        binding.layoutDiscount.error = null
        binding.editUnitRate.setText(String.format(Locale.US, "%.2f", current.master.unitRate))
        binding.editDiscount.setText(trimNumber(current.master.discountPercent))
        binding.textPriceNote.text = when (current.comparison.note) {
            ReplacementRatePolicy.PriceNote.NOT_ON_BILL -> getString(R.string.return_not_on_bill)
            ReplacementRatePolicy.PriceNote.MATCHES -> getString(R.string.return_price_matches)
            ReplacementRatePolicy.PriceNote.DIFFERS -> getString(R.string.return_price_differs)
        }
        binding.textOriginalContext.text = originalContext(current, symbol)
        refreshSummary()
        binding.scrollAddReplacement.post { binding.scrollAddReplacement.fullScroll(View.FOCUS_DOWN) }
    }

    private fun originalContext(current: ReplacementChoice, symbol: String): String {
        val originals = current.comparison.originals
        if (originals.isEmpty()) return ""
        val lines = originals.joinToString("\n") { price ->
            getString(
                R.string.return_original_discount,
                PriceCalculator.formatAmount(price.unitRate, symbol),
                trimNumber(price.discountPercent)
            )
        }
        return if (originals.size > 1) {
            getString(R.string.return_multiple_prices) + "\n" + lines
        } else {
            lines
        }
    }

    private fun clearSelection() {
        selected = null
        choice = null
        binding.cardSelectedItem.isVisible = false
        binding.sectionPrice.isVisible = false
        binding.rowQuantity.isVisible = false
        binding.layoutUnitRate.error = null
        binding.layoutDiscount.error = null
    }

    private fun setQuantity(value: Int) {
        quantity = value.coerceAtLeast(1)
        binding.textQuantity.text = quantity.toString()
        if (_binding != null && binding.sectionPrice.isVisible) refreshSummary()
    }

    private fun refreshSummary() {
        val symbol = host.currencySymbol()
        val rate = ReplacementRatePolicy.parseRate(binding.editUnitRate.text?.toString().orEmpty())
        val discount = ReplacementRatePolicy.parseDiscount(binding.editDiscount.text?.toString().orEmpty())
        if (rate == null || discount == null) {
            binding.textNetSummary.text = ""
            return
        }
        val net = PriceCalculator.priceAfterDiscount(rate, discount)
        val total = ReplacementRatePolicy.lineTotal(rate, discount, quantity)
        binding.textNetSummary.text = getString(R.string.return_net_unit) + " " +
            PriceCalculator.formatAmount(net, symbol) + "\n" +
            getString(R.string.quantity) + " " + quantity + "\n" +
            getString(R.string.return_line_total) + " " + PriceCalculator.formatAmount(total, symbol)
    }

    private fun confirm() {
        val current = choice
        val item = selected
        if (current == null || item == null || binding.autoChooseItem.text?.toString() != item.name) {
            binding.layoutChooseItem.error = getString(R.string.return_choose_item_first)
            return
        }
        binding.layoutChooseItem.error = null
        val rate = ReplacementRatePolicy.parseRate(binding.editUnitRate.text?.toString().orEmpty())
        val discount = ReplacementRatePolicy.parseDiscount(binding.editDiscount.text?.toString().orEmpty())
        binding.layoutUnitRate.error = if (rate == null) getString(R.string.return_rate_invalid) else null
        binding.layoutDiscount.error = if (discount == null) getString(R.string.return_discount_invalid) else null
        if (rate == null || discount == null) return
        host.addApprovedReplacement(item, current.name, rate, discount, quantity)
        dismiss()
    }

    private fun trimNumber(value: Double): String {
        val text = String.format(Locale.US, "%.2f", value)
        return text.trimEnd('0').trimEnd('.')
    }
}

private class MasterItemAdapter(
    context: android.content.Context,
    private val currencySymbol: String
) : ArrayAdapter<Item>(context, 0) {

    private val rows = mutableListOf<Item>()

    fun submit(items: List<Item>) {
        rows.clear()
        rows.addAll(items)
        clear()
        addAll(items)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            ItemReplacementChoiceBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            ItemReplacementChoiceBinding.bind(convertView)
        }
        val item = getItem(position)
        if (item != null) {
            binding.textChoiceName.text = item.name
            val rate = ReplacementRatePolicy.catalogRate(item.mrp, item.discount, item.sellingPrice)
            binding.textChoiceRate.text = PriceCalculator.formatAmount(rate, currencySymbol)
        }
        return binding.root
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            return FilterResults().apply {
                values = rows.toList()
                count = rows.size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?): CharSequence {
            return (resultValue as? Item)?.name.orEmpty()
        }
    }
}

private fun BottomSheetDialogFragment.prepareSheet(scroll: View) {
    val sheetDialog = dialog as? BottomSheetDialog ?: return
    sheetDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    val sheet = sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
    val behavior = BottomSheetBehavior.from(sheet)
    behavior.skipCollapsed = true
    behavior.isFitToContents = true
    behavior.maxHeight = (resources.displayMetrics.heightPixels * 0.92f).toInt()
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
    val basePadding = scroll.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, basePadding + maxOf(ime, bars))
        insets
    }
}
