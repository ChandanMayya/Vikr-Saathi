package com.kex.vikrsaathi.ui.orders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentOrdersBinding
import com.kex.vikrsaathi.databinding.ItemOrderHubBinding
import com.kex.vikrsaathi.domain.finance.HubRecordKind
import com.kex.vikrsaathi.domain.finance.HubRecordStatus
import com.kex.vikrsaathi.domain.finance.HubStatusFilter
import com.kex.vikrsaathi.domain.finance.HubTypeFilter
import com.kex.vikrsaathi.domain.finance.OrderHubMath
import com.kex.vikrsaathi.domain.finance.OrderHubRow
import com.kex.vikrsaathi.ui.finance.FinanceDates
import com.kex.vikrsaathi.ui.finance.FinanceUi
import com.kex.vikrsaathi.ui.finance.PurchaseRequestFragment
import com.kex.vikrsaathi.ui.finance.PurchasingFragment
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.ViewModelFactory
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class OrdersFragment : Fragment() {

    private var _binding: FragmentOrdersBinding? = null
    private val binding get() = _binding!!
    private var suppressChips = false

    private val viewModel: OrdersViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installHelpMenu(HelpScreen.VENDORS)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.add(Menu.NONE, R.id.action_vendor_purchase_order, 1, R.string.new_purchase_order)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId != R.id.action_vendor_purchase_order) return false
                pickVendorForOrder()
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.recyclerOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.buttonOrderDates.setOnClickListener {
            FinanceDates.showRange(this) { start, end -> viewModel.setDates(start, end) }
        }
        binding.buttonClearOrderDates.setOnClickListener { viewModel.setDates(null, null) }
        binding.chipGroupType.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressChips) return@setOnCheckedStateChangeListener
            val type = when (checkedIds.firstOrNull()) {
                R.id.chipTypeRequests -> HubTypeFilter.REQUESTS
                R.id.chipTypeOrders -> HubTypeFilter.PURCHASE_ORDERS
                else -> HubTypeFilter.ALL
            }
            viewModel.setType(type)
        }
        binding.chipGroupStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressChips) return@setOnCheckedStateChangeListener
            val status = when (checkedIds.firstOrNull()) {
                R.id.chipInProgress -> HubStatusFilter.IN_PROGRESS
                R.id.chipToReceive -> HubStatusFilter.TO_RECEIVE
                R.id.chipCompleted -> HubStatusFilter.COMPLETED
                R.id.chipOpenItems -> HubStatusFilter.OPEN
                else -> HubStatusFilter.ALL
            }
            viewModel.setStatus(status)
        }
        binding.fabNewRequest.setOnClickListener {
            findNavController().navigate(
                R.id.purchaseRequestFragment,
                bundleOf(PurchaseRequestFragment.ARG_REQUEST_ID to 0L)
            )
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressOrders.isVisible = loading
        }
        viewModel.failed.observe(viewLifecycleOwner) { failed ->
            if (failed) binding.textOrdersEmpty.setText(R.string.orders_load_error)
        }
        viewModel.rows.observe(viewLifecycleOwner) { rows ->
            val failed = viewModel.failed.value == true
            val loading = viewModel.loading.value == true
            binding.textOrdersEmpty.isVisible = !loading && (failed || rows.isEmpty())
            if (!failed) {
                binding.textOrdersEmpty.setText(
                    if (viewModel.status.value == HubStatusFilter.OPEN) R.string.orders_empty_open else R.string.orders_empty
                )
            }
            binding.recyclerOrders.isVisible = !loading && !failed && rows.isNotEmpty()
            binding.recyclerOrders.adapter = HubAdapter(
                rows,
                (requireActivity().application as VikrSaathiApp).settingsRepository.currencySymbol,
                onOpen = ::openRow,
                onReceive = ::receiveOrder
            )
        }
        viewModel.counts.observe(viewLifecycleOwner) { counts ->
            binding.chipInProgress.text = getString(R.string.orders_in_progress_count, counts.inProgress)
            binding.chipToReceive.text = getString(R.string.orders_to_receive_count, counts.toReceive)
            binding.chipCompleted.text = getString(R.string.orders_completed_count, counts.completed)
            binding.chipOpenItems.text = getString(R.string.orders_open_count, counts.open)
        }
        viewModel.type.observe(viewLifecycleOwner) { type ->
            val id = when (type) {
                HubTypeFilter.REQUESTS -> R.id.chipTypeRequests
                HubTypeFilter.PURCHASE_ORDERS -> R.id.chipTypeOrders
                else -> R.id.chipTypeAll
            }
            if (binding.chipGroupType.checkedChipId == id) return@observe
            suppressChips = true
            binding.chipGroupType.check(id)
            suppressChips = false
        }
        viewModel.status.observe(viewLifecycleOwner) { status ->
            val id = when (status) {
                HubStatusFilter.IN_PROGRESS -> R.id.chipInProgress
                HubStatusFilter.TO_RECEIVE -> R.id.chipToReceive
                HubStatusFilter.COMPLETED -> R.id.chipCompleted
                HubStatusFilter.OPEN -> R.id.chipOpenItems
                else -> View.NO_ID
            }
            if (binding.chipGroupStatus.checkedChipId == id) return@observe
            suppressChips = true
            if (id == View.NO_ID) binding.chipGroupStatus.clearCheck() else binding.chipGroupStatus.check(id)
            suppressChips = false
        }
        viewModel.from.observe(viewLifecycleOwner) { refreshDateLabel() }
        viewModel.to.observe(viewLifecycleOwner) { refreshDateLabel() }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun refreshDateLabel() {
        val start = viewModel.from.value
        val end = viewModel.to.value
        binding.buttonOrderDates.text = if (start == null || end == null) {
            getString(R.string.ledger_all_dates)
        } else {
            getString(R.string.ledger_range, FinanceDates.format(start), FinanceDates.format(end))
        }
        binding.buttonClearOrderDates.isVisible = start != null && end != null
    }

    private fun openRow(row: OrderHubRow) {
        if (row.kind == HubRecordKind.REQUEST) {
            findNavController().navigate(
                R.id.purchaseRequestFragment,
                bundleOf(PurchaseRequestFragment.ARG_REQUEST_ID to row.id)
            )
        } else {
            findNavController().navigate(
                R.id.purchasingFragment,
                bundleOf(PurchasingFragment.ARG_VENDOR_ID to (row.vendorId ?: 0L))
            )
        }
    }

    private fun receiveOrder(row: OrderHubRow) {
        findNavController().navigate(
            R.id.purchasingFragment,
            bundleOf(
                PurchasingFragment.ARG_VENDOR_ID to (row.vendorId ?: 0L),
                PurchasingFragment.ARG_ORDER_ID to row.id,
                PurchasingFragment.ARG_START to PurchasingFragment.START_RECEIPT
            )
        )
    }

    private fun pickVendorForOrder() {
        viewLifecycleOwner.lifecycleScope.launch {
            val vendors = (requireActivity().application as VikrSaathiApp).vendorRepository.getActive()
            if (vendors.isEmpty()) {
                Toast.makeText(requireContext(), R.string.select_vendor, Toast.LENGTH_LONG).show()
                return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.new_purchase_order)
                .setItems(vendors.map { it.name }.toTypedArray()) { _, index ->
                    findNavController().navigate(
                        R.id.purchasingFragment,
                        bundleOf(
                            PurchasingFragment.ARG_VENDOR_ID to vendors[index].id,
                            PurchasingFragment.ARG_START to PurchasingFragment.START_ORDER
                        )
                    )
                }
                .show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private class HubAdapter(
    private val rows: List<OrderHubRow>,
    private val symbol: String,
    private val onOpen: (OrderHubRow) -> Unit,
    private val onReceive: (OrderHubRow) -> Unit
) : RecyclerView.Adapter<HubAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemOrderHubBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val context = holder.itemView.context
        holder.binding.textOrderKind.text = context.getString(
            if (row.kind == HubRecordKind.REQUEST) R.string.orders_badge_request else R.string.orders_badge_po
        )
        holder.binding.textOrderKind.setTextColor(context.getColor(R.color.text_accent))
        holder.binding.textOrderNumber.text = row.number
        holder.binding.textOrderStatus.text = context.getString(statusLabel(row.status))
        holder.binding.textOrderStatus.setTextColor(context.getColor(statusColor(row.status)))
        holder.binding.textOrderMeta.text = meta(context, row)
        val receive = row.kind == HubRecordKind.PURCHASE_ORDER && OrderHubMath.showReceive(row.status)
        holder.binding.buttonOrderAction.isVisible = receive
        holder.binding.buttonOrderAction.setOnClickListener { onReceive(row) }
        holder.itemView.setOnClickListener { onOpen(row) }
    }

    override fun getItemCount(): Int = rows.size

    private fun meta(context: android.content.Context, row: OrderHubRow): String {
        val date = FinanceDates.format(row.dateMillis)
        return if (row.kind == HubRecordKind.REQUEST) {
            context.getString(R.string.orders_request_meta, date, row.lineCount ?: 0)
        } else {
            val vendor = row.vendorName ?: context.getString(R.string.orders_removed_vendor)
            val amount = row.amountPaise?.let { " · ${FinanceUi.formatPaise(it, symbol)}" }.orEmpty()
            vendor + " · " + date + amount
        }
    }

    class Holder(val binding: ItemOrderHubBinding) : RecyclerView.ViewHolder(binding.root)
}

private fun statusLabel(status: HubRecordStatus): Int = when (status) {
    HubRecordStatus.DRAFT -> R.string.request_status_draft
    HubRecordStatus.COLLECTING_QUOTES -> R.string.orders_status_collecting
    HubRecordStatus.READY_TO_COMPARE -> R.string.orders_status_ready
    HubRecordStatus.PARTIALLY_AWARDED -> R.string.request_status_partial
    HubRecordStatus.AWARDED -> R.string.request_status_awarded
    HubRecordStatus.CANCELLED -> R.string.request_status_cancelled
    HubRecordStatus.ORDERED -> R.string.orders_status_ordered
    HubRecordStatus.PARTIALLY_RECEIVED -> R.string.orders_status_partial_receipt
    HubRecordStatus.RECEIVED -> R.string.orders_status_received
}

private fun statusColor(status: HubRecordStatus): Int = when (status) {
    HubRecordStatus.AWARDED, HubRecordStatus.RECEIVED -> R.color.chart_accepted
    HubRecordStatus.CANCELLED -> R.color.text_secondary_dark
    HubRecordStatus.PARTIALLY_AWARDED, HubRecordStatus.PARTIALLY_RECEIVED -> R.color.orange_800
    else -> R.color.text_accent
}
