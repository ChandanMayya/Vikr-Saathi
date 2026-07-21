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
import com.kex.vikrsaathi.databinding.ItemMasterCompactBinding
import com.kex.vikrsaathi.databinding.ItemMasterDetailsBinding
import com.kex.vikrsaathi.util.ListViewMode
import com.kex.vikrsaathi.util.PriceCalculator

class ItemAdapter(
    private val currencySymbol: String,
    private val lowStockThreshold: Int,
    private val onEdit: (Item) -> Unit,
    private val onDelete: (Item) -> Unit
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
            VIEW_COMPACT -> CompactVH(ItemMasterCompactBinding.inflate(inflater, parent, false))
            VIEW_DETAILS -> DetailsVH(ItemMasterDetailsBinding.inflate(inflater, parent, false))
            else -> ComfortableVH(ItemMasterBinding.inflate(inflater, parent, false))
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

    private fun displayPrice(item: Item): String {
        val price = item.sellingPrice
            ?: PriceCalculator.priceAfterDiscount(item.mrp, item.discount)
        return PriceCalculator.formatAmount(price, currencySymbol)
    }

    private fun stockColor(context: android.content.Context, qty: Int): Int =
        ContextCompat.getColor(
            context,
            if (qty <= lowStockThreshold) R.color.orange_700 else R.color.text_primary_dark
        )

    inner class ComfortableVH(private val binding: ItemMasterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textItemBadge.text =
                item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            binding.textItemName.text = item.name
            binding.textItemPrice.text = displayPrice(item)
            binding.textItemMrp.text = PriceCalculator.formatAmount(item.mrp, currencySymbol)
            binding.textItemDiscount.text = String.format("%.1f%%", item.discount)
            binding.textItemBarcode.text = item.barcode?.ifBlank { null } ?: "-"
            binding.textItemStock.text = item.stockQty.toString()
            binding.textItemStock.setTextColor(stockColor(binding.root.context, item.stockQty))
            binding.buttonEditItem.setOnClickListener { onEdit(item) }
            binding.buttonDeleteItem.setOnClickListener { onDelete(item) }
        }
    }

    inner class CompactVH(private val binding: ItemMasterCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textItemName.text = item.name
            binding.textItemPrice.text = displayPrice(item)
            binding.textItemStock.text =
                binding.root.context.getString(R.string.stock_label_format, item.stockQty)
            binding.textItemStock.setTextColor(stockColor(binding.root.context, item.stockQty))
            binding.buttonEditItem.setOnClickListener { onEdit(item) }
            binding.buttonDeleteItem.setOnClickListener { onDelete(item) }
        }
    }

    inner class DetailsVH(private val binding: ItemMasterDetailsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textItemName.text = item.name
            binding.textItemMrp.text = PriceCalculator.formatAmount(item.mrp, currencySymbol)
            binding.textItemDiscount.text = String.format("%.0f%%", item.discount)
            binding.textItemStock.text = item.stockQty.toString()
            binding.textItemStock.setTextColor(stockColor(binding.root.context, item.stockQty))
            binding.textItemBarcode.text = item.barcode?.ifBlank { null } ?: "-"
            binding.buttonEditItem.setOnClickListener { onEdit(item) }
            binding.buttonDeleteItem.setOnClickListener { onDelete(item) }
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
