package com.loctell.vikrsaathi.ui.item

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.loctell.vikrsaathi.data.entity.Item
import com.loctell.vikrsaathi.databinding.ItemMasterBinding
import com.loctell.vikrsaathi.util.PriceCalculator

class ItemAdapter(
    private val currencySymbol: String,
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
            binding.textItemName.text = item.name
            binding.textItemBarcode.text = item.barcode ?: "-"
            binding.textItemMrp.text = PriceCalculator.formatAmount(item.mrp, currencySymbol)
            binding.textItemDiscount.text = "${item.discount}%"
            binding.buttonEditItem.setOnClickListener { onEdit(item) }
            binding.buttonDeleteItem.setOnClickListener { onDelete(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
    }
}
