package com.kex.vikrsaathi.ui.customer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
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
    private val onDelete: (Customer) -> Unit,
    private val onOpen: ((Customer) -> Unit)? = null
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

    private fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "?"
        val letters = if (parts.size == 1) parts[0].take(1) else parts[0].take(1) + parts[1].take(1)
        return letters.uppercase()
    }

    private fun showText(container: View, textView: TextView, value: String) {
        val shown = value.trim()
        container.isVisible = shown.isNotEmpty()
        if (shown.isNotEmpty()) textView.text = shown
    }

    private fun wireOpen(root: View, customer: Customer) {
        val open = onOpen
        root.isClickable = open != null
        root.isFocusable = open != null
        if (open != null) root.setOnClickListener { open(customer) } else root.setOnClickListener(null)
    }

    inner class ComfortableViewHolder(
        private val binding: ItemCustomerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(customer: Customer) {
            binding.textCustomerBadge.text = initials(customer.name)
            binding.textCustomerName.text = customer.name
            showText(binding.layoutCustomerPhone, binding.textCustomerPhone, customer.phone)
            showText(binding.layoutCustomerAddress, binding.textCustomerAddress, customer.formattedAddress())
            showText(binding.layoutCustomerRemarks, binding.textCustomerRemarks, customer.remarks)
            binding.buttonEditCustomer.setOnClickListener { onEdit(customer) }
            binding.buttonDeleteCustomer.setOnClickListener { onDelete(customer) }
            wireOpen(binding.root, customer)
        }
    }

    inner class CompactViewHolder(
        private val binding: ItemCustomerCompactBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(customer: Customer) {
            binding.textCustomerBadge.text = initials(customer.name)
            binding.textCustomerName.text = customer.name
            val phone = customer.phone.trim()
            binding.textCustomerPhone.isVisible = phone.isNotEmpty()
            if (phone.isNotEmpty()) binding.textCustomerPhone.text = phone
            val address = customer.formattedAddress()
            binding.textCustomerAddress.isVisible = address.isNotEmpty()
            if (address.isNotEmpty()) binding.textCustomerAddress.text = address
            binding.buttonEditCustomer.setOnClickListener { onEdit(customer) }
            binding.buttonDeleteCustomer.setOnClickListener { onDelete(customer) }
            wireOpen(binding.root, customer)
        }
    }

    inner class DetailsViewHolder(
        private val binding: ItemCustomerDetailsBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(customer: Customer) {
            binding.textCustomerBadge.text = initials(customer.name)
            binding.textCustomerName.text = customer.name
            showText(binding.layoutCustomerPhone, binding.textCustomerPhone, customer.phone)
            showText(binding.layoutCustomerAddress, binding.textCustomerAddress, customer.formattedAddress())
            showText(binding.layoutCustomerRemarks, binding.textCustomerRemarks, customer.remarks)
            binding.buttonEditCustomer.setOnClickListener { onEdit(customer) }
            binding.buttonDeleteCustomer.setOnClickListener { onDelete(customer) }
            wireOpen(binding.root, customer)
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
