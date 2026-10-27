package com.kex.vikrsaathi.ui.stock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.databinding.ItemStockRowBinding
import com.kex.vikrsaathi.databinding.ItemStockRowCompactBinding
import com.kex.vikrsaathi.databinding.ItemStockRowDetailsBinding
import com.kex.vikrsaathi.util.ListViewMode
import com.kex.vikrsaathi.util.PriceCalculator
import java.util.Locale

class StockAdapter(
    private val currencySymbol: String,
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

    var vendorNames: Map<Long, String> = emptyMap()
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

    private fun qtyLabel(item: Item): String {
        val unit = item.stockUnit().trim()
        return if (unit.isEmpty()) item.stockQty.toString() else "${item.stockQty} $unit"
    }

    private fun money(amount: Double): String = PriceCalculator.formatAmount(amount, currencySymbol)

    private fun sellingAmount(item: Item): Double =
        item.sellingPrice ?: PriceCalculator.priceAfterDiscount(item.mrp, item.discount)

    private fun bindLowStock(view: TextView, item: Item) {
        view.isVisible = item.stockQty <= lowStockThreshold
    }

    private fun discountText(discount: Double): String? {
        if (kotlin.math.abs(discount) < 0.005) return null
        val whole = kotlin.math.abs(discount - discount.toInt()) < 0.05
        return if (whole) "${discount.toInt()}%" else String.format(Locale.getDefault(), "%.1f%%", discount)
    }

    /** Remarks that are only standing in for a missing base unit stay on the quantity. */
    private fun remarkText(item: Item): String? {
        val remarks = item.remarks.trim()
        if (remarks.isEmpty()) return null
        if (item.baseUnit.isBlank() && remarks == item.stockUnit().trim()) return null
        return remarks
    }

    private fun vendorName(item: Item): String? =
        item.preferredVendorId?.let { vendorNames[it]?.trim() }?.takeIf { it.isNotEmpty() }

    private fun showText(container: View, textView: TextView, value: String?) {
        val shown = value?.trim().orEmpty()
        container.isVisible = shown.isNotEmpty()
        if (shown.isNotEmpty()) textView.text = shown
    }

    inner class ComfortableVH(private val binding: ItemStockRowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textStockItemName.text = item.name
            binding.textStockQty.text = qtyLabel(item)
            binding.textStockQty.setTextColor(qtyColor(binding.root.context, item.stockQty))
            bindLowStock(binding.textStockLow, item)
            showText(binding.layoutStockBarcode, binding.textStockBarcode, item.barcode)
            binding.textStockMrp.text = money(item.mrp)
            binding.textStockSelling.text = money(sellingAmount(item))
            binding.buttonStockAdjust.setOnClickListener { onAdjust(item) }
            binding.buttonStockIn.setOnClickListener { onStockIn(item) }
            binding.root.setOnClickListener { onOpenMovements(item) }
        }
    }

    inner class CompactVH(private val binding: ItemStockRowCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            val context = binding.root.context
            binding.textStockItemName.text = item.name
            binding.textStockQty.text = qtyLabel(item)
            binding.textStockQty.setTextColor(qtyColor(context, item.stockQty))
            bindLowStock(binding.textStockLow, item)
            val parts = mutableListOf(
                context.getString(R.string.list_selling_price) + " " + money(sellingAmount(item))
            )
            item.barcode?.trim()?.takeIf { it.isNotEmpty() }?.let { barcode ->
                parts += context.getString(R.string.col_barcode) + " " + barcode
            }
            binding.textStockMeta.isVisible = parts.isNotEmpty()
            if (parts.isNotEmpty()) binding.textStockMeta.text = parts.joinToString("  ·  ")
            binding.buttonStockAdjust.setOnClickListener { onAdjust(item) }
            binding.buttonStockIn.setOnClickListener { onStockIn(item) }
            binding.root.setOnClickListener { onOpenMovements(item) }
        }
    }

    inner class DetailsVH(private val binding: ItemStockRowDetailsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textStockItemName.text = item.name
            binding.textStockQty.text = qtyLabel(item)
            binding.textStockQty.setTextColor(qtyColor(binding.root.context, item.stockQty))
            bindLowStock(binding.textStockLow, item)
            binding.textStockMrp.text = money(item.mrp)
            val discount = discountText(item.discount)
            binding.layoutStockDiscount.isVisible = discount != null
            if (discount != null) binding.textStockDiscount.text = discount
            binding.textStockSelling.text = money(sellingAmount(item))
            showText(binding.layoutStockBarcode, binding.textStockBarcode, item.barcode)
            showText(binding.layoutStockBaseUnit, binding.textStockBaseUnit, item.baseUnit)
            showText(binding.layoutStockRemarks, binding.textStockRemarks, remarkText(item))
            binding.layoutStockIdentity.isVisible =
                binding.layoutStockBarcode.isVisible ||
                    binding.layoutStockBaseUnit.isVisible ||
                    binding.layoutStockRemarks.isVisible
            val purchaseUnit = item.purchaseUnit.trim()
            binding.layoutStockPurchaseUnit.isVisible = purchaseUnit.isNotEmpty()
            if (purchaseUnit.isNotEmpty()) binding.textStockPurchaseUnit.text = purchaseUnit
            val showFactor = purchaseUnit.isNotEmpty() && item.unitsPerPurchase > 0
            binding.layoutStockPackFactor.isVisible = showFactor
            if (showFactor) binding.textStockPackFactor.text = item.unitsPerPurchase.toString()
            val vendor = vendorName(item)
            binding.layoutStockVendor.isVisible = vendor != null
            if (vendor != null) binding.textStockVendor.text = vendor
            binding.layoutStockPurchase.isVisible =
                binding.layoutStockPurchaseUnit.isVisible ||
                    binding.layoutStockPackFactor.isVisible ||
                    binding.layoutStockVendor.isVisible
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
