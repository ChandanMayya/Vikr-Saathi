package com.kex.vikrsaathi.ui.stock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.databinding.ItemStockRowBinding

class StockAdapter(
    private val lowStockThreshold: Int,
    private val onAdjust: (Item) -> Unit,
    private val onStockIn: (Item) -> Unit,
    private val onOpenMovements: (Item) -> Unit
) : ListAdapter<Item, StockAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStockRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemStockRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item) {
            binding.textStockItemName.text = item.name
            binding.textStockQty.text = item.stockQty.toString()
            binding.textStockBarcode.text = item.barcode?.ifBlank { null } ?: "-"
            val color = if (item.stockQty <= lowStockThreshold) {
                ContextCompat.getColor(binding.root.context, R.color.orange_700)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.text_accent)
            }
            binding.textStockQty.setTextColor(color)
            binding.buttonStockAdjust.setOnClickListener { onAdjust(item) }
            binding.buttonStockIn.setOnClickListener { onStockIn(item) }
            binding.root.setOnClickListener { onOpenMovements(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
    }
}
