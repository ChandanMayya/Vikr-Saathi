package com.kex.vikrsaathi.ui.finance

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.databinding.ItemVendorBinding
import com.kex.vikrsaathi.databinding.ItemVendorCompactBinding
import com.kex.vikrsaathi.databinding.ItemVendorDetailsBinding
import com.kex.vikrsaathi.util.ListViewMode

data class VendorCard(
    val vendor: Vendor,
    val balanceText: String
)

class VendorAdapter(
    private val onOpen: (Vendor) -> Unit,
    private val onEdit: (Vendor) -> Unit,
    private val onArchive: (Vendor) -> Unit
) : ListAdapter<VendorCard, RecyclerView.ViewHolder>(DiffCallback()) {

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
            VIEW_COMPACT -> CompactViewHolder(ItemVendorCompactBinding.inflate(inflater, parent, false))
            VIEW_DETAILS -> DetailsViewHolder(ItemVendorDetailsBinding.inflate(inflater, parent, false))
            else -> ComfortableViewHolder(ItemVendorBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val card = getItem(position)
        when (holder) {
            is ComfortableViewHolder -> holder.bind(card)
            is CompactViewHolder -> holder.bind(card)
            is DetailsViewHolder -> holder.bind(card)
        }
    }

    inner class ComfortableViewHolder(
        private val binding: ItemVendorBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card: VendorCard) {
            val vendor = card.vendor
            binding.textVendorBadge.text = vendor.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            binding.textVendorName.text = displayName(vendor)
            binding.textVendorPhone.text = vendor.phone.ifBlank { "-" }
            binding.textVendorAddress.text = vendor.address.ifBlank { "-" }
            binding.textVendorBalance.text = card.balanceText
            binding.buttonEditVendor.setOnClickListener { onEdit(vendor) }
            binding.buttonArchiveVendor.setOnClickListener { onArchive(vendor) }
            binding.root.setOnClickListener { onOpen(vendor) }
        }
    }

    inner class CompactViewHolder(
        private val binding: ItemVendorCompactBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card: VendorCard) {
            val vendor = card.vendor
            binding.textVendorBadge.text = vendor.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            binding.textVendorName.text = displayName(vendor)
            binding.textVendorPhone.text = vendor.phone.ifBlank { "-" }
            binding.textVendorAddress.text = vendor.address.ifBlank { "-" }
            binding.textVendorBalance.text = card.balanceText
            binding.buttonEditVendor.setOnClickListener { onEdit(vendor) }
            binding.buttonArchiveVendor.setOnClickListener { onArchive(vendor) }
            binding.root.setOnClickListener { onOpen(vendor) }
        }
    }

    inner class DetailsViewHolder(
        private val binding: ItemVendorDetailsBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card: VendorCard) {
            val vendor = card.vendor
            binding.textVendorName.text = displayName(vendor)
            binding.textVendorPhone.text = vendor.phone.ifBlank { "-" }
            binding.textVendorAddress.text = vendor.address.ifBlank { "-" }
            binding.textVendorBalance.text = card.balanceText
            binding.buttonEditVendor.setOnClickListener { onEdit(vendor) }
            binding.buttonArchiveVendor.setOnClickListener { onArchive(vendor) }
            binding.root.setOnClickListener { onOpen(vendor) }
        }
    }

    private fun displayName(vendor: Vendor): String {
        return if (vendor.archived) "${vendor.name} · archived" else vendor.name
    }

    private class DiffCallback : DiffUtil.ItemCallback<VendorCard>() {
        override fun areItemsTheSame(oldItem: VendorCard, newItem: VendorCard) =
            oldItem.vendor.id == newItem.vendor.id

        override fun areContentsTheSame(oldItem: VendorCard, newItem: VendorCard) = oldItem == newItem
    }

    companion object {
        private const val VIEW_COMFORTABLE = 0
        private const val VIEW_COMPACT = 1
        private const val VIEW_DETAILS = 2
    }
}
