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
import com.kex.vikrsaathi.databinding.ItemStockRowCompactBinding
import com.kex.vikrsaathi.databinding.ItemStockRowDetailsBinding
import com.kex.vikrsaathi.util.ListViewMode

class StockAdapter(
    private val lowStockThreshold: Int,
    private val onAdjust: (Item) -> Unit,
    private val onStockIn: (Item) -> Unit,
    private val onOpenMovements: (Item) -> Unit
) : ListAdapter<Item, RecyclerView.ViewHolder>(DiffCallback()) {

    var viewMode: ListViewMode = ListViewMode.COMFORTABLE
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun getItemViewType(position: Int): Int = when (viewMode) {
        ListViewMode.COMFORTABLE -> VIEW_COMFORTABLE
        ListViewMode.COMPACT -> VIEW_COMPACT
        ListViewMode.DETAILS -> VIEW_DETAILS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_COMPACT -> CompactVH(ItemStockRowCompactBinding.inflate(inflater, parent, false))
            VIEW_DETAILS -> DetailsVH(ItemStockRowDetailsBinding.inflate(inflater, parent, false))
            else -> ComfortableVH(ItemStockRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is ComfortableVH -> holder.bind(item)
            is CompactVH -> holder.bind(item)
            is DetailsVH -> holder.bind(item)
        }
    }

    private fun qtyColor(context: android.content.Context, qty: Int): Int =
        ContextCompat.getColor(
            context,
            if (qty <= lowStockThreshold) R.color.orange_700 else R.color.text_accent
        )

    inner class ComfortableVH(private val binding: ItemStockRowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textStockItemName.text = item.name
            binding.textStockQty.text = item.stockQty.toString()
            binding.textStockBarcode.text = item.barcode?.ifBlank { null } ?: "-"
            binding.textStockQty.setTextColor(qtyColor(binding.root.context, item.stockQty))
            binding.buttonStockAdjust.setOnClickListener { onAdjust(item) }
            binding.buttonStockIn.setOnClickListener { onStockIn(item) }
            binding.root.setOnClickListener { onOpenMovements(item) }
        }
    }

    inner class CompactVH(private val binding: ItemStockRowCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textStockItemName.text = item.name
            binding.textStockQty.text = item.stockQty.toString()
            binding.textStockQty.setTextColor(qtyColor(binding.root.context, item.stockQty))
            binding.buttonStockAdjust.setOnClickListener { onAdjust(item) }
            binding.buttonStockIn.setOnClickListener { onStockIn(item) }
            binding.root.setOnClickListener { onOpenMovements(item) }
        }
    }

    inner class DetailsVH(private val binding: ItemStockRowDetailsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textStockItemName.text = item.name
            binding.textStockQty.text = item.stockQty.toString()
            binding.textStockBarcode.text = item.barcode?.ifBlank { null } ?: "-"
            binding.textStockQty.setTextColor(qtyColor(binding.root.context, item.stockQty))
            binding.buttonStockAdjust.setOnClickListener { onAdjust(item) }
            binding.buttonStockIn.setOnClickListener { onStockIn(item) }
            binding.root.setOnClickListener { onOpenMovements(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
    }

    companion object {
        private const val VIEW_COMFORTABLE = 0
        private const val VIEW_COMPACT = 1
        private const val VIEW_DETAILS = 2
    }
}
