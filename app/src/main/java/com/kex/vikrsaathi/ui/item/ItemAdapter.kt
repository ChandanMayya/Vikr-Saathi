package com.kex.vikrsaathi.ui.item

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.databinding.ItemMasterBinding
import com.kex.vikrsaathi.util.PriceCalculator

class ItemAdapter(
    private val currencySymbol: String,
    private val lowStockThreshold: Int,
    private val onEdit: (Item) -> Unit,
    private val onDelete: (Item) -> Unit
) : ListAdapter<Item, ItemAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMasterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemMasterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item) {
            binding.textItemBadge.text =
                item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            binding.textItemName.text = item.name
            val displayPrice = item.sellingPrice
                ?: PriceCalculator.priceAfterDiscount(item.mrp, item.discount)
            binding.textItemPrice.text = PriceCalculator.formatAmount(displayPrice, currencySymbol)
            binding.textItemMrp.text = PriceCalculator.formatAmount(item.mrp, currencySymbol)
            binding.textItemDiscount.text = String.format("%.1f%%", item.discount)
            binding.textItemBarcode.text = item.barcode?.ifBlank { null } ?: "-"
            binding.textItemStock.text = item.stockQty.toString()
            val stockColor = if (item.stockQty <= lowStockThreshold) {
                ContextCompat.getColor(binding.root.context, R.color.orange_700)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.text_primary_dark)
            }
            binding.textItemStock.setTextColor(stockColor)
            binding.buttonEditItem.setOnClickListener { onEdit(item) }
            binding.buttonDeleteItem.setOnClickListener { onDelete(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
    }
}
