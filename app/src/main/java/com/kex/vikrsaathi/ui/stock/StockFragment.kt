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
import com.kex.vikrsaathi.data.entity.StockMovement
import com.kex.vikrsaathi.data.entity.StockMovementType
import com.kex.vikrsaathi.databinding.DialogStockAdjustBinding
import com.kex.vikrsaathi.databinding.FragmentStockBinding
import com.kex.vikrsaathi.databinding.ItemStockMovementBinding
import com.kex.vikrsaathi.ui.common.applyListViewMode
import com.kex.vikrsaathi.ui.common.installListViewOptionsDrawer
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.ListViewMode
import com.kex.vikrsaathi.util.ListViewPreferences
import com.kex.vikrsaathi.util.ListViewScreen
import com.kex.vikrsaathi.util.ViewModelFactory
import java.text.DateFormat
import java.util.Date

class StockFragment : Fragment() {

    private var _binding: FragmentStockBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StockViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private lateinit var adapter: StockAdapter
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
        installListViewOptionsDrawer(
            drawerLayout = binding.drawerLayout,
            optionsTitleView = binding.optionsDrawer.textOptionsTitle,
            titleRes = R.string.inventory_options,
            radioViewMode = binding.optionsDrawer.radioViewMode,
            currentMode = { viewMode },
            onModeSelected = ::applyViewMode
        )

        adapter = StockAdapter(
            lowStockThreshold = viewModel.lowStockThreshold,
            onAdjust = { showAdjustDialog(it) },
            onStockIn = { showAdjustDialog(it, prefillDelta = 1) },
            onOpenMovements = { showMovementsDialog(it) }
        )
        binding.recyclerStock.adapter = adapter
        applyViewMode(viewMode, persist = false)

        viewModel.items.observe(viewLifecycleOwner) { viewModel.onItemsChanged(it) }
        viewModel.filteredItems.observe(viewLifecycleOwner) { adapter.submitList(it) }

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
        binding.recyclerStock.applyListViewMode(mode)
        binding.detailsHeader.root.isVisible = mode == ListViewMode.DETAILS
    }

    private fun showAdjustDialog(item: Item, prefillDelta: Int? = null) {
        val formBinding = DialogStockAdjustBinding.inflate(layoutInflater)
        formBinding.textAdjustCurrentStock.text = getString(R.string.current_stock, item.stockQty)
        prefillDelta?.let { formBinding.editStockDelta.setText(it.toString()) }
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

    private fun showMovementsDialog(item: Item) {
        viewModel.loadMovements(item.id)
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.stock_movements) + " — " + item.name)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .show()

        viewModel.movements.observe(viewLifecycleOwner) { movements ->
            if (!dialog.isShowing) return@observe
            container.removeAllViews()
            if (movements.isEmpty()) {
                val empty = android.widget.TextView(requireContext()).apply {
                    text = getString(R.string.stock_movement_empty)
                }
                container.addView(empty)
                return@observe
            }
            movements.forEach { movement ->
                val row = ItemStockMovementBinding.inflate(layoutInflater, container, false)
                bindMovement(row, movement)
                container.addView(row.root)
            }
        }
    }

    private fun bindMovement(binding: ItemStockMovementBinding, movement: StockMovement) {
        binding.textMovementType.text = movementTypeLabel(movement.type)
        binding.textMovementDelta.text = getString(
            R.string.movement_delta_format,
            movement.delta,
            movement.quantityAfter
        )
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(movement.createdAt))
        val note = movement.note?.takeIf { it.isNotBlank() }
        binding.textMovementMeta.text = listOfNotNull(date, note).joinToString(" • ")
    }

    private fun movementTypeLabel(type: String): String = when (type) {
        StockMovementType.SALE.name -> getString(R.string.movement_type_sale)
        StockMovementType.SALE_REVERSAL.name -> getString(R.string.movement_type_sale_reversal)
        StockMovementType.ADJUSTMENT.name -> getString(R.string.movement_type_adjustment)
        StockMovementType.OPENING.name -> getString(R.string.movement_type_opening)
        StockMovementType.IMPORT.name -> getString(R.string.movement_type_import)
        else -> type
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
