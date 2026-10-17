package com.kex.vikrsaathi.ui.orders

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.ItemOrderFactBinding
import com.kex.vikrsaathi.databinding.ItemOrderHubBinding
import com.kex.vikrsaathi.domain.finance.HubRecordKind
import com.kex.vikrsaathi.domain.finance.HubRecordStatus
import com.kex.vikrsaathi.domain.finance.HubStatusFilter
import com.kex.vikrsaathi.domain.finance.HubTypeFilter
import com.kex.vikrsaathi.domain.finance.OrderHubMath
import com.kex.vikrsaathi.domain.finance.OrderHubRow
import com.kex.vikrsaathi.ui.finance.FinanceDates
import com.kex.vikrsaathi.ui.finance.FinanceUi
import com.kex.vikrsaathi.ui.finance.PurchaseOrderFragment
import com.kex.vikrsaathi.ui.finance.PurchaseRequestFragment
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu
import com.kex.vikrsaathi.util.ListViewMode
import com.kex.vikrsaathi.util.ListViewPreferences
import com.kex.vikrsaathi.util.ListViewScreen
import com.kex.vikrsaathi.util.ViewModelFactory

class OrdersFragment : Fragment() {

    private var _binding: com.kex.vikrsaathi.databinding.FragmentOrdersBinding? = null
    private val binding get() = _binding!!
    private val drawer get() = binding.ordersDrawer
    private var suppressDrawer = false
    private lateinit var listViewPrefs: ListViewPreferences
    private lateinit var hubAdapter: HubAdapter
    private var viewMode: ListViewMode = ListViewMode.COMFORTABLE

    private val viewModel: OrdersViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = com.kex.vikrsaathi.databinding.FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as VikrSaathiApp
        listViewPrefs = app.listViewPreferences
        viewMode = listViewPrefs.getMode(ListViewScreen.ORDERS)
        if (savedInstanceState == null) {
            when (arguments?.getString(ARG_HUB_TYPE)) {
                HUB_REQUESTS -> viewModel.selectType(HubTypeFilter.REQUESTS)
                HUB_ORDERS -> viewModel.selectType(HubTypeFilter.PURCHASE_ORDERS)
            }
        }

        hubAdapter = HubAdapter(onOpen = ::openRow, onReceive = ::receiveOrder)
        binding.recyclerOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerOrders.adapter = hubAdapter
        binding.textOrdersFilterSummary.setOnClickListener { openOptions() }
        binding.fabNewRequest.setOnClickListener {
            findNavController().navigate(
                R.id.purchaseRequestFragment,
                bundleOf(PurchaseRequestFragment.ARG_REQUEST_ID to 0L)
            )
        }
        setupOptionsMenu()
        installHelpMenu(HelpScreen.VENDORS)
        setupDrawer()
        applyViewMode(viewMode, persist = false)

