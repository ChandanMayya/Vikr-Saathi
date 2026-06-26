package com.kex.vikrsaathi.ui.settings.reset

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.reset.ResetHistoryEntry
import com.kex.vikrsaathi.util.ViewModelFactory
import java.text.DateFormat
import java.util.Date

class ResetHistoryBottomSheet : BottomSheetDialogFragment() {

    interface Callback {
        fun onRestoreRequested(entry: ResetHistoryEntry)
    }

    var callback: Callback? = null

    private val viewModel: ResetViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    ) {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var adapter: ResetHistoryAdapter
    private var emptyView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_reset_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView = view.findViewById(R.id.textHistoryEmpty)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerResetHistory)
        adapter = ResetHistoryAdapter(
            onRestore = { entry ->
                callback?.onRestoreRequested(entry)
                dismiss()
            },
            categoryLabel = { key -> formatCategory(requireContext(), key) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewModel.historyEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            emptyView?.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.refreshHistory()
    }

    companion object {
        fun newInstance() = ResetHistoryBottomSheet()

        fun formatCategory(context: android.content.Context, key: String): String {
            return when (key) {
                "customers" -> context.getString(R.string.reset_category_customers)
                "items" -> context.getString(R.string.reset_category_items)
                "sales" -> context.getString(R.string.reset_category_sales)
                "templates" -> context.getString(R.string.reset_category_templates)
                "settings" -> context.getString(R.string.reset_category_settings)
                "invoice_config" -> context.getString(R.string.reset_category_invoice_config)
                else -> key
            }
        }

        fun formatCategories(context: android.content.Context, keys: List<String>): String =
            keys.joinToString(", ") { formatCategory(context, it) }

        fun formatDate(timestamp: Long): String =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
    }
}

private class ResetHistoryAdapter(
    private val onRestore: (ResetHistoryEntry) -> Unit,
    private val categoryLabel: (String) -> String
) : RecyclerView.Adapter<ResetHistoryAdapter.ViewHolder>() {

    private var entries: List<ResetHistoryEntry> = emptyList()

    fun submitList(list: List<ResetHistoryEntry>) {
        entries = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reset_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount(): Int = entries.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDate = itemView.findViewById<TextView>(R.id.textHistoryDate)
        private val textCategories = itemView.findViewById<TextView>(R.id.textHistoryCategories)
        private val buttonRestore = itemView.findViewById<MaterialButton>(R.id.buttonRestoreReset)

        fun bind(entry: ResetHistoryEntry) {
            textDate.text = ResetHistoryBottomSheet.formatDate(entry.performedAt)
            textCategories.text = entry.resetCategories.joinToString(", ") { categoryLabel(it) }
            buttonRestore.setOnClickListener { onRestore(entry) }
        }
    }
}
