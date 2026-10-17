package com.kex.vikrsaathi.ui.item

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.databinding.DialogItemFormBinding
import com.kex.vikrsaathi.databinding.DialogStockAdjustBinding
import com.kex.vikrsaathi.databinding.FragmentItemsBinding
import com.kex.vikrsaathi.ui.common.applyListViewMode
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kex.vikrsaathi.ui.common.installInventoryOptionsDrawer
import com.kex.vikrsaathi.util.FileShareHelper
import com.kex.vikrsaathi.util.ItemCatalogExcelExporter
import kotlinx.coroutines.launch
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.ListViewMode
import com.kex.vikrsaathi.util.ListViewPreferences
import com.kex.vikrsaathi.util.ListViewScreen
import com.kex.vikrsaathi.util.ViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ItemFragment : Fragment() {

    private var _binding: FragmentItemsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ItemViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var adapter: ItemAdapter
    private lateinit var listViewPrefs: ListViewPreferences
    private var viewMode: ListViewMode = ListViewMode.COMFORTABLE

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
        listViewPrefs = app.listViewPreferences
        viewMode = listViewPrefs.getMode(ListViewScreen.ITEMS)

        installHelpMenu(HelpScreen.ITEMS)
        installInventoryOptionsDrawer(
            drawerLayout = binding.drawerLayout,
            drawerRoot = binding.optionsDrawer.root,
            optionsTitleView = binding.optionsDrawer.textOptionsTitle,
            titleRes = R.string.items_options,
            radioViewMode = binding.optionsDrawer.radioViewMode,
            currentMode = { viewMode },
            onModeSelected = ::applyViewMode,
            onImportClick = {
                findNavController().navigate(R.id.action_item_to_inventory_import)
            },
            onExportClick = ::exportInventory
        )

        adapter = ItemAdapter(
            currencySymbol = app.settingsRepository.currencySymbol,
            lowStockThreshold = viewModel.lowStockThreshold,
            onEdit = { showItemDialog(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.recyclerItems.adapter = adapter
        applyViewMode(viewMode, persist = false)

        viewModel.items.observe(viewLifecycleOwner) { adapter.submitList(it) }
        binding.fabAddItem.setOnClickListener { showItemDialog(null) }
    }

    private fun applyViewMode(mode: ListViewMode, persist: Boolean = true) {
        viewMode = mode
        if (persist) listViewPrefs.setMode(ListViewScreen.ITEMS, mode)
        adapter.viewMode = mode
        binding.recyclerItems.applyListViewMode(mode)
        binding.detailsHeader.root.isVisible = mode == ListViewMode.DETAILS
    }

    private fun showItemDialog(existing: Item?) {
        val app = requireActivity().application as VikrSaathiApp
        if (!app.settingsRepository.vendorManagementEnabled) {
            presentItemDialog(existing, vendors = null)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val vendors = app.vendorRepository.getActive()
            if (!isAdded) return@launch
            presentItemDialog(existing, vendors)
        }
    }

    private fun presentItemDialog(existing: Item?, vendors: List<com.kex.vikrsaathi.data.entity.Vendor>?) {
        val formBinding = DialogItemFormBinding.inflate(layoutInflater)
        var selectedVendorId = existing?.preferredVendorId
        if (vendors != null) {
            formBinding.layoutPreferredVendor.isVisible = true
            val none = getString(R.string.preferred_vendor_none)
            val labels = listOf(none) + vendors.map { it.name }
            formBinding.editPreferredVendor.setAdapter(
                android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
            )
            val current = vendors.indexOfFirst { it.id == existing?.preferredVendorId }
            formBinding.editPreferredVendor.setText(if (current >= 0) vendors[current].name else none, false)
            formBinding.editPreferredVendor.setOnItemClickListener { _, _, position, _ ->
                selectedVendorId = if (position == 0) null else vendors[position - 1].id
            }
        }
        existing?.let {
            formBinding.editItemName.setText(it.name)
            formBinding.editItemBarcode.setText(it.barcode)
            formBinding.editItemMrp.setText(it.mrp.toString())
            formBinding.editItemDiscount.setText(it.discount.toString())
            formBinding.editItemSellingPrice.setText(it.sellingPrice?.toString().orEmpty())
            formBinding.editItemUnit.setText(it.baseUnit.ifBlank { it.remarks })
            formBinding.editPurchaseUnit.setText(it.purchaseUnit)
            if (it.purchaseUnit.isNotBlank() && it.unitsPerPurchase > 0) {
                formBinding.editUnitsPerPurchase.setText(it.unitsPerPurchase.toString())
            }
            formBinding.layoutOpeningStock.visibility = View.GONE
            formBinding.textCurrentStock.visibility = View.VISIBLE
            formBinding.textCurrentStock.text = getString(R.string.current_stock, it.stockQty)
            formBinding.buttonAdjustStock.visibility = View.VISIBLE
            formBinding.buttonAdjustStock.setOnClickListener {
                showAdjustStockDialog(existing)
            }
        } ?: run {
            formBinding.editItemDiscount.setText(viewModel.defaultDiscount.toString())
            formBinding.layoutOpeningStock.visibility = View.VISIBLE
            formBinding.textCurrentStock.visibility = View.GONE
            formBinding.buttonAdjustStock.visibility = View.GONE
        }

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
                val openingStock = formBinding.editOpeningStock.text.toString().toIntOrNull() ?: 0
                val unit = formBinding.editItemUnit.text.toString().trim()
                val purchaseUnit = formBinding.editPurchaseUnit.text.toString().trim()
                val factorText = formBinding.editUnitsPerPurchase.text.toString().trim()
                val parsedFactor = factorText.toIntOrNull()
                val unitsPerPurchase = when {
                    purchaseUnit.isBlank() && (factorText.isEmpty() || parsedFactor == 1) -> 1
                    purchaseUnit.isBlank() || factorText.isEmpty() -> 0
                    else -> parsedFactor ?: 0
                }
                val packError = com.kex.vikrsaathi.domain.finance.PackMath.validateItemPack(
                    purchaseUnit,
                    unitsPerPurchase
                )
                if (packError != null) {
                    Toast.makeText(
                        requireContext(),
                        com.kex.vikrsaathi.ui.finance.FinanceUi.message(
                            requireContext(),
                            com.kex.vikrsaathi.domain.finance.FinanceException(packError)
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                val item = Item(
                    id = existing?.id ?: 0,
                    name = name,
                    barcode = formBinding.editItemBarcode.text.toString().trim().ifEmpty { null },
                    mrp = mrp,
                    discount = formBinding.editItemDiscount.text.toString().toDoubleOrNull() ?: 0.0,
                    sellingPrice = formBinding.editItemSellingPrice.text.toString().toDoubleOrNull(),
                    remarks = unit,
                    stockQty = existing?.stockQty ?: 0,
                    preferredVendorId = if (vendors == null) existing?.preferredVendorId else selectedVendorId,
                    baseUnit = unit,
                    purchaseUnit = purchaseUnit,
                    unitsPerPurchase = unitsPerPurchase
                )
                viewModel.saveItem(item, openingStock = if (existing == null) openingStock else 0) { result ->
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

    private fun showAdjustStockDialog(item: Item) {
        val formBinding = DialogStockAdjustBinding.inflate(layoutInflater)
        formBinding.textAdjustCurrentStock.text = getString(R.string.current_stock, item.stockQty)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.adjust_stock)
            .setView(formBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val delta = formBinding.editStockDelta.text.toString().toIntOrNull() ?: 0
                if (delta == 0) return@setPositiveButton
                val note = formBinding.editStockNote.text.toString().trim()
                viewModel.adjustStock(item.id, delta, note.ifEmpty { null }) { result ->
                    result.onSuccess {
                        Toast.makeText(requireContext(), R.string.stock_adjusted, Toast.LENGTH_SHORT).show()
                    }.onFailure {
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

    private fun exportInventory() {
        lifecycleScope.launch {
            val items = (requireActivity().application as VikrSaathiApp).itemRepository.getAllSync()
            if (items.isEmpty()) {
                Toast.makeText(requireContext(), R.string.inventory_export_no_data, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val file = ItemCatalogExcelExporter.exportItems(requireContext(), items)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.inventory_export)
                .setMessage(getString(R.string.inventory_export_success))
                .setPositiveButton(R.string.open_file) { _, _ ->
                    try {
                        FileShareHelper.openFile(
                            requireContext(),
                            file,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), file.absolutePath, Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(R.string.share_file) { _, _ ->
                    FileShareHelper.shareFile(
                        requireContext(),
                        file,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        getString(R.string.inventory_export)
                    )
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
