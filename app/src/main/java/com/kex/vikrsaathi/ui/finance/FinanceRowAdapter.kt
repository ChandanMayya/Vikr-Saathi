package com.kex.vikrsaathi.ui.finance

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.databinding.ItemFinanceRowBinding

data class FinanceRow(
    val title: String,
    val subtitle: String,
    val id: Long = 0L,
    val subtitleColorRes: Int = 0
)

class FinanceRowAdapter(
    private val onClick: (FinanceRow) -> Unit = {}
) : RecyclerView.Adapter<FinanceRowAdapter.Holder>() {

    private val rows = mutableListOf<FinanceRow>()

    fun submit(items: List<FinanceRow>) {
        rows.clear()
        rows.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemFinanceRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.binding.textFinanceTitle.text = row.title
        holder.binding.textFinanceSubtitle.text = row.subtitle
        val color = if (row.subtitleColorRes != 0) row.subtitleColorRes else R.color.text_secondary_dark
        holder.binding.textFinanceSubtitle.setTextColor(holder.binding.root.context.getColor(color))
        holder.binding.root.setOnClickListener { onClick(row) }
    }

    override fun getItemCount(): Int = rows.size

    class Holder(val binding: ItemFinanceRowBinding) : RecyclerView.ViewHolder(binding.root)
}
