package com.loctell.vikrsaathi.ui.bill

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.loctell.vikrsaathi.data.model.BillLineItem
import com.loctell.vikrsaathi.databinding.ItemBillLineBinding
import com.loctell.vikrsaathi.util.PriceCalculator

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
                    if (quantity != getItem(pos).quantity) {
                        onQuantityChange(pos, quantity)
                    }
                }
            })

            binding.editDiscount.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (suppressUpdates || readOnly) return
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    val discount = s?.toString()?.trim()?.toDoubleOrNull() ?: return
                    if (discount != getItem(pos).discount) {
                        onDiscountChange(pos, discount)
                    }
                }
            })

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
            binding.editDiscount.setText(String.format("%.1f", line.discount))
            binding.editQuantity.setText(line.quantity.toString())
            binding.textPrice.text = PriceCalculator.formatAmount(line.lineTotal, currencySymbol)
            suppressUpdates = false

            binding.buttonRemoveLine.visibility = if (readOnly) View.GONE else View.VISIBLE
            binding.editDiscount.isEnabled = !readOnly
            binding.editQuantity.isEnabled = !readOnly
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<BillLineItem>() {
        override fun areItemsTheSame(oldItem: BillLineItem, newItem: BillLineItem) =
            oldItem.itemId == newItem.itemId && oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: BillLineItem, newItem: BillLineItem) =
            oldItem == newItem
    }
}
