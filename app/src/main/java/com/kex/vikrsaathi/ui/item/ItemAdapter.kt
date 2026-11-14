package com.kex.vikrsaathi.ui.item

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
import com.kex.vikrsaathi.data.repository.ExpiryAlert
import com.kex.vikrsaathi.databinding.ItemMasterBinding
import com.kex.vikrsaathi.databinding.ItemMasterCompactBinding
import com.kex.vikrsaathi.databinding.ItemMasterDetailsBinding
import com.kex.vikrsaathi.util.ListViewMode
import com.kex.vikrsaathi.util.PriceCalculator
import java.util.Locale

class ItemAdapter(
    private val currencySymbol: String,
    private val lowStockThreshold: Int,
    private val onEdit: (Item) -> Unit,
    private val onDelete: (Item) -> Unit,
    private val onOpenBatches: (Item) -> Unit
) : ListAdapter<Item, RecyclerView.ViewHolder>(DiffCallback()) {

    var batchTrackingEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var expiringByItem: Map<Long, List<ExpiryAlert>> = emptyMap()
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

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

    private fun money(amount: Double): String = PriceCalculator.formatAmount(amount, currencySymbol)

    private fun sellingAmount(item: Item): Double =
        item.sellingPrice ?: PriceCalculator.priceAfterDiscount(item.mrp, item.discount)

    private fun stockLabel(item: Item): String {
        val unit = item.stockUnit().trim()
        return if (unit.isEmpty()) item.stockQty.toString() else "${item.stockQty} $unit"
    }

    private fun stockColor(context: android.content.Context, qty: Int): Int =
        ContextCompat.getColor(
            context,
            if (qty <= lowStockThreshold) R.color.orange_700 else R.color.text_accent
        )

    private fun bindStock(view: TextView, low: TextView, item: Item) {
        view.text = stockLabel(item)
        view.setTextColor(stockColor(view.context, item.stockQty))
        low.isVisible = item.stockQty <= lowStockThreshold
    }

    private fun discountText(discount: Double): String? {
        if (kotlin.math.abs(discount) < 0.005) return null
        val whole = kotlin.math.abs(discount - discount.toInt()) < 0.05
        return if (whole) "${discount.toInt()}%" else String.format(Locale.getDefault(), "%.1f%%", discount)
    }

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

    inner class ComfortableVH(private val binding: ItemMasterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textItemName.text = item.name
            bindStock(binding.textItemStock, binding.textItemLow, item)
            showText(binding.layoutItemBarcode, binding.textItemBarcode, item.barcode)
            binding.textItemMrp.text = money(item.mrp)
            binding.textItemPrice.text = money(sellingAmount(item))
            val discount = discountText(item.discount)
            binding.layoutItemDiscount.isVisible = discount != null
            if (discount != null) binding.textItemDiscount.text = discount
            bindExpiry(binding.textItemExpiry, item)
            binding.root.setOnClickListener { openBatches(item) }
            binding.buttonEditItem.setOnClickListener { onEdit(item) }
            binding.buttonDeleteItem.setOnClickListener { onDelete(item) }
        }
    }

    inner class CompactVH(private val binding: ItemMasterCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            val context = binding.root.context
            binding.textItemName.text = item.name
            bindStock(binding.textItemStock, binding.textItemLow, item)
            bindExpiry(binding.textItemExpiry, item)
            binding.root.setOnClickListener { openBatches(item) }
            val parts = mutableListOf(
                context.getString(R.string.list_selling_price) + " " + money(sellingAmount(item))
            )
            item.barcode?.trim()?.takeIf { it.isNotEmpty() }?.let { barcode ->
                parts += context.getString(R.string.col_barcode) + " " + barcode
            }
            binding.textItemMeta.text = parts.joinToString("  ·  ")
            binding.buttonEditItem.setOnClickListener { onEdit(item) }
            binding.buttonDeleteItem.setOnClickListener { onDelete(item) }
        }
    }

    inner class DetailsVH(private val binding: ItemMasterDetailsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.textItemName.text = item.name
            bindStock(binding.textItemStock, binding.textItemLow, item)
            binding.textItemMrp.text = money(item.mrp)
            binding.textItemPrice.text = money(sellingAmount(item))
            val discount = discountText(item.discount)
            binding.layoutItemDiscount.isVisible = discount != null
            if (discount != null) binding.textItemDiscount.text = discount
            showText(binding.layoutItemBarcode, binding.textItemBarcode, item.barcode)
            showText(binding.layoutItemBaseUnit, binding.textItemBaseUnit, item.baseUnit)
            showText(binding.layoutItemRemarks, binding.textItemRemarks, remarkText(item))
            binding.layoutItemIdentity.isVisible =
                binding.layoutItemBarcode.isVisible ||
                    binding.layoutItemBaseUnit.isVisible ||
                    binding.layoutItemRemarks.isVisible
            val purchaseUnit = item.purchaseUnit.trim()
            binding.layoutItemPurchaseUnit.isVisible = purchaseUnit.isNotEmpty()
            if (purchaseUnit.isNotEmpty()) binding.textItemPurchaseUnit.text = purchaseUnit
            val showFactor = purchaseUnit.isNotEmpty() && item.unitsPerPurchase > 0
            binding.layoutItemPackFactor.isVisible = showFactor
            if (showFactor) binding.textItemPackFactor.text = item.unitsPerPurchase.toString()
            val vendor = vendorName(item)
            binding.layoutItemVendor.isVisible = vendor != null
            if (vendor != null) binding.textItemVendor.text = vendor
            binding.layoutItemPurchase.isVisible =
                binding.layoutItemPurchaseUnit.isVisible ||
                    binding.layoutItemPackFactor.isVisible ||
                    binding.layoutItemVendor.isVisible
            bindExpiry(binding.textItemExpiry, item)
            binding.root.setOnClickListener { openBatches(item) }
            binding.buttonEditItem.setOnClickListener { onEdit(item) }
            binding.buttonDeleteItem.setOnClickListener { onDelete(item) }
        }
    }

    private fun openBatches(item: Item) {
        if (batchTrackingEnabled && item.trackBatches) onOpenBatches(item)
    }

    private fun bindExpiry(view: TextView, item: Item) {
        val alerts = if (batchTrackingEnabled && item.trackBatches) {
            expiringByItem[item.id].orEmpty()
        } else {
            emptyList()
        }
        view.isVisible = alerts.isNotEmpty()
        if (alerts.isEmpty()) return
        val lead = alerts.first()
        val name = lead.lotNumber?.trim()?.takeIf { it.isNotEmpty() } ?: lead.internalRef
        val extra = alerts.size - 1
        view.text = if (lead.expired) {
            if (extra > 0) view.context.getString(R.string.item_batch_expired_more, name, extra)
            else view.context.getString(R.string.item_batch_expired, name)
        } else {
            if (extra > 0) view.context.getString(R.string.item_batch_expiring_more, name, extra)
            else view.context.getString(R.string.item_batch_expiring, name)
        }
        view.setTextColor(
            ContextCompat.getColor(
                view.context,
                if (alerts.any { it.expired }) R.color.balance_payable else R.color.orange_700
            )
        )
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
