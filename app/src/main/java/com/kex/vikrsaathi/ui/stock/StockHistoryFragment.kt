package com.kex.vikrsaathi.ui.stock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.data.entity.InventoryBatch
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.StockMovement
import com.kex.vikrsaathi.data.entity.StockMovementType
import com.kex.vikrsaathi.databinding.FragmentStockHistoryBinding
import com.kex.vikrsaathi.databinding.ItemStockHistoryBatchBinding
import com.kex.vikrsaathi.databinding.ItemStockHistoryMoveBinding
import com.kex.vikrsaathi.domain.inventory.BatchState
import com.kex.vikrsaathi.domain.inventory.CivilDate
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class StockHistoryFragment : Fragment() {

    private var _binding: FragmentStockHistoryBinding? = null
    private val binding get() = _binding!!

    private val app get() = requireActivity().application as VikrSaathiApp
    private var itemId: Long = 0L
    private var unit: String = ""
    private var movements: List<StockMovement> = emptyList()
    private var filter: HistoryFilter = HistoryFilter.ALL
    private val whenFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStockHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        itemId = arguments?.getLong(ARG_ITEM_ID) ?: 0L
        installHelpMenu(HelpScreen.STOCK_HISTORY)
        installFilterDrawer()
        if (itemId <= 0L) {
            findNavController().popBackStack()
            return
        }
        reload()
    }

    private fun installFilterDrawer() {
        val drawer = binding.optionsDrawer
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_list_screen_options, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId != R.id.action_list_options) return false
                binding.drawerLayout.openDrawer(GravityCompat.END)
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        drawer.radioHistoryFilter.setOnCheckedChangeListener { _, checkedId ->
            filter = when (checkedId) {
                R.id.radioHistoryIn -> HistoryFilter.IN
                R.id.radioHistoryOut -> HistoryFilter.OUT
                R.id.radioHistorySales -> HistoryFilter.SALES
                R.id.radioHistoryAdjust -> HistoryFilter.ADJUSTMENTS
                R.id.radioHistoryPurchase -> HistoryFilter.PURCHASES
                R.id.radioHistoryReturn -> HistoryFilter.RETURNS
                else -> HistoryFilter.ALL
            }
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            showMovements()
        }
    }

    private fun reload() {
        viewLifecycleOwner.lifecycleScope.launch {
            val item = app.itemRepository.getById(itemId)
            if (!isAdded) return@launch
            if (item == null) {
                findNavController().popBackStack()
                return@launch
            }
            unit = item.stockUnit().trim()
            movements = app.inventoryRepository.getMovementsForItem(itemId, 400)
            val tracking = app.settingsRepository.batchTrackingEnabled && item.trackBatches
            val batches = if (tracking) {
                app.batchRepository.batchesFor(item.id).filter { it.quantityOnHand > 0 }
            } else {
                emptyList()
            }
            if (_binding == null) return@launch
            bindSummary(item)
            bindBatches(tracking, batches, app.settingsRepository.expiryAlertDays)
            showMovements()
        }
    }

    private fun bindSummary(item: Item) {
        binding.textHistoryName.text = item.name
        binding.textHistoryStock.text = amount(item.stockQty)
    }

    private fun bindBatches(tracking: Boolean, batches: List<InventoryBatch>, alertDays: Int) {
        binding.layoutBatches.isVisible = tracking
        if (!tracking) return
        binding.textBatchesEmpty.isVisible = batches.isEmpty()
        binding.batchList.removeAllViews()
        val today = CivilDate.today()
        batches.forEach { batch ->
            val row = ItemStockHistoryBatchBinding.inflate(layoutInflater, binding.batchList, true)
            val name = batch.lotNumber?.trim()?.takeIf { it.isNotEmpty() } ?: batch.internalRef
            row.textBatchName.text = name
            val expiryDay = batch.expiryEpochDay
            val expired = batch.state == BatchState.EXPIRED || (expiryDay != null && expiryDay < today)
            val soon = !expired && expiryDay != null && batch.state != BatchState.QUARANTINE &&
                CivilDate.inAlertWindow(expiryDay, today, alertDays)
            row.textBatchExpiry.text = when {
                expired && expiryDay != null -> getString(R.string.stock_history_expired_on, CivilDate.format(expiryDay))
                expiryDay != null -> getString(R.string.stock_history_expires, CivilDate.format(expiryDay))
                else -> getString(R.string.stock_history_expiry_missing)
            }
            row.textBatchQty.text = getString(R.string.stock_history_left, amount(batch.quantityOnHand))
            val held = batch.state == BatchState.QUARANTINE
            val flag = when {
                held -> getString(R.string.stock_history_held)
                soon -> getString(R.string.stock_history_soon)
                else -> null
            }
            row.textBatchFlag.isVisible = flag != null
            row.textBatchFlag.text = flag.orEmpty()
            row.textBatchFlag.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (expired) R.color.balance_payable else R.color.orange_700
                )
            )
            row.buttonReleaseBatch.isVisible = held && batch.quantityOnHand > 0
            row.buttonReleaseBatch.setOnClickListener { release(batch.id) }
        }
    }

    private fun release(batchId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                app.batchRepository.releaseQuarantine(batchId, null, true, null)
            }
            if (!isAdded) return@launch
            result.onSuccess { reload() }
                .onFailure {
                    Toast.makeText(
                        requireContext(),
                        com.kex.vikrsaathi.ui.finance.FinanceUi.message(requireContext(), it),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun showMovements() {
        val shown = movements.filter { filter.matches(it) }
        binding.textHistoryFilter.text = getString(R.string.stock_history_showing, getString(filter.labelRes))
        binding.moveList.removeAllViews()
        binding.textMovesEmpty.isVisible = shown.isEmpty()
        binding.textMovesEmpty.setText(
            if (movements.isEmpty()) R.string.stock_history_empty else R.string.stock_history_filter_empty
        )
        shown.forEach { movement ->
            val row = ItemStockHistoryMoveBinding.inflate(layoutInflater, binding.moveList, true)
            row.textMoveTitle.setText(titleRes(movement.type))
            row.textMoveWhen.text = whenFormat.format(Date(movement.createdAt))
            row.textMoveAfter.text = getString(R.string.stock_history_after, amount(movement.quantityAfter))
            val note = meaningfulNote(movement.note)
            row.textMoveNote.isVisible = note != null
            row.textMoveNote.text = note.orEmpty()
            val added = movement.delta > 0
            row.textMoveDelta.text = if (added) "+${movement.delta}" else movement.delta.toString().replace('-', '−')
            row.textMoveDelta.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (added) R.color.text_accent else R.color.balance_payable
                )
            )
        }
    }

    private fun meaningfulNote(note: String?): String? {
        val text = note?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text in GENERIC_NOTES) return null
        return text
    }

    private fun amount(quantity: Int): String =
        if (unit.isEmpty()) quantity.toString() else "$quantity $unit"

    private fun titleRes(type: String): Int = when (type) {
        StockMovementType.SALE.name -> R.string.movement_type_sale
        StockMovementType.SALE_REVERSAL.name -> R.string.stock_history_sale_undone
        StockMovementType.ADJUSTMENT.name -> R.string.movement_type_adjustment
        StockMovementType.OPENING.name -> R.string.stock_history_opening
        StockMovementType.IMPORT.name -> R.string.stock_history_imported
        StockMovementType.PURCHASE_RECEIPT.name -> R.string.stock_history_purchase
        StockMovementType.PURCHASE_REVERSAL.name -> R.string.stock_history_purchase_undone
        StockMovementType.SALES_RETURN.name -> R.string.stock_history_return
        else -> R.string.movement_type_adjustment
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class HistoryFilter(val labelRes: Int) {
        ALL(R.string.stock_history_filter_all),
        IN(R.string.stock_history_filter_in),
        OUT(R.string.stock_history_filter_out),
        SALES(R.string.stock_history_filter_sales),
        ADJUSTMENTS(R.string.stock_history_filter_adjust),
        PURCHASES(R.string.stock_history_filter_purchase),
        RETURNS(R.string.stock_history_filter_return);

        fun matches(movement: StockMovement): Boolean = when (this) {
            ALL -> true
            IN -> movement.delta > 0
            OUT -> movement.delta < 0
            SALES -> movement.type == StockMovementType.SALE.name ||
                movement.type == StockMovementType.SALE_REVERSAL.name
            ADJUSTMENTS -> movement.type == StockMovementType.ADJUSTMENT.name ||
                movement.type == StockMovementType.OPENING.name ||
                movement.type == StockMovementType.IMPORT.name
            PURCHASES -> movement.type == StockMovementType.PURCHASE_RECEIPT.name ||
                movement.type == StockMovementType.PURCHASE_REVERSAL.name
            RETURNS -> movement.type == StockMovementType.SALES_RETURN.name
        }
    }

    companion object {
        const val ARG_ITEM_ID = "itemId"
        private val GENERIC_NOTES = setOf(
            "Sale",
            "Sale reversal",
            "Adjustment",
            "Opening",
            "Opening stock",
            "Opening stock · expiry unknown",
            "Import",
            "Goods receipt",
            "Reversed goods receipt",
            "Return to batch"
        )

        fun args(itemId: Long) = bundleOf(ARG_ITEM_ID to itemId)
    }
}
