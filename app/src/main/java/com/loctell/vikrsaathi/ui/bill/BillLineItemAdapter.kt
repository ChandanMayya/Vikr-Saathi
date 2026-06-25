package com.loctell.vikrsaathi.ui.bill

import android.view.LayoutInflater
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBillLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(private val binding: ItemBillLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(line: BillLineItem, position: Int) {
            binding.textSlNo.text = (position + 1).toString()
            binding.textParticulars.text = line.name
            binding.textMrp.text = PriceCalculator.formatAmount(line.mrp, currencySymbol)
            binding.editDiscount.setText(String.format("%.1f", line.discount))
            binding.textPrice.text = PriceCalculator.formatAmount(line.lineTotal, currencySymbol)
            binding.textQuantity.text = line.quantity.toString()

            binding.buttonDecreaseQty.setOnClickListener {
                if (line.quantity > 1) onQuantityChange(position, line.quantity - 1)
            }
            binding.buttonIncreaseQty.setOnClickListener {
                onQuantityChange(position, line.quantity + 1)
            }
            binding.buttonRemoveLine.setOnClickListener { onRemove(position) }
            binding.editDiscount.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val discount = binding.editDiscount.text.toString().toDoubleOrNull() ?: line.discount
                    onDiscountChange(position, discount)
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<BillLineItem>() {
        override fun areItemsTheSame(oldItem: BillLineItem, newItem: BillLineItem) =
            oldItem.itemId == newItem.itemId && oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: BillLineItem, newItem: BillLineItem) =
            oldItem == newItem
    }
}
