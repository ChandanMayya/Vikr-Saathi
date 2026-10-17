package com.kex.vikrsaathi.ui.bill

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.databinding.ItemBillLineBinding
import com.kex.vikrsaathi.util.PriceCalculator

class BillLineItemAdapter(
    private val currencySymbol: String,
    private val onQuantityChange: (Int, Int) -> Unit,
    private val onDiscountChange: (Int, Double) -> Unit,
    private val onRoundOffChange: (Int, Double) -> Unit,
    private val onRateChange: (Int, Double) -> Unit,
    private val onRemove: (Int) -> Unit
) : ListAdapter<BillLineItem, BillLineItemAdapter.ViewHolder>(DiffCallback()) {

    var readOnly: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBillLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            if (payloads.contains(PAYLOAD_LINE_TOTAL)) {
                holder.updateLineTotal(getItem(position))
            }
        }
    }

    inner class ViewHolder(private val binding: ItemBillLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var suppressUpdates = false

        init {
            binding.editQuantity.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (suppressUpdates || readOnly) return
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    val quantity = s?.toString()?.trim()?.toIntOrNull() ?: return
                    if (quantity < 1) return
                    updateLineTotalForQuantity(getItem(pos), quantity)
                    if (quantity != getItem(pos).quantity) {
                        onQuantityChange(pos, quantity)
                    }
                }
            })

            binding.editQuantity.setOnFocusChangeListener { _, hasFocus ->
                if (suppressUpdates || readOnly || hasFocus) return@setOnFocusChangeListener
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener
                val quantity = binding.editQuantity.text?.toString()?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                suppressUpdates = true
                binding.editQuantity.setText(quantity.toString())
                binding.editQuantity.setSelection(binding.editQuantity.text?.length ?: 0)
                suppressUpdates = false
            }

            binding.editDiscount.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (suppressUpdates || readOnly) return
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    val discount = s?.toString()?.trim()?.toDoubleOrNull() ?: return
                    updateLineTotalForDiscount(getItem(pos), discount)
                    if (discount != getItem(pos).discount) {
                        onDiscountChange(pos, discount)
                    }
                }
            })

            binding.editDiscount.setOnFocusChangeListener { _, hasFocus ->
                if (suppressUpdates || readOnly || hasFocus) return@setOnFocusChangeListener
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener
                val discount = binding.editDiscount.text?.toString()?.trim()?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                val formatted = String.format("%.1f", discount)
                suppressUpdates = true
                binding.editDiscount.setText(formatted)
                binding.editDiscount.setSelection(binding.editDiscount.text?.length ?: 0)
                suppressUpdates = false
            }

            binding.editRoundOff.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (suppressUpdates || readOnly) return
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    val text = s?.toString()?.trim().orEmpty()
                    val roundOff = if (text.isEmpty() || text == "-" || text == "+") {
                        0.0
                    } else {
                        text.toDoubleOrNull() ?: return
                    }
                    updateLineTotalForRoundOff(getItem(pos), roundOff)
                    if (roundOff != getItem(pos).roundOff) {
                        onRoundOffChange(pos, roundOff)
                    }
                }
            })

            binding.editRoundOff.setOnFocusChangeListener { _, hasFocus ->
                if (suppressUpdates || readOnly || hasFocus) return@setOnFocusChangeListener
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener
                val roundOff = binding.editRoundOff.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
                val formatted = String.format("%.2f", roundOff)
                suppressUpdates = true
                binding.editRoundOff.setText(formatted)
                binding.editRoundOff.setSelection(binding.editRoundOff.text?.length ?: 0)
                suppressUpdates = false
            }

            binding.editRate.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (suppressUpdates || readOnly) return
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    val rate = s?.toString()?.trim()?.toDoubleOrNull() ?: return
                    if (rate <= 0.0) return
                    updateLineTotalForRate(getItem(pos), rate)
                    if (rate != getItem(pos).mrp) {
                        onRateChange(pos, rate)
                    }
                }
            })

            binding.editRate.setOnFocusChangeListener { _, hasFocus ->
                if (suppressUpdates || readOnly || hasFocus) return@setOnFocusChangeListener
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener
                suppressUpdates = true
                binding.editRate.setText(String.format("%.2f", getItem(pos).mrp))
                suppressUpdates = false
            }

            binding.buttonRemoveLine.setOnClickListener {
                if (!readOnly) {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onRemove(pos)
                }
            }

            binding.buttonQtyMinus.setOnClickListener { stepQuantity(-1) }
            binding.buttonQtyPlus.setOnClickListener { stepQuantity(1) }
        }

        private fun stepQuantity(delta: Int) {
            if (readOnly) return
            val pos = bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return
            val next = getItem(pos).quantity + delta
            if (next < 1) return
            suppressUpdates = true
            binding.editQuantity.setText(next.toString())
            suppressUpdates = false
            updateLineTotalForQuantity(getItem(pos), next)
            onQuantityChange(pos, next)
        }

        fun bind(line: BillLineItem) {
            suppressUpdates = true
            binding.textSlNo.text = bindingAdapterPosition.plus(1).toString()
            binding.textParticulars.text = line.name
            bindNewItemState(line)
            binding.layoutQuantity.visibility = View.VISIBLE
            binding.editQuantity.visibility = View.VISIBLE
            refreshFigures(line)
            suppressUpdates = false

            val editable = !readOnly
            binding.buttonRemoveLine.visibility = if (readOnly) View.GONE else View.VISIBLE
            binding.editDiscount.isEnabled = editable
            binding.editQuantity.isEnabled = editable
            binding.editRoundOff.isEnabled = editable
            binding.editRate.isEnabled = editable
            binding.buttonQtyMinus.isEnabled = editable
            binding.buttonQtyPlus.isEnabled = editable
        }

        fun updateLineTotal(line: BillLineItem) {
            refreshFigures(line)
        }

        private fun refreshFigures(line: BillLineItem) {
            val wasSuppressed = suppressUpdates
            suppressUpdates = true
            setQuantityTextIfNeeded(line.quantity)
            setDiscountTextIfNeeded(line.discount)
            setRoundOffTextIfNeeded(line.roundOff)
            if (line.isNew) setRateTextIfNeeded(line.mrp)
            val unitPrice = PriceCalculator.formatAmount(line.mrp, currencySymbol)
            binding.textUnitPrice.text = unitPrice
            binding.textMrp.text = unitPrice
            binding.textPrice.text = PriceCalculator.formatAmount(line.lineTotal, currencySymbol)
            suppressUpdates = wasSuppressed
        }

        private fun bindNewItemState(line: BillLineItem) {
            binding.textNewBadge.visibility = if (line.isNew) View.VISIBLE else View.GONE
            binding.textNewBadge.setText(R.string.new_item_flag)
            binding.textMrpLabel.setText(if (line.isNew) R.string.rate else R.string.mrp)
            binding.textMrp.visibility = if (line.isNew) View.GONE else View.VISIBLE
            binding.editRate.visibility = if (line.isNew) View.VISIBLE else View.GONE
        }

        private fun updateLineTotalForRate(line: BillLineItem, rate: Double) {
            val total = PriceCalculator.priceAfterDiscount(rate, line.discount) * line.quantity + line.roundOff
            binding.textPrice.text = PriceCalculator.formatAmount(total, currencySymbol)
        }

        private fun setRateTextIfNeeded(rate: Double) {
            val edit = binding.editRate
            if (edit.hasFocus()) return

            val current = edit.text?.toString()?.trim().orEmpty()
            if (current.toDoubleOrNull()?.let { kotlin.math.abs(it - rate) < 0.005 } == true) {
                return
            }

            edit.setText(String.format("%.2f", rate))
            edit.setSelection(edit.text?.length ?: 0)
        }

        private fun updateLineTotalForQuantity(line: BillLineItem, quantity: Int) {
            val total = PriceCalculator.priceAfterDiscount(line.mrp, line.discount) * quantity + line.roundOff
            binding.textPrice.text = PriceCalculator.formatAmount(total, currencySymbol)
        }

        private fun updateLineTotalForDiscount(line: BillLineItem, discount: Double) {
            val total = PriceCalculator.priceAfterDiscount(line.mrp, discount) * line.quantity + line.roundOff
            binding.textPrice.text = PriceCalculator.formatAmount(total, currencySymbol)
        }

        private fun updateLineTotalForRoundOff(line: BillLineItem, roundOff: Double) {
            val total = PriceCalculator.priceAfterDiscount(line.mrp, line.discount) * line.quantity + roundOff
            binding.textPrice.text = PriceCalculator.formatAmount(total, currencySymbol)
        }

        private fun setQuantityTextIfNeeded(quantity: Int) {
            val edit = binding.editQuantity
            if (edit.hasFocus()) return

            val current = edit.text?.toString()?.trim().orEmpty()
            if (current.toIntOrNull() == quantity) return

            edit.setText(quantity.toString())
            edit.setSelection(edit.text?.length ?: 0)
        }

        private fun setDiscountTextIfNeeded(discount: Double) {
            val edit = binding.editDiscount
            if (edit.hasFocus()) return

            val current = edit.text?.toString()?.trim().orEmpty()
            if (current.toDoubleOrNull()?.let { kotlin.math.abs(it - discount) < 0.001 } == true) {
                return
            }

            val formatted = String.format("%.1f", discount)
            edit.setText(formatted)
            edit.setSelection(edit.text?.length ?: 0)
        }

        private fun setRoundOffTextIfNeeded(roundOff: Double) {
            val edit = binding.editRoundOff
            if (edit.hasFocus()) return

            val current = edit.text?.toString()?.trim().orEmpty()
            if (current.toDoubleOrNull()?.let { kotlin.math.abs(it - roundOff) < 0.005 } == true) {
                return
            }

            val formatted = String.format("%.2f", roundOff)
            edit.setText(formatted)
            edit.setSelection(edit.text?.length ?: 0)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<BillLineItem>() {
        override fun areItemsTheSame(oldItem: BillLineItem, newItem: BillLineItem): Boolean {
            if (oldItem.lineKey.isNotEmpty() && oldItem.lineKey == newItem.lineKey) return true
            if (oldItem.itemId != null && oldItem.itemId == newItem.itemId && oldItem.name == newItem.name) {
                return true
            }
            return oldItem.itemId == null && newItem.itemId == null &&
                oldItem.isNew && newItem.isNew &&
                oldItem.name.trim().equals(newItem.name.trim(), ignoreCase = true)
        }

        override fun areContentsTheSame(oldItem: BillLineItem, newItem: BillLineItem) =
            oldItem == newItem

        override fun getChangePayload(oldItem: BillLineItem, newItem: BillLineItem): Any? {
            if (oldItem.lineKey != newItem.lineKey ||
                oldItem.itemId != newItem.itemId ||
                oldItem.name != newItem.name ||
                oldItem.isNew != newItem.isNew ||
                oldItem.openingStock != newItem.openingStock
            ) {
                return null
            }
            if (oldItem.mrp != newItem.mrp ||
                oldItem.quantity != newItem.quantity ||
                oldItem.discount != newItem.discount ||
                oldItem.roundOff != newItem.roundOff
            ) {
                return PAYLOAD_LINE_TOTAL
            }
            return null
        }
    }

    companion object {
        private const val PAYLOAD_LINE_TOTAL = "line_total"
    }
}
