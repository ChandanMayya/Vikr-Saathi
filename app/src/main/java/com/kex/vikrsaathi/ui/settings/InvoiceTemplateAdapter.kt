package com.kex.vikrsaathi.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.databinding.ItemInvoiceTemplateBinding

class InvoiceTemplateAdapter(
    private val onSetDefault: (InvoiceTemplate) -> Unit,
    private val onEdit: (InvoiceTemplate) -> Unit,
    private val onDelete: (InvoiceTemplate) -> Unit
) : ListAdapter<InvoiceTemplate, InvoiceTemplateAdapter.ViewHolder>(DiffCallback()) {

    var canDeleteTemplates: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInvoiceTemplateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemInvoiceTemplateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(template: InvoiceTemplate) {
            binding.textTemplateName.text = template.name
            binding.textTemplateMeta.text = binding.root.context.getString(
                com.kex.vikrsaathi.R.string.template_element_count,
                template.elements.size
            )
            binding.radioDefault.isChecked = template.isDefault
            binding.radioDefault.setOnClickListener { onSetDefault(template) }
            binding.buttonEditTemplate.setOnClickListener { onEdit(template) }
            binding.buttonDeleteTemplate.isVisible = canDeleteTemplates && !template.isDefault
            binding.buttonDeleteTemplate.setOnClickListener { onDelete(template) }
            binding.root.setOnClickListener { onEdit(template) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<InvoiceTemplate>() {
        override fun areItemsTheSame(oldItem: InvoiceTemplate, newItem: InvoiceTemplate) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: InvoiceTemplate, newItem: InvoiceTemplate) =
            oldItem == newItem
    }
}