        viewModel.loading.observe(viewLifecycleOwner) { showOrders() }
        viewModel.failed.observe(viewLifecycleOwner) { showOrders() }
        viewModel.rows.observe(viewLifecycleOwner) { rows ->
            hubAdapter.submit(rows, app.settingsRepository.currencySymbol)
            showOrders()
        }
        viewModel.filterState.observe(viewLifecycleOwner) { state ->
            renderFilter(state)
            showOrders()
        }
    }

    private fun setupOptionsMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_orders, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.action_orders_options) {
                    openOptions()
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun openOptions() {
        viewModel.filterState.value?.let { renderFilter(it) }
        binding.drawerLayout.openDrawer(GravityCompat.END)
    }

    private fun setupDrawer() {
        drawer.switchDateRange.setOnCheckedChangeListener { _, checked ->
            if (suppressDrawer) return@setOnCheckedChangeListener
            viewModel.setDateEnabled(checked)
        }
        drawer.switchTypeFilter.setOnCheckedChangeListener { _, checked ->
            if (suppressDrawer) return@setOnCheckedChangeListener
            viewModel.setTypeEnabled(checked)
        }
        drawer.switchStatusFilter.setOnCheckedChangeListener { _, checked ->
            if (suppressDrawer) return@setOnCheckedChangeListener
            viewModel.setStatusEnabled(checked)
        }
        drawer.buttonOrderDates.setOnClickListener {
            FinanceDates.showRange(this) { start, end -> viewModel.setDates(start, end) }
        }
        drawer.buttonClearOrderDates.setOnClickListener { viewModel.setDates(null, null) }
        drawer.buttonClearFilters.setOnClickListener { viewModel.clearFilters() }
        drawer.chipGroupType.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressDrawer) return@setOnCheckedStateChangeListener
            val type = when (checkedIds.firstOrNull()) {
                R.id.chipTypeRequests -> HubTypeFilter.REQUESTS
                R.id.chipTypeOrders -> HubTypeFilter.PURCHASE_ORDERS
                else -> HubTypeFilter.ALL
            }
            viewModel.setType(type)
        }
        drawer.chipGroupStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressDrawer) return@setOnCheckedStateChangeListener
            val status = when (checkedIds.firstOrNull()) {
                R.id.chipStatusOpen -> HubStatusFilter.OPEN
                R.id.chipStatusCompleted -> HubStatusFilter.COMPLETED
                else -> HubStatusFilter.ALL
            }
            viewModel.setStatus(status)
        }
        drawer.radioViewMode.setOnCheckedChangeListener { _, checkedId ->
            if (suppressDrawer) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.radioViewCompact -> ListViewMode.COMPACT
                R.id.radioViewDetails -> ListViewMode.DETAILS
                else -> ListViewMode.COMFORTABLE
            }
            applyViewMode(mode)
        }
    }

    private fun applyViewMode(mode: ListViewMode, persist: Boolean = true) {
        viewMode = mode
        if (persist) listViewPrefs.setMode(ListViewScreen.ORDERS, mode)
        hubAdapter.viewMode = mode
        val id = when (mode) {
            ListViewMode.COMPACT -> R.id.radioViewCompact
            ListViewMode.DETAILS -> R.id.radioViewDetails
            ListViewMode.COMFORTABLE -> R.id.radioViewComfortable
        }
        if (drawer.radioViewMode.checkedRadioButtonId == id) return
        suppressDrawer = true
        drawer.radioViewMode.check(id)
        suppressDrawer = false
    }

    private fun renderFilter(state: OrdersFilterState) {
        suppressDrawer = true
        drawer.switchDateRange.isChecked = state.dateEnabled
        drawer.switchTypeFilter.isChecked = state.typeEnabled
        drawer.switchStatusFilter.isChecked = state.statusEnabled
        drawer.layoutDateFilter.isVisible = state.dateEnabled
        drawer.chipGroupType.isVisible = state.typeEnabled
        drawer.chipGroupStatus.isVisible = state.statusEnabled
        drawer.buttonOrderDates.text = if (state.from == null || state.to == null) {
            getString(R.string.ledger_all_dates)
        } else {
            getString(R.string.ledger_range, FinanceDates.format(state.from), FinanceDates.format(state.to))
        }
        drawer.buttonClearOrderDates.isVisible = state.from != null && state.to != null
        val typeId = when (state.type) {
            HubTypeFilter.REQUESTS -> R.id.chipTypeRequests
            HubTypeFilter.PURCHASE_ORDERS -> R.id.chipTypeOrders
            HubTypeFilter.ALL -> R.id.chipTypeAll
        }
        if (drawer.chipGroupType.checkedChipId != typeId) drawer.chipGroupType.check(typeId)
        val statusId = when (state.status) {
            HubStatusFilter.OPEN -> R.id.chipStatusOpen
            HubStatusFilter.COMPLETED -> R.id.chipStatusCompleted
            else -> R.id.chipStatusAll
        }
        if (drawer.chipGroupStatus.checkedChipId != statusId) drawer.chipGroupStatus.check(statusId)
        suppressDrawer = false

        val parts = mutableListOf<String>()
        val from = state.appliedFrom
        val to = state.appliedTo
        if (from != null && to != null) {
            parts += getString(R.string.ledger_range, FinanceDates.format(from), FinanceDates.format(to))
        }
        when (state.appliedType) {
            HubTypeFilter.REQUESTS -> parts += getString(R.string.orders_filter_requests)
            HubTypeFilter.PURCHASE_ORDERS -> parts += getString(R.string.orders_filter_pos)
            HubTypeFilter.ALL -> Unit
        }
        when (state.appliedStatus) {
            HubStatusFilter.OPEN -> parts += getString(R.string.orders_filter_open)
            HubStatusFilter.COMPLETED -> parts += getString(R.string.orders_filter_completed)
            else -> Unit
        }
        val active = parts.isNotEmpty()
        binding.textOrdersFilterSummary.isVisible = active
        if (active) {
            val summary = getString(R.string.orders_filtered_prefix, parts.joinToString(" · "))
            binding.textOrdersFilterSummary.text = summary
            binding.textOrdersFilterSummary.contentDescription =
                summary + ". " + getString(R.string.orders_options)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun showOrders() {
        val rows = viewModel.rows.value.orEmpty()
        val failed = viewModel.failed.value == true
        val loading = viewModel.loading.value == true
        binding.progressOrders.isVisible = loading && rows.isEmpty()
        binding.textOrdersEmpty.isVisible = !loading && (failed || rows.isEmpty())
        binding.textOrdersEmpty.setText(
            when {
                failed -> R.string.orders_load_error
                viewModel.filterState.value?.appliedStatus == HubStatusFilter.OPEN -> R.string.orders_empty_open
                viewModel.filterState.value?.appliedStatus == HubStatusFilter.COMPLETED -> R.string.orders_empty_completed
                else -> R.string.orders_empty
            }
        )
        binding.recyclerOrders.isVisible = !failed && rows.isNotEmpty()
    }

    private fun openRow(row: OrderHubRow) {
        if (row.kind == HubRecordKind.REQUEST) {
            findNavController().navigate(
                R.id.purchaseRequestFragment,
                bundleOf(PurchaseRequestFragment.ARG_REQUEST_ID to row.id)
            )
        } else {
            findNavController().navigate(
                R.id.purchaseOrderFragment,
                bundleOf(PurchaseOrderFragment.ARG_ORDER_ID to row.id)
            )
        }
    }

    private fun receiveOrder(row: OrderHubRow) {
        findNavController().navigate(
            R.id.purchaseOrderFragment,
            bundleOf(
                PurchaseOrderFragment.ARG_ORDER_ID to row.id,
                PurchaseOrderFragment.ARG_START_RECEIPT to true
            )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_HUB_TYPE = "hubType"
        const val HUB_REQUESTS = "REQUESTS"
        const val HUB_ORDERS = "ORDERS"
    }
}

private class HubAdapter(
    private val onOpen: (OrderHubRow) -> Unit,
    private val onReceive: (OrderHubRow) -> Unit
) : RecyclerView.Adapter<HubAdapter.Holder>() {

    private var rows: List<OrderHubRow> = emptyList()
    private var symbol: String = ""

    var viewMode: ListViewMode = ListViewMode.COMFORTABLE
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    fun submit(next: List<OrderHubRow>, currencySymbol: String) {
        rows = next
        symbol = currencySymbol
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemOrderHubBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val context = holder.itemView.context
        val request = row.kind == HubRecordKind.REQUEST
        val compact = viewMode == ListViewMode.COMPACT
        val detailed = viewMode == ListViewMode.DETAILS
        val binding = holder.binding

        binding.textOrderKind.setText(
            when {
                detailed && request -> R.string.orders_kind_request
                detailed -> R.string.orders_kind_po
                request -> R.string.orders_badge_request
                else -> R.string.orders_badge_po
            }
        )
        binding.textOrderKind.setTextColor(
            context.getColor(if (request) R.color.orange_800 else R.color.chart_paid)
        )
        binding.textOrderKind.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.surface_muted))
        binding.textOrderNumber.text = row.number
        binding.textOrderNumber.setTextAppearance(
            if (compact) {
                com.google.android.material.R.style.TextAppearance_Material3_TitleSmall
            } else {
                com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            }
        )
        binding.textOrderStatus.setText(statusLabel(row.status))
        paintStatus(binding.textOrderStatus, row.status)

        val density = context.resources.displayMetrics.density
        val horizontal = ((if (compact) 10 else 14) * density).toInt()
        val vertical = ((if (compact) 6 else 12) * density).toInt()
        binding.groupOrderBody.setPadding(horizontal, vertical, horizontal, vertical)

        binding.groupOrderCompact.isVisible = compact
        binding.textOrderMeta.isVisible = !compact && !detailed
        binding.groupOrderFacts.isVisible = detailed
        if (compact) {
            binding.textOrderCompactDate.text = FinanceDates.format(row.dateMillis)
            binding.textOrderCompactDetail.text = compactDetail(context, row)
            binding.textOrderDetail.isVisible = false
        } else if (!detailed) {
            binding.textOrderMeta.text = meta(context, row)
            val detail = detail(context, row)
            binding.textOrderDetail.isVisible = detail != null
            binding.textOrderDetail.text = detail
        } else {
            binding.textOrderDetail.isVisible = false
            bindFacts(binding, context, row)
        }

        val receive = row.kind == HubRecordKind.PURCHASE_ORDER && OrderHubMath.showReceive(row.status)
        binding.buttonOrderAction.isVisible = receive
        binding.buttonOrderAction.minHeight = ((if (compact) 40 else 48) * density).toInt()
        binding.buttonOrderAction.setOnClickListener { onReceive(row) }
        binding.cardOrder.setOnClickListener { onOpen(row) }
    }

    override fun getItemCount(): Int = rows.size

    private fun bindFacts(binding: ItemOrderHubBinding, context: android.content.Context, row: OrderHubRow) {
        val request = row.kind == HubRecordKind.REQUEST
        bindFact(binding.rowFactDate, context.getString(R.string.col_date), FinanceDates.format(row.dateMillis))
        val items = row.lineCount?.let { context.resources.getQuantityString(R.plurals.request_item_count, it, it) }
        bindFact(binding.rowFactItems, context.getString(R.string.orders_label_items), items)
        val quotes = if (request) row.quoteCount?.toString() else null
        bindFact(binding.rowFactQuotes, context.getString(R.string.orders_label_quotes), quotes)
        val supplier = supplier(context, row)?.let { name ->
            if (request && row.ordersToReceive > 0) {
                name + " · " + context.getString(R.string.request_next_receive)
            } else {
                name
            }
        }
        bindFact(binding.rowFactSupplier, context.getString(R.string.orders_label_supplier), supplier)
        val amount = row.amountPaise?.let { FinanceUi.formatPaise(it, symbol) }
        bindFact(binding.rowFactAmount, context.getString(R.string.orders_label_amount), amount)
        val required = row.requiredBy?.let { FinanceDates.format(it) }
        bindFact(binding.rowFactRequired, context.getString(R.string.required_by), required)
        val ordered = row.orderedBase
        val received = row.receivedBase
        val progress = if (ordered != null && received != null) "$received / $ordered" else null
        bindFact(binding.rowFactReceived, context.getString(R.string.orders_label_received), progress)
        val showBar = ordered != null && ordered > 0 && received != null
        binding.barOrderReceived.isVisible = showBar
        if (showBar) {
            binding.barOrderReceived.progress = ((received!! * 100L) / ordered!!).toInt().coerceIn(0, 100)
        }
    }

    private fun bindFact(row: ItemOrderFactBinding, label: String, value: String?) {
        row.root.isVisible = !value.isNullOrBlank()
        if (value.isNullOrBlank()) return
        row.textFactLabel.text = label
        row.textFactValue.text = value
    }

    private fun supplier(context: android.content.Context, row: OrderHubRow): String? {
        if (row.kind == HubRecordKind.PURCHASE_ORDER) {
            return row.vendorName ?: context.getString(R.string.orders_removed_vendor)
        }
        if (row.awardedSupplierCount <= 0) return null
        return if (row.awardedSupplierCount == 1) {
            row.awardedSupplierName ?: context.getString(R.string.orders_removed_vendor)
        } else {
            context.getString(R.string.request_awarded_suppliers, row.awardedSupplierCount)
        }
    }

    private fun compactDetail(context: android.content.Context, row: OrderHubRow): String {
        if (row.kind == HubRecordKind.REQUEST) {
            supplier(context, row)?.let { return it }
            val quotes = row.quoteCount
            if (quotes != null && quotes > 0) return context.getString(R.string.orders_quote_progress, quotes)
            val count = row.lineCount ?: 0
            return context.resources.getQuantityString(R.plurals.request_item_count, count, count)
        }
        val ordered = row.orderedBase
        val received = row.receivedBase
        if (ordered != null && received != null && received > 0) {
            return context.getString(R.string.orders_received_progress, received, ordered)
        }
        return row.vendorName ?: row.amountPaise?.let { FinanceUi.formatPaise(it, symbol) }
            ?: context.getString(R.string.orders_removed_vendor)
    }

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

    private fun detail(context: android.content.Context, row: OrderHubRow): String? {
        if (row.kind == HubRecordKind.REQUEST) {
            if (row.awardedSupplierCount > 0) {
                val who = if (row.awardedSupplierCount == 1) {
                    context.getString(
                        R.string.request_awarded_to,
                        row.awardedSupplierName ?: context.getString(R.string.orders_removed_vendor)
                    )
                } else {
                    context.getString(R.string.request_awarded_suppliers, row.awardedSupplierCount)
                }
                return if (row.ordersToReceive > 0) {
                    who + " · " + context.getString(R.string.request_next_receive)
                } else {
                    who
                }
            }
            val quotes = row.quoteCount ?: return null
            return context.getString(R.string.orders_quote_progress, quotes)
        }
        val ordered = row.orderedBase ?: return null
        val received = row.receivedBase ?: return null
        return context.getString(R.string.orders_received_progress, received, ordered)
    }

    private fun paintStatus(view: TextView, status: HubRecordStatus) {
        val accent = when (status) {
            HubRecordStatus.DRAFT,
            HubRecordStatus.COLLECTING_QUOTES,
            HubRecordStatus.READY_TO_COMPARE,
            HubRecordStatus.ORDERED -> true
            else -> false
        }
        if (accent) {
            view.backgroundTintList = ColorStateList.valueOf(view.context.getColor(R.color.surface_muted))
            view.setTextColor(view.context.getColor(R.color.orange_800))
        } else {
            view.backgroundTintList = ColorStateList.valueOf(view.context.getColor(statusColor(status)))
            view.setTextColor(view.context.getColor(R.color.orange_on_primary))
        }
    }

    class Holder(val binding: ItemOrderHubBinding) : RecyclerView.ViewHolder(binding.root)
}

internal fun statusLabel(status: HubRecordStatus): Int = when (status) {
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

internal fun statusColor(status: HubRecordStatus): Int = when (status) {
    HubRecordStatus.AWARDED, HubRecordStatus.RECEIVED -> R.color.chart_accepted
    HubRecordStatus.CANCELLED -> R.color.balance_payable
    HubRecordStatus.PARTIALLY_AWARDED, HubRecordStatus.PARTIALLY_RECEIVED -> R.color.orange_800
    else -> R.color.text_accent
}
