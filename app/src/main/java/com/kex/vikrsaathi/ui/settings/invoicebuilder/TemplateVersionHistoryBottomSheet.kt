package com.kex.vikrsaathi.ui.settings.invoicebuilder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.model.template.InvoiceTemplateVersion
import com.kex.vikrsaathi.databinding.BottomSheetTemplateVersionsBinding
import com.kex.vikrsaathi.databinding.ItemTemplateVersionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TemplateVersionHistoryBottomSheet : BottomSheetDialogFragment() {

    interface Callback {
        fun onRestore(versionId: Long)
    }

    private var _binding: BottomSheetTemplateVersionsBinding? = null
    private val binding get() = _binding!!

    var callback: Callback? = null
    private var versions: List<InvoiceTemplateVersion> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTemplateVersionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerVersions.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerVersions.adapter = VersionAdapter(versions) { version ->
            confirmRestore(version)
        }
        binding.textNoVersions.isVisible = versions.isEmpty()
        binding.recyclerVersions.isVisible = versions.isNotEmpty()
    }

    fun submitVersions(list: List<InvoiceTemplateVersion>) {
        versions = list
        val currentBinding = _binding ?: return
        (currentBinding.recyclerVersions.adapter as? VersionAdapter)?.submit(list)
        currentBinding.textNoVersions.isVisible = list.isEmpty()
        currentBinding.recyclerVersions.isVisible = list.isNotEmpty()
    }

    private fun confirmRestore(version: InvoiceTemplateVersion) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore_version_title)
            .setMessage(getString(R.string.restore_version_message, version.versionNumber))
            .setPositiveButton(R.string.restore) { _, _ ->
                callback?.onRestore(version.id)
                dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class VersionAdapter(
        private var items: List<InvoiceTemplateVersion>,
        private val onClick: (InvoiceTemplateVersion) -> Unit
    ) : RecyclerView.Adapter<VersionAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        inner class ViewHolder(private val itemBinding: ItemTemplateVersionBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(version: InvoiceTemplateVersion) {
                itemBinding.textVersionNumber.text =
                    itemBinding.root.context.getString(R.string.version_number, version.versionNumber)
                itemBinding.textVersionMeta.text = itemBinding.root.context.getString(
                    R.string.version_meta,
                    dateFormat.format(Date(version.savedAt)),
                    version.elementCount
                )
                itemBinding.root.setOnClickListener { onClick(version) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemTemplateVersionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        fun submit(list: List<InvoiceTemplateVersion>) {
            items = list
            notifyDataSetChanged()
        }
    }

    companion object {
        fun newInstance(): TemplateVersionHistoryBottomSheet = TemplateVersionHistoryBottomSheet()
    }
}
