package com.kex.vikrsaathi.ui.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.databinding.ItemCustomerBinding
import com.kex.vikrsaathi.databinding.ItemCustomerCompactBinding
import com.kex.vikrsaathi.databinding.ItemCustomerDetailsBinding
import com.kex.vikrsaathi.util.ListViewMode

class CustomerAdapter(
    private val onEdit: (Customer) -> Unit,
    private val onDelete: (Customer) -> Unit
) : ListAdapter<Customer, RecyclerView.ViewHolder>(DiffCallback()) {

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
            VIEW_COMPACT -> CompactViewHolder(
                ItemCustomerCompactBinding.inflate(inflater, parent, false)
            )
            VIEW_DETAILS -> DetailsViewHolder(
                ItemCustomerDetailsBinding.inflate(inflater, parent, false)
            )
            else -> ComfortableViewHolder(
                ItemCustomerBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val customer = getItem(position)
        when (holder) {
            is ComfortableViewHolder -> holder.bind(customer)
            is CompactViewHolder -> holder.bind(customer)
            is DetailsViewHolder -> holder.bind(customer)
        }
    }

    inner class ComfortableViewHolder(
        private val binding: ItemCustomerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(customer: Customer) {
            binding.textCustomerBadge.text =
                customer.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            binding.textCustomerName.text = customer.name
            binding.textCustomerPhone.text = customer.phone.ifBlank { "-" }
            binding.textCustomerAddress.text = customer.formattedAddress().ifBlank { "-" }
            binding.buttonEditCustomer.setOnClickListener { onEdit(customer) }
            binding.buttonDeleteCustomer.setOnClickListener { onDelete(customer) }
        }
    }

    inner class CompactViewHolder(
        private val binding: ItemCustomerCompactBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(customer: Customer) {
            binding.textCustomerBadge.text =
                customer.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            binding.textCustomerName.text = customer.name
            binding.textCustomerPhone.text = customer.phone.ifBlank { "-" }
            binding.textCustomerAddress.text = customer.formattedAddress().ifBlank { "-" }
            binding.buttonEditCustomer.setOnClickListener { onEdit(customer) }
            binding.buttonDeleteCustomer.setOnClickListener { onDelete(customer) }
        }
    }

    inner class DetailsViewHolder(
        private val binding: ItemCustomerDetailsBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(customer: Customer) {
            binding.textCustomerName.text = customer.name
            binding.textCustomerPhone.text = customer.phone.ifBlank { "-" }
            binding.textCustomerAddress.text = customer.formattedAddress().ifBlank { "-" }
            binding.buttonEditCustomer.setOnClickListener { onEdit(customer) }
            binding.buttonDeleteCustomer.setOnClickListener { onDelete(customer) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Customer>() {
        override fun areItemsTheSame(oldItem: Customer, newItem: Customer) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Customer, newItem: Customer) = oldItem == newItem
    }

    companion object {
        private const val VIEW_COMFORTABLE = 0
        private const val VIEW_COMPACT = 1
        private const val VIEW_DETAILS = 2
    }
}
