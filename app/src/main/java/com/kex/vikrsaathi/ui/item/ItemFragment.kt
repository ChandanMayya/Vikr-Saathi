package com.kex.vikrsaathi.ui.item

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.databinding.DialogItemFormBinding
import com.kex.vikrsaathi.databinding.FragmentItemsBinding
import com.kex.vikrsaathi.util.ViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ItemFragment : Fragment() {

    private var _binding: FragmentItemsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ItemViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var adapter: ItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as VikrSaathiApp
        adapter = ItemAdapter(
            currencySymbol = app.settingsRepository.currencySymbol,
            onEdit = { showItemDialog(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.recyclerItems.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerItems.adapter = adapter

        viewModel.items.observe(viewLifecycleOwner) { adapter.submitList(it) }
        binding.fabAddItem.setOnClickListener { showItemDialog(null) }
    }

    private fun showItemDialog(existing: Item?) {
        val formBinding = DialogItemFormBinding.inflate(layoutInflater)
        existing?.let {
            formBinding.editItemName.setText(it.name)
            formBinding.editItemBarcode.setText(it.barcode)
            formBinding.editItemMrp.setText(it.mrp.toString())
            formBinding.editItemDiscount.setText(it.discount.toString())
            formBinding.editItemSellingPrice.setText(it.sellingPrice?.toString().orEmpty())
            formBinding.editItemUnit.setText(it.remarks)
        } ?: formBinding.editItemDiscount.setText(viewModel.defaultDiscount.toString())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.add_item else R.string.edit_item)
            .setView(formBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = formBinding.editItemName.text.toString().trim()
                val mrp = formBinding.editItemMrp.text.toString().toDoubleOrNull()
                if (name.isEmpty() || mrp == null) {
                    Toast.makeText(requireContext(), R.string.invalid_item, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val item = Item(
                    id = existing?.id ?: 0,
                    name = name,
                    barcode = formBinding.editItemBarcode.text.toString().trim().ifEmpty { null },
                    mrp = mrp,
                    discount = formBinding.editItemDiscount.text.toString().toDoubleOrNull() ?: 0.0,
                    sellingPrice = formBinding.editItemSellingPrice.text.toString().toDoubleOrNull(),
                    remarks = formBinding.editItemUnit.text.toString().trim()
                )
                viewModel.saveItem(item) { result ->
                    result.onFailure {
                        Toast.makeText(
                            requireContext(),
                            it.message ?: getString(R.string.save_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(item: Item) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_item)
            .setMessage(R.string.delete_item_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteItem(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
