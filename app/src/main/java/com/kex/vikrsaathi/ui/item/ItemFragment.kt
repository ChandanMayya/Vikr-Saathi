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
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.domain.inventory.BatchState
import com.kex.vikrsaathi.domain.inventory.CivilDate
import com.kex.vikrsaathi.databinding.DialogItemFormBinding
import com.kex.vikrsaathi.ui.stock.showBatchAdjustDialog
import com.kex.vikrsaathi.databinding.FragmentItemsBinding
import androidx.recyclerview.widget.LinearLayoutManager
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
            onDelete = { confirmDelete(it) },
            onOpenBatches = { showStockedBatches(it) }
        )
        binding.recyclerItems.adapter = adapter
        applyViewMode(viewMode, persist = false)

        viewModel.items.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            refreshExpiryFlags()
        }
        binding.fabAddItem.setOnClickListener { showItemDialog(null) }
    }

    override fun onResume() {
        super.onResume()
        refreshExpiryFlags()
        val app = requireActivity().application as VikrSaathiApp
        if (!app.settingsRepository.vendorManagementEnabled) {
            adapter.vendorNames = emptyMap()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) return@launch
            adapter.vendorNames = app.vendorRepository.getAll().associate { it.id to it.name }
        }
    }

    private fun refreshExpiryFlags() {
        val app = requireActivity().application as VikrSaathiApp
        viewLifecycleOwner.lifecycleScope.launch {
            val enabled = app.settingsRepository.batchTrackingEnabled
            val alerts = if (enabled) app.batchRepository.alerts() else emptyList()
            if (!isAdded) return@launch
            adapter.batchTrackingEnabled = enabled
            adapter.expiringByItem = alerts.groupBy { it.itemId }
        }
    }

    private fun showStockedBatches(item: Item) {
        val app = requireActivity().application as VikrSaathiApp
        if (!app.settingsRepository.batchTrackingEnabled || !item.trackBatches) return
        viewLifecycleOwner.lifecycleScope.launch {
            val lots = app.batchRepository.batchesFor(item.id).filter { it.quantityOnHand > 0 }
            if (!isAdded) return@launch
            val today = CivilDate.today()
            val window = app.settingsRepository.expiryAlertDays
            val message = if (lots.isEmpty()) {
                getString(R.string.item_batches_none)
            } else {
                lots.joinToString("\n") { batch ->
                    val name = batch.lotNumber?.trim()?.takeIf { it.isNotEmpty() } ?: batch.internalRef
                    val expiry = batch.expiryEpochDay?.let { CivilDate.format(it) }
                        ?: getString(R.string.expiry_unknown)
                    val expired = batch.state == BatchState.EXPIRED ||
                        (batch.expiryEpochDay != null && batch.expiryEpochDay < today)
                    val soon = !expired && batch.expiryEpochDay != null &&
                        batch.state != BatchState.QUARANTINE &&
                        CivilDate.inAlertWindow(batch.expiryEpochDay, today, window)
                    val line = when {
                        expired -> R.string.item_batch_stock_expired
                        soon -> R.string.item_batch_stock_soon
                        else -> R.string.item_batch_stock_line
                    }
                    getString(line, name, expiry, batch.quantityOnHand)
                }
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.name)
                .setMessage(getString(R.string.item_batches_title) + "\n\n" + message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun applyViewMode(mode: ListViewMode, persist: Boolean = true) {
        viewMode = mode
        if (persist) listViewPrefs.setMode(ListViewScreen.ITEMS, mode)
        adapter.viewMode = mode
        val recycler = binding.recyclerItems
        val first = (recycler.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
        recycler.layoutManager = LinearLayoutManager(requireContext())
        if (first > 0) recycler.scrollToPosition(first)
        binding.detailsHeader.root.isVisible = false
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
            formBinding.checkTrackBatches.isChecked = it.trackBatches
            formBinding.checkTrackBatches.setOnCheckedChangeListener { _, checked ->
                if (checked && !it.trackBatches && it.stockQty > 0) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(getString(R.string.track_batches_confirm, it.stockQty))
                        .setNegativeButton(R.string.cancel) { _, _ ->
                            formBinding.checkTrackBatches.isChecked = false
                        }
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        } ?: run {
            formBinding.editItemDiscount.setText(viewModel.defaultDiscount.toString())
            formBinding.layoutOpeningStock.visibility = View.VISIBLE
            formBinding.textCurrentStock.visibility = View.GONE
            formBinding.buttonAdjustStock.visibility = View.GONE
        }
        bindWarrantyForm(formBinding, existing)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.add_item else R.string.edit_item)
            .setView(formBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = formBinding.editItemName.text.toString().trim()
                val mrp = formBinding.editItemMrp.text.toString().toDoubleOrNull()
                if (name.isEmpty() || mrp == null) {
                    Toast.makeText(requireContext(), R.string.invalid_item, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setOnClickListener
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
                    return@setOnClickListener
                }
                val warranty = readWarranty(formBinding, existing) ?: return@setOnClickListener
                val item = Item(
                    id = existing?.id ?: 0,
                    name = name,
                    barcode = formBinding.editItemBarcode.text.toString().trim().ifEmpty { null },
                    mrp = mrp,
                    discount = formBinding.editItemDiscount.text.toString().toDoubleOrNull() ?: 0.0,
                    sellingPrice = formBinding.editItemSellingPrice.text.toString().toDoubleOrNull(),
                    remarks = unit,
                    stockQty = existing?.stockQty ?: 0,
                    trackBatches = formBinding.checkTrackBatches.isChecked,
                    preferredVendorId = if (vendors == null) existing?.preferredVendorId else selectedVendorId,
                    baseUnit = unit,
                    purchaseUnit = purchaseUnit,
                    unitsPerPurchase = unitsPerPurchase,
                    warrantyApplies = warranty.applies,
                    warrantyPeriod = warranty.period,
                    warrantyUnit = warranty.unit,
                    warrantyProvider = warranty.provider,
                    warrantyProviderName = warranty.providerName,
                    warrantyTerms = warranty.terms
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
                dialog.dismiss()
        }
    }

    private data class ItemWarrantyInput(
        val applies: Boolean,
        val period: Int,
        val unit: String,
        val provider: String,
        val providerName: String,
        val terms: String
    )

    private fun bindWarrantyForm(formBinding: DialogItemFormBinding, existing: Item?) {
        val enabled = (requireActivity().application as VikrSaathiApp).settingsRepository.warrantyEnabled
        formBinding.sectionWarranty.isVisible = enabled
        if (!enabled) return
        val units = listOf(
            WarrantyPolicy.DAYS to getString(R.string.warranty_unit_days),
            WarrantyPolicy.MONTHS to getString(R.string.warranty_unit_months),
            WarrantyPolicy.YEARS to getString(R.string.warranty_unit_years)
        )
        val providers = listOf(
            WarrantyPolicy.MANUFACTURER to getString(R.string.warranty_provider_manufacturer),
            WarrantyPolicy.SHOP to getString(R.string.warranty_provider_shop),
            WarrantyPolicy.OTHER to getString(R.string.warranty_provider_other)
        )
        formBinding.editWarrantyUnit.setAdapter(
            android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, units.map { it.second })
        )
        formBinding.editWarrantyProvider.setAdapter(
            android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, providers.map { it.second })
        )
        val unitLabel = units.firstOrNull { it.first == existing?.warrantyUnit }?.second
            ?: getString(R.string.warranty_unit_months)
        val providerLabel = providers.firstOrNull { it.first == existing?.warrantyProvider }?.second
            ?: getString(R.string.warranty_provider_manufacturer)
        if ((existing?.warrantyPeriod ?: 0) > 0) {
            formBinding.editWarrantyPeriod.setText(existing?.warrantyPeriod.toString())
        }
        formBinding.editWarrantyUnit.setText(unitLabel, false)
        formBinding.editWarrantyProvider.setText(providerLabel, false)
        formBinding.editWarrantyProviderName.setText(existing?.warrantyProviderName.orEmpty())
        formBinding.editWarrantyTerms.setText(existing?.warrantyTerms.orEmpty())
        formBinding.switchItemWarranty.isChecked = existing?.warrantyApplies == true
        formBinding.layoutWarrantyDetails.isVisible = formBinding.switchItemWarranty.isChecked
        formBinding.switchItemWarranty.setOnCheckedChangeListener { _, checked ->
            formBinding.layoutWarrantyDetails.isVisible = checked
        }
    }

    private fun readWarranty(formBinding: DialogItemFormBinding, existing: Item?): ItemWarrantyInput? {
        val shopEnabled = (requireActivity().application as VikrSaathiApp).settingsRepository.warrantyEnabled
        if (!shopEnabled) {
            return ItemWarrantyInput(
                applies = existing?.warrantyApplies == true,
                period = existing?.warrantyPeriod ?: 0,
                unit = existing?.warrantyUnit.orEmpty(),
                provider = existing?.warrantyProvider.orEmpty(),
                providerName = existing?.warrantyProviderName.orEmpty(),
                terms = existing?.warrantyTerms.orEmpty()
            )
        }
        if (!formBinding.switchItemWarranty.isChecked) {
            return ItemWarrantyInput(
                applies = false,
                period = WarrantyPolicy.parsePeriod(formBinding.editWarrantyPeriod.text?.toString().orEmpty())
                    ?: existing?.warrantyPeriod ?: 0,
                unit = warrantyCode(
                    formBinding.editWarrantyUnit.text?.toString().orEmpty(),
                    existing?.warrantyUnit.orEmpty()
                ),
                provider = providerCode(
                    formBinding.editWarrantyProvider.text?.toString().orEmpty(),
                    existing?.warrantyProvider.orEmpty()
                ),
                providerName = formBinding.editWarrantyProviderName.text?.toString().orEmpty().trim(),
                terms = formBinding.editWarrantyTerms.text?.toString().orEmpty().trim()
            )
        }
        val period = WarrantyPolicy.parsePeriod(formBinding.editWarrantyPeriod.text?.toString().orEmpty())
        if (period == null) {
            formBinding.layoutWarrantyPeriod.error = getString(R.string.warranty_period_invalid)
            Toast.makeText(requireContext(), R.string.warranty_period_invalid, Toast.LENGTH_SHORT).show()
            return null
        }
        formBinding.layoutWarrantyPeriod.error = null
        return ItemWarrantyInput(
            applies = true,
            period = period,
            unit = warrantyCode(formBinding.editWarrantyUnit.text?.toString().orEmpty(), WarrantyPolicy.MONTHS),
            provider = providerCode(
                formBinding.editWarrantyProvider.text?.toString().orEmpty(),
                WarrantyPolicy.MANUFACTURER
            ),
            providerName = formBinding.editWarrantyProviderName.text?.toString().orEmpty().trim(),
            terms = formBinding.editWarrantyTerms.text?.toString().orEmpty().trim()
        )
    }

    private fun warrantyCode(label: String, fallback: String): String = when (label) {
        getString(R.string.warranty_unit_days) -> WarrantyPolicy.DAYS
        getString(R.string.warranty_unit_months) -> WarrantyPolicy.MONTHS
        getString(R.string.warranty_unit_years) -> WarrantyPolicy.YEARS
        else -> fallback.ifBlank { WarrantyPolicy.MONTHS }
    }

    private fun providerCode(label: String, fallback: String): String = when (label) {
        getString(R.string.warranty_provider_manufacturer) -> WarrantyPolicy.MANUFACTURER
        getString(R.string.warranty_provider_shop) -> WarrantyPolicy.SHOP
        getString(R.string.warranty_provider_other) -> WarrantyPolicy.OTHER
        else -> fallback.ifBlank { WarrantyPolicy.MANUFACTURER }
    }

    private fun showAdjustStockDialog(item: Item) {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as VikrSaathiApp
            val batches = if (item.trackBatches && app.settingsRepository.batchTrackingEnabled) {
                app.batchRepository.batchesFor(item.id).filter { it.quantityOnHand > 0 }
            } else {
                emptyList()
            }
            showBatchAdjustDialog(this@ItemFragment, item, batches, null) { request ->
                viewModel.adjustStock(item.id, request) { result ->
                    result.onSuccess {
                        Toast.makeText(requireContext(), R.string.stock_adjusted, Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(
                            requireContext(),
                            com.kex.vikrsaathi.ui.finance.FinanceUi.message(requireContext(), it),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
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
