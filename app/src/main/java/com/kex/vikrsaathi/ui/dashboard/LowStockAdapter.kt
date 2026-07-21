package com.kex.vikrsaathi.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.databinding.ItemDashboardLowStockBinding

class LowStockAdapter :
    ListAdapter<LowStockRow, LowStockAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDashboardLowStockBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemDashboardLowStockBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: LowStockRow) {
            binding.textLowStockItemName.text = row.name
            binding.textLowStockItemQty.text = row.stockQty.toString()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<LowStockRow>() {
        override fun areItemsTheSame(oldItem: LowStockRow, newItem: LowStockRow): Boolean =
            oldItem.name == newItem.name && oldItem.stockQty == newItem.stockQty

        override fun areContentsTheSame(oldItem: LowStockRow, newItem: LowStockRow): Boolean =
            oldItem == newItem
    }
}
