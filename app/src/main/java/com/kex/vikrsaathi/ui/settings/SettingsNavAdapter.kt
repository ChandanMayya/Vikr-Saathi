package com.kex.vikrsaathi.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.databinding.ItemSettingsNavRowBinding

data class SettingsNavEntry(
    @DrawableRes val iconRes: Int,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

class SettingsNavAdapter(
    private val items: List<SettingsNavEntry>
) : RecyclerView.Adapter<SettingsNavAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemSettingsNavRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SettingsNavEntry) {
            binding.imageIcon.setImageResource(item.iconRes)
            binding.textTitle.text = item.title
            binding.textSubtitle.text = item.subtitle
            binding.root.setOnClickListener { item.onClick() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSettingsNavRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
