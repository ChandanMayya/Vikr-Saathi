package com.kex.vikrsaathi.ui.bill

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.data.draft.HeldDraftSummary
import com.kex.vikrsaathi.databinding.ItemHeldBillBinding
import com.kex.vikrsaathi.util.PriceCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HeldBillAdapter(
    private val currencySymbol: String,
    private val onResume: (HeldDraftSummary) -> Unit,
    private val onDelete: (HeldDraftSummary) -> Unit
) : ListAdapter<HeldDraftSummary, HeldBillAdapter.ViewHolder>(DiffCallback()) {

    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHeldBillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemHeldBillBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(held: HeldDraftSummary) {
            binding.textHeldCustomer.text = held.customerName
            binding.textHeldMeta.text = binding.root.context.getString(
                com.kex.vikrsaathi.R.string.held_bill_list_meta,
                held.itemCount,
                PriceCalculator.formatAmount(held.grandTotal, currencySymbol),
                dateTimeFormat.format(Date(held.heldAt))
            )
            binding.buttonResumeHeld.setOnClickListener { onResume(held) }
            binding.buttonDeleteHeld.setOnClickListener { onDelete(held) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<HeldDraftSummary>() {
        override fun areItemsTheSame(oldItem: HeldDraftSummary, newItem: HeldDraftSummary) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: HeldDraftSummary, newItem: HeldDraftSummary) =
            oldItem == newItem
    }
}
