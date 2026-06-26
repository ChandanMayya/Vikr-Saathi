package com.kex.vikrsaathi.ui.bill

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.databinding.ItemBillLineBinding
import com.kex.vikrsaathi.util.PriceCalculator

class BillLineItemAdapter(
    private val currencySymbol: String,
    private val onQuantityChange: (Int, Int) -> Unit,
    private val onDiscountChange: (Int, Double) -> Unit,
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

            binding.buttonRemoveLine.setOnClickListener {
                if (!readOnly) {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onRemove(pos)
                }
            }
        }

        fun bind(line: BillLineItem) {
            suppressUpdates = true
            binding.textSlNo.text = bindingAdapterPosition.plus(1).toString()
            binding.textParticulars.text = line.name
            binding.textMrp.text = PriceCalculator.formatAmount(line.mrp, currencySymbol)
            setQuantityTextIfNeeded(line.quantity)
            setDiscountTextIfNeeded(line.discount)
            binding.textPrice.text = PriceCalculator.formatAmount(line.lineTotal, currencySymbol)
            suppressUpdates = false

            binding.buttonRemoveLine.visibility = if (readOnly) View.GONE else View.VISIBLE
            binding.editDiscount.isEnabled = !readOnly
            binding.editQuantity.isEnabled = !readOnly
        }

        fun updateLineTotal(line: BillLineItem) {
            binding.textPrice.text = PriceCalculator.formatAmount(line.lineTotal, currencySymbol)
        }

        private fun updateLineTotalForQuantity(line: BillLineItem, quantity: Int) {
            val total = PriceCalculator.priceAfterDiscount(line.mrp, line.discount) * quantity
            binding.textPrice.text = PriceCalculator.formatAmount(total, currencySymbol)
        }

        private fun updateLineTotalForDiscount(line: BillLineItem, discount: Double) {
            val total = PriceCalculator.priceAfterDiscount(line.mrp, discount) * line.quantity
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
    }

    private class DiffCallback : DiffUtil.ItemCallback<BillLineItem>() {
        override fun areItemsTheSame(oldItem: BillLineItem, newItem: BillLineItem) =
            oldItem.itemId == newItem.itemId && oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: BillLineItem, newItem: BillLineItem) =
            oldItem == newItem

        override fun getChangePayload(oldItem: BillLineItem, newItem: BillLineItem): Any? {
            if (oldItem.itemId != newItem.itemId ||
                oldItem.name != newItem.name ||
                oldItem.mrp != newItem.mrp
            ) {
                return null
            }
            if (oldItem.quantity != newItem.quantity || oldItem.discount != newItem.discount) {
                return PAYLOAD_LINE_TOTAL
            }
            return null
        }
    }

    companion object {
        private const val PAYLOAD_LINE_TOTAL = "line_total"
    }
}
