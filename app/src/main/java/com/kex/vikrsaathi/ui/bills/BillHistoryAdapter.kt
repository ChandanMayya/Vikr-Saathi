package com.kex.vikrsaathi.ui.bills

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.databinding.ItemBillHistoryBinding
import com.kex.vikrsaathi.databinding.ItemBillHistoryCompactBinding
import com.kex.vikrsaathi.databinding.ItemBillHistoryDetailsBinding
import com.kex.vikrsaathi.util.ListViewMode
import com.kex.vikrsaathi.util.PriceCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BillHistoryAdapter(
    private val currencySymbol: String,
    private val onOpen: (BillWithDetails) -> Unit,
    private val onDuplicate: (BillWithDetails) -> Unit,
    private val onDelete: (BillWithDetails) -> Unit
) : ListAdapter<BillWithDetails, RecyclerView.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

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
            VIEW_COMPACT -> CompactVH(ItemBillHistoryCompactBinding.inflate(inflater, parent, false))
            VIEW_DETAILS -> DetailsVH(ItemBillHistoryDetailsBinding.inflate(inflater, parent, false))
            else -> ComfortableVH(ItemBillHistoryBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val bill = getItem(position)
        when (holder) {
            is ComfortableVH -> holder.bind(bill)
            is CompactVH -> holder.bind(bill)
            is DetailsVH -> holder.bind(bill)
        }
    }

    inner class ComfortableVH(private val binding: ItemBillHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bill: BillWithDetails) {
            val counter = bill.bill.invoiceCounter
            binding.textBillBadge.text = if (counter > 0) counter.toString() else "#"
            binding.textBillNumber.text = bill.bill.billNumber
            binding.textBillCustomer.text = bill.customer?.name ?: "-"
            binding.textBillDate.text = dateFormat.format(Date(bill.bill.date))
            binding.textBillTotal.text = PriceCalculator.formatAmount(bill.bill.total, currencySymbol)
            binding.root.setOnClickListener { onOpen(bill) }
            binding.buttonDuplicateBill.setOnClickListener { onDuplicate(bill) }
            binding.buttonDeleteBill.setOnClickListener { onDelete(bill) }
        }
    }

    inner class CompactVH(private val binding: ItemBillHistoryCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bill: BillWithDetails) {
            binding.textBillNumber.text = bill.bill.billNumber
            binding.textBillCustomer.text = bill.customer?.name ?: "-"
            binding.textBillTotal.text = PriceCalculator.formatAmount(bill.bill.total, currencySymbol)
            binding.textBillDate.text = dateFormat.format(Date(bill.bill.date))
            binding.root.setOnClickListener { onOpen(bill) }
            binding.buttonDuplicateBill.setOnClickListener { onDuplicate(bill) }
            binding.buttonDeleteBill.setOnClickListener { onDelete(bill) }
        }
    }

    inner class DetailsVH(private val binding: ItemBillHistoryDetailsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bill: BillWithDetails) {
            binding.textBillNumber.text = bill.bill.billNumber
            binding.textBillCustomer.text = bill.customer?.name ?: "-"
            binding.textBillDate.text = dateFormat.format(Date(bill.bill.date))
            binding.textBillTotal.text = PriceCalculator.formatAmount(bill.bill.total, currencySymbol)
            binding.root.setOnClickListener { onOpen(bill) }
            binding.buttonDuplicateBill.setOnClickListener { onDuplicate(bill) }
            binding.buttonDeleteBill.setOnClickListener { onDelete(bill) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<BillWithDetails>() {
        override fun areItemsTheSame(oldItem: BillWithDetails, newItem: BillWithDetails) =
            oldItem.bill.id == newItem.bill.id

        override fun areContentsTheSame(oldItem: BillWithDetails, newItem: BillWithDetails) =
            oldItem == newItem
    }

    companion object {
        private const val VIEW_COMFORTABLE = 0
        private const val VIEW_COMPACT = 1
        private const val VIEW_DETAILS = 2
    }
}
