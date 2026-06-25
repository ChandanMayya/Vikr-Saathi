package com.kex.vikrsaathi.ui.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.databinding.ItemCustomerBinding

class CustomerAdapter(
    private val onEdit: (Customer) -> Unit,
    private val onDelete: (Customer) -> Unit
) : ListAdapter<Customer, CustomerAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCustomerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCustomerBinding) :
        RecyclerView.ViewHolder(binding.root) {

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

    private class DiffCallback : DiffUtil.ItemCallback<Customer>() {
        override fun areItemsTheSame(oldItem: Customer, newItem: Customer) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Customer, newItem: Customer) = oldItem == newItem
    }
}
