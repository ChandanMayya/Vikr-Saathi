package com.kex.vikrsaathi.ui.settings.invoicebuilder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kex.vikrsaathi.data.model.template.TableColumn
import com.kex.vikrsaathi.data.model.template.TemplateElement
import com.kex.vikrsaathi.data.model.template.TemplateJsonCodec
import com.kex.vikrsaathi.databinding.BottomSheetTableColumnsBinding
import com.kex.vikrsaathi.databinding.ItemTableColumnEditorBinding

class TableColumnEditorBottomSheet : BottomSheetDialogFragment() {

    interface Callback {
        fun onApply(elementId: String, columns: List<TableColumn>)
    }

    private var _binding: BottomSheetTableColumnsBinding? = null
    private val binding get() = _binding!!

    private var element: TemplateElement? = null
    var callback: Callback? = null

    private val columns = mutableListOf<TableColumn>()
    private lateinit var adapter: ColumnAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTableColumnsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val current = element ?: return
        val columnsJson = current.content["columns"].orEmpty()
        columns.clear()
        columns.addAll(TemplateJsonCodec.tableColumnsFromJson(columnsJson))

        adapter = ColumnAdapter(columns) { index ->
            columns.removeAt(index)
            adapter.notifyDataSetChanged()
        }
        binding.recyclerColumns.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerColumns.adapter = adapter

        binding.buttonAddColumn.setOnClickListener {
            columns.add(TableColumn("field", "Label", 20f))
            adapter.notifyItemInserted(columns.lastIndex)
        }

        binding.buttonApplyColumns.setOnClickListener {
            val parsed = adapter.readColumns()
            callback?.onApply(current.id, parsed)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class ColumnAdapter(
        private val items: MutableList<TableColumn>,
        private val onRemove: (Int) -> Unit
    ) : RecyclerView.Adapter<ColumnAdapter.ViewHolder>() {

        inner class ViewHolder(val itemBinding: ItemTableColumnEditorBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemTableColumnEditorBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val column = items[position]
            holder.itemBinding.editColumnKey.setText(column.key)
            holder.itemBinding.editColumnLabel.setText(column.label)
            holder.itemBinding.editColumnWidth.setText(column.widthPercent.toString())
            holder.itemBinding.buttonRemoveColumn.setOnClickListener {
                onRemove(holder.bindingAdapterPosition)
            }
        }

        override fun getItemCount(): Int = items.size

        fun readColumns(): List<TableColumn> {
            val result = mutableListOf<TableColumn>()
            for (i in 0 until itemCount) {
                val holder = binding.recyclerColumns.findViewHolderForAdapterPosition(i)
                    as? ViewHolder ?: continue
                val key = holder.itemBinding.editColumnKey.text.toString().trim()
                val label = holder.itemBinding.editColumnLabel.text.toString().trim()
                val width = holder.itemBinding.editColumnWidth.text.toString().toFloatOrNull() ?: 20f
                if (key.isNotEmpty()) {
                    result.add(TableColumn(key, label.ifEmpty { key }, width))
                }
            }
            return result.ifEmpty { items.toList() }
        }
    }

    companion object {
        fun newInstance(element: TemplateElement): TableColumnEditorBottomSheet {
            return TableColumnEditorBottomSheet().apply {
                this.element = element
            }
        }
    }
}
