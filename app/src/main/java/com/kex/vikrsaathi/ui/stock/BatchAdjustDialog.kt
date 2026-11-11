package com.kex.vikrsaathi.ui.stock

import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.entity.InventoryBatch
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.databinding.DialogStockAdjustBinding
import com.kex.vikrsaathi.domain.inventory.CivilDate

data class BatchAdjustRequest(
    val delta: Int,
    val note: String?,
    val batchId: Long?,
    val lotNumber: String?,
    val expiryEpochDay: Long?,
    val expiryUnknown: Boolean
)

fun showBatchAdjustDialog(
    fragment: androidx.fragment.app.Fragment,
    item: Item,
    batches: List<InventoryBatch>,
    prefillDelta: Int?,
    onSave: (BatchAdjustRequest) -> Unit
) {
    val context = fragment.requireContext()
    val binding = DialogStockAdjustBinding.inflate(fragment.layoutInflater)
    binding.textAdjustCurrentStock.text = context.getString(R.string.current_stock, item.stockQty)
    prefillDelta?.let { binding.editStockDelta.setText(it.toString()) }
    binding.groupBatch.visibility = if (item.trackBatches) View.VISIBLE else View.GONE
    val labels = batches.map { batch ->
        val expiry = batch.expiryEpochDay?.let { CivilDate.format(it) } ?: context.getString(R.string.expiry_unknown)
        val name = batch.lotNumber ?: batch.internalRef
        "$name · $expiry · ${batch.quantityOnHand} · ${batch.state}"
    }
    binding.spinnerBatch.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.adjust_stock)
        .setView(binding.root)
        .setPositiveButton(R.string.save, null)
        .setNegativeButton(R.string.cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val delta = binding.editStockDelta.text.toString().toIntOrNull() ?: 0
            if (delta == 0) {
                dialog.dismiss()
                return@setOnClickListener
            }
            val unknown = binding.checkExpiryUnknown.isChecked
            val expiry = if (unknown) null else CivilDate.parse(binding.editExpiry.text.toString())
            val chosen = batches.getOrNull(binding.spinnerBatch.selectedItemPosition)
            if (item.trackBatches && delta < 0 && chosen == null) {
                Toast.makeText(context, R.string.batch_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (item.trackBatches && delta > 0 && !unknown && expiry == null) {
                Toast.makeText(context, R.string.batch_expiry_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSave(
                BatchAdjustRequest(
                    delta = delta,
                    note = binding.editStockNote.text.toString().trim().ifEmpty { null },
                    batchId = if (delta < 0) chosen?.id else null,
                    lotNumber = binding.editLotNumber.text.toString().trim().ifEmpty { null },
                    expiryEpochDay = expiry,
                    expiryUnknown = unknown
                )
            )
            dialog.dismiss()
        }
    }
    dialog.show()
}
