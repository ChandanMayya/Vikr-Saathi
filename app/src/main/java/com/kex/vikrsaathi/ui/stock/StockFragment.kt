package com.kex.vikrsaathi.ui.stock

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.databinding.FragmentStockBinding
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

class StockFragment : Fragment() {

    private var _binding: FragmentStockBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StockViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private val app get() = requireActivity().application as VikrSaathiApp
    private lateinit var adapter: StockAdapter
    private var pendingFocusId: Long = 0L
    private lateinit var listViewPrefs: ListViewPreferences
    private var viewMode: ListViewMode = ListViewMode.COMFORTABLE

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as VikrSaathiApp
        listViewPrefs = app.listViewPreferences
        viewMode = listViewPrefs.getMode(ListViewScreen.INVENTORY)

        installHelpMenu(HelpScreen.STOCK)
        installInventoryOptionsDrawer(
            drawerLayout = binding.drawerLayout,
            drawerRoot = binding.optionsDrawer.root,
            optionsTitleView = binding.optionsDrawer.textOptionsTitle,
            titleRes = R.string.inventory_options,
            radioViewMode = binding.optionsDrawer.radioViewMode,
            currentMode = { viewMode },
            onModeSelected = ::applyViewMode,
            onImportClick = {
                findNavController().navigate(R.id.action_stock_to_inventory_import)
            },
            onExportClick = ::exportInventory
        )

        adapter = StockAdapter(
            currencySymbol = app.settingsRepository.currencySymbol,
            lowStockThreshold = viewModel.lowStockThreshold,
            onAdjust = { showAdjustDialog(it) },
            onStockIn = { showAdjustDialog(it, prefillDelta = 1) },
            onOpenMovements = { openHistory(it) }
        )
        binding.recyclerStock.adapter = adapter
        applyViewMode(viewMode, persist = false)

        viewModel.items.observe(viewLifecycleOwner) { viewModel.onItemsChanged(it) }
        val batchFilter = arguments?.getString("batchFilter").orEmpty()
        if (batchFilter.isNotBlank()) {
            val label = when (batchFilter) {
                "expired" -> getString(R.string.batch_filter_expired)
                "soon" -> getString(R.string.batch_filter_soon)
                else -> getString(R.string.batch_filter_unknown)
            }
            binding.textBatchFilter.isVisible = true
            binding.textBatchFilter.text = label
            viewLifecycleOwner.lifecycleScope.launch {
                val ids = app.batchRepository.itemIdsForFilter(batchFilter)
                viewModel.setAllowedItemIds(ids)
                if (ids.isEmpty()) binding.textBatchFilter.text = "$label\n${getString(R.string.batch_filter_empty)}"
            }
        }
        pendingFocusId = arguments?.getLong("focusItemId") ?: 0L
        val focusName = arguments?.getString("focusItemName").orEmpty()
        if (focusName.isNotBlank()) {
            binding.editStockSearch.setText(focusName)
            viewModel.setQuery(focusName)
        }
        viewModel.filteredItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            if (pendingFocusId > 0L) {
                val match = items.firstOrNull { it.id == pendingFocusId }
                if (match != null) {
                    pendingFocusId = 0L
                    openHistory(match)
                }
            }
        }
        viewModel.vendorNames.observe(viewLifecycleOwner) { adapter.vendorNames = it }

        binding.editStockSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.switchLowStockOnly.setOnCheckedChangeListener { _, checked ->
            viewModel.setLowStockOnly(checked)
        }
    }

    private fun applyViewMode(mode: ListViewMode, persist: Boolean = true) {
        viewMode = mode
        if (persist) listViewPrefs.setMode(ListViewScreen.INVENTORY, mode)
        adapter.viewMode = mode
        val recycler = binding.recyclerStock
        val first = (recycler.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
        recycler.layoutManager = LinearLayoutManager(requireContext())
        if (first > 0) recycler.scrollToPosition(first)
        binding.detailsHeader.root.isVisible = false
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshVendorNames()
    }

    private fun showAdjustDialog(item: Item, prefillDelta: Int? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            val batches = if (item.trackBatches && app.settingsRepository.batchTrackingEnabled) {
                app.batchRepository.batchesFor(item.id).filter { it.quantityOnHand > 0 }
            } else {
                emptyList()
            }
            if (_binding == null) return@launch
            showBatchAdjustDialog(this@StockFragment, item, batches, prefillDelta) { request ->
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

    private fun openHistory(item: Item) {
        findNavController().navigate(
            R.id.action_stock_to_stock_history,
            StockHistoryFragment.args(item.id)
        )
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
