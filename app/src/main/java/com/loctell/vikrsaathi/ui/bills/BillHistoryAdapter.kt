package com.loctell.vikrsaathi.ui.bills

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.loctell.vikrsaathi.data.model.BillWithDetails
import com.loctell.vikrsaathi.databinding.ItemBillHistoryBinding
import com.loctell.vikrsaathi.util.PriceCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BillHistoryAdapter(
    private val currencySymbol: String,
    private val onOpen: (BillWithDetails) -> Unit,
    private val onDuplicate: (BillWithDetails) -> Unit,
    private val onDelete: (BillWithDetails) -> Unit
) : ListAdapter<BillWithDetails, BillHistoryAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBillHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBillHistoryBinding) :
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
}
