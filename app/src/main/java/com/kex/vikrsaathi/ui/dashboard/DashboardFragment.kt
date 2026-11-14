package com.kex.vikrsaathi.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.VikrSaathiApp
import com.kex.vikrsaathi.databinding.FragmentDashboardBinding
import com.kex.vikrsaathi.databinding.ItemDashboardAttentionBinding
import com.kex.vikrsaathi.databinding.ItemDashboardTopSellerBinding
import com.kex.vikrsaathi.domain.dashboard.DashboardPeriod
import com.kex.vikrsaathi.util.PriceCalculator
import com.kex.vikrsaathi.util.ViewModelFactory
import com.kex.vikrsaathi.ui.help.HelpScreen
import com.kex.vikrsaathi.ui.help.installHelpMenu

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels {
        ViewModelFactory(requireActivity().application as VikrSaathiApp)
    }

    private var actionsExpanded = false
    private var ignoreHandleClick = false
    private var actionOffset = 0f
    private var vendorsEnabled = true
    private var ordersEnabled = true
    private var extrasRevealed: Boolean? = null
    private var actionGestureActive = false
    private var trayFill: GradientDrawable? = null
    private var trayOutline: QuickActionsOutline? = null
    private var grabFill: GradientDrawable? = null
    private var iconBackings: List<GradientDrawable> = emptyList()
    private var trayPalette: TrayPalette? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        installHelpMenu(HelpScreen.DASHBOARD)

        val app = requireActivity().application as VikrSaathiApp
        updateToolbarTitle(app.settingsRepository.shopName)

        bindGreeting()
        setupQuickActions(savedInstanceState)

        viewModel.shopName.observe(viewLifecycleOwner, ::updateToolbarTitle)

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.skeletonLoader.root.isVisible = loading
            binding.layoutStats.isVisible = !loading
            val showActions = viewModel.periodStats.value != null
            binding.quickActionsSheet.isVisible = showActions
            if (showActions) {
                binding.quickActionsSheet.post {
                    if (_binding == null) return@post
                    updateSheetBounds()
                    val behavior = BottomSheetBehavior.from(binding.quickActionsSheet)
                    val target = if (actionsExpanded) {
                        BottomSheetBehavior.STATE_EXPANDED
                    } else {
                        BottomSheetBehavior.STATE_COLLAPSED
                    }
                    if (behavior.state != target) behavior.state = target
                    renderActions(if (actionsExpanded) 1f else 0f)
                    applyListScrolling()
                }
            } else {
                binding.dashboardScroll.updatePadding(bottom = dp(16))
            }
        }

        binding.togglePeriod.check(
            when (viewModel.selectedPeriod) {
                DashboardPeriod.WEEK -> R.id.buttonPeriodWeek
                DashboardPeriod.MONTH -> R.id.buttonPeriodMonth
                DashboardPeriod.TODAY -> R.id.buttonPeriodToday
            }
        )
        binding.togglePeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val period = when (checkedId) {
                R.id.buttonPeriodWeek -> DashboardPeriod.WEEK
                R.id.buttonPeriodMonth -> DashboardPeriod.MONTH
                else -> DashboardPeriod.TODAY
            }
            viewModel.setPeriod(period)
        }
        if (arguments?.getString("openPeriod") == "WEEK") {
            binding.togglePeriod.check(R.id.buttonPeriodWeek)
        }
        viewModel.dueBoard.observe(viewLifecycleOwner) { renderAttention() }
        binding.cardPeriodSales.setOnClickListener { openBillsForPeriod() }

        viewModel.periodStats.observe(viewLifecycleOwner) { stats ->
            if (stats == null) return@observe
            val symbol = viewModel.currencySymbol.value.orEmpty().ifBlank { "₹" }
            binding.textSalesTitle.setText(salesTitle(stats.period))
            binding.textTodaySalesAmount.text = PriceCalculator.formatAmount(stats.totalSales, symbol)
            binding.textTodayBillCount.text = resources.getQuantityString(
                R.plurals.dashboard_bill_count,
                stats.billCount,
                stats.billCount
            )
            val average = if (stats.billCount == 0) 0.0 else stats.totalSales / stats.billCount
            binding.textAverageBill.text = getString(
                R.string.dashboard_avg_bill_short,
                PriceCalculator.formatAmount(average, symbol)
            )
            renderComparison(stats)
            binding.salesSparkline.setValues(stats.trend.map { it.revenue })
            binding.salesSparkline.contentDescription = trendDescription(stats.trend.map { it.revenue })
            renderTopSellers(stats.topItems)
        }

        viewModel.customerDuesPaise.observe(viewLifecycleOwner) { paise ->
            val symbol = viewModel.currencySymbol.value.orEmpty().ifBlank { "₹" }
            binding.textCustomerDues.text = com.kex.vikrsaathi.ui.finance.FinanceUi.formatBalance(paise, symbol)
        }
        viewModel.vendorPayablesPaise.observe(viewLifecycleOwner) { paise ->
            val symbol = viewModel.currencySymbol.value.orEmpty().ifBlank { "₹" }
            binding.textVendorPayables.text = com.kex.vikrsaathi.ui.finance.FinanceUi.formatBalance(paise, symbol)
        }

        viewModel.orderAttentionCount.observe(viewLifecycleOwner) { count ->
            binding.textOrdersAttention.isVisible = count > 0
            if (count > 0) {
                binding.textOrdersAttention.text = getString(R.string.orders_need_attention, count)
            }
            renderAttention()
        }
        viewModel.lowStock.observe(viewLifecycleOwner) { renderAttention() }

        binding.cardNewBill.setOnClickListener { openNewBill() }
        binding.cardCustomers.setOnClickListener { openCustomers() }
        binding.cardItems.setOnClickListener { openItems() }
        binding.cardBillsHistory.setOnClickListener { openBillsHistory() }
        binding.cardVendors.setOnClickListener { openVendors() }
        binding.cardStock.setOnClickListener { openInventory() }
        viewModel.expiryAlerts.observe(viewLifecycleOwner) { renderAttention() }
        binding.cardAnalysis.setOnClickListener { openAnalysis() }
        binding.cardOrders.setOnClickListener { openOrders() }
        binding.cardSettings.setOnClickListener { openSettings() }
        binding.cardWarrantyService.setOnClickListener { openWarranty() }
        binding.textCustomerDues.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_outstanding)
        }
        binding.textVendorPayables.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_vendors)
        }
        applyDashboardLayout()
        renderActions(if (actionsExpanded) 1f else 0f)
    }

    override fun onResume() {
        super.onResume()
        bindGreeting()
        applyDashboardLayout()
        viewModel.refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (_binding != null && binding.quickActionsSheet.isVisible) captureActionsState()
        outState.putBoolean(STATE_ACTIONS_EXPANDED, actionsExpanded)
        super.onSaveInstanceState(outState)
    }

    private fun applyDashboardLayout() {
        val settings = (requireActivity().application as VikrSaathiApp).settingsRepository
        vendorsEnabled = settings.vendorManagementEnabled
        ordersEnabled = settings.orderManagementEnabled
        binding.cardCustomerDues.isVisible = settings.customerBalanceEnabled
        binding.cardVendorPayables.isVisible = vendorsEnabled
        binding.layoutMoney.isVisible = settings.customerBalanceEnabled || vendorsEnabled
        updateActionConstraints(vendorsEnabled, ordersEnabled, settings.warrantyEnabled)
        renderAttention()
        renderActions(actionOffset)
    }

    private fun openBillsForPeriod(itemName: String = "") {
        val stats = viewModel.periodStats.value ?: return
        findNavController().navigate(
            R.id.action_dashboard_to_bills_history,
            bundleOf(
                "rangeStart" to stats.window.startInclusive,
                "rangeEnd" to stats.window.endExclusive,
                "itemName" to itemName
            )
        )
    }

    private fun bindGreeting() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> R.string.dashboard_greeting_morning
            in 12..16 -> R.string.dashboard_greeting_afternoon
            in 17..20 -> R.string.dashboard_greeting_evening
            else -> R.string.dashboard_greeting_night
        }
        binding.textGreeting.setText(greeting)
        val date = java.text.SimpleDateFormat("EEEE, d MMM", java.util.Locale.getDefault())
            .format(java.util.Date())
        binding.textGreetingDate.text = date
    }

    private fun salesTitle(period: DashboardPeriod): Int = when (period) {
        DashboardPeriod.TODAY -> R.string.dashboard_sales_today
        DashboardPeriod.WEEK -> R.string.dashboard_sales_week
        DashboardPeriod.MONTH -> R.string.dashboard_sales_month
    }

    private fun renderComparison(stats: com.kex.vikrsaathi.data.model.DashboardPeriodStats) {
        val comparison = stats.comparison
        val versus = when (stats.period) {
            DashboardPeriod.TODAY -> getString(R.string.dashboard_vs_yesterday)
            DashboardPeriod.WEEK -> getString(R.string.dashboard_vs_previous_week)
            DashboardPeriod.MONTH -> getString(R.string.dashboard_vs_previous_period)
        }
        val text: String
        val color: Int
        when {
            comparison.previousSales == 0.0 && comparison.currentSales == 0.0 -> {
                text = getString(R.string.dashboard_no_sales_either, versus)
                color = R.color.text_secondary_dark
            }
            comparison.salesPercent == null -> {
                text = getString(R.string.dashboard_no_prior_sales, versus)
                color = R.color.text_secondary_dark
            }
            else -> {
                val arrow = when {
                    comparison.salesDelta > 0.0 -> "↑"
                    comparison.salesDelta < 0.0 -> "↓"
                    else -> "→"
                }
                val percent = kotlin.math.abs(comparison.salesPercent)
                val percentText = if (percent % 1.0 < 0.05 || percent % 1.0 > 0.95) {
                    String.format(java.util.Locale.getDefault(), "%.0f%%", percent)
                } else {
                    String.format(java.util.Locale.getDefault(), "%.1f%%", percent)
                }
                text = getString(R.string.dashboard_comparison_percent, arrow, percentText, versus)
                color = when {
                    comparison.salesDelta > 0.0 -> R.color.balance_receivable
                    comparison.salesDelta < 0.0 -> R.color.balance_payable
                    else -> R.color.text_secondary_dark
                }
            }
        }
        binding.textSalesComparison.text = text
        binding.textSalesComparison.setTextColor(requireContext().getColor(color))
    }

    private fun trendDescription(values: List<Double>): String {
        if (values.isEmpty()) return getString(R.string.dashboard_trend_empty)
        if (values.size == 1) return getString(R.string.dashboard_trend_point)
        val delta = values.last() - values.first()
        return getString(
            when {
                delta > 0.0 -> R.string.dashboard_trend_up
                delta < 0.0 -> R.string.dashboard_trend_down
                else -> R.string.dashboard_trend_flat
            }
        )
    }

    private fun renderTopSellers(items: List<com.kex.vikrsaathi.data.model.DashboardTopItem>) {
        val shown = items.take(3)
        binding.topSellerRows.removeAllViews()
        binding.textTopItemsEmpty.isVisible = shown.isEmpty()
        if (shown.isEmpty()) return
        val maxQty = shown.maxOf { it.quantity }.coerceAtLeast(1)
        shown.forEachIndexed { index, item ->
            val row = ItemDashboardTopSellerBinding.inflate(layoutInflater, binding.topSellerRows, true)
            row.textSellerRank.text = getString(R.string.dashboard_seller_rank, index + 1)
            row.textSellerName.text = item.name
            row.textSellerQty.text = getString(R.string.dashboard_sold_count, item.quantity)
            row.root.setOnClickListener { openBillsForPeriod(item.name) }
            row.sellerTrack.post {
                if (_binding == null) return@post
                val width = row.sellerTrack.width
                val bar = (width * item.quantity / maxQty.toFloat()).toInt().coerceAtLeast(dp(8))
                row.sellerBar.layoutParams = row.sellerBar.layoutParams.apply { this.width = bar }
            }
        }
    }

    private fun renderAttention() {
        if (_binding == null) return
        val settings = (requireActivity().application as VikrSaathiApp).settingsRepository
        val rows = mutableListOf<AttentionRow>()
        val low = viewModel.lowStock.value?.count ?: 0
        if (low > 0) {
            rows += AttentionRow(
                icon = R.drawable.ic_dashboard_stock,
                message = resources.getQuantityString(R.plurals.dashboard_attention_low_stock, low, low),
                action = getString(R.string.dashboard_attention_review_stock)
            ) { findNavController().navigate(R.id.action_dashboard_to_stock) }
        }
        val board = viewModel.dueBoard.value
        if (settings.customerBalanceEnabled && board != null && board.overdueCount > 0) {
            rows += AttentionRow(
                icon = R.drawable.ic_calendar,
                message = resources.getQuantityString(
                    R.plurals.dashboard_attention_overdue,
                    board.overdueCount,
                    board.overdueCount
                ),
                action = getString(R.string.dashboard_attention_view_dues)
            ) {
                findNavController().navigate(
                    R.id.action_dashboard_to_outstanding,
                    bundleOf("dueFilter" to "overdue")
                )
            }
        } else if (settings.customerBalanceEnabled && board != null && board.soonCount > 0) {
            rows += AttentionRow(
                icon = R.drawable.ic_calendar,
                message = resources.getQuantityString(
                    R.plurals.dashboard_attention_due_soon,
                    board.soonCount,
                    board.soonCount
                ),
                action = getString(R.string.dashboard_attention_view_dues)
            ) {
                findNavController().navigate(
                    R.id.action_dashboard_to_outstanding,
                    bundleOf("dueFilter" to "soon")
                )
            }
        }
        val orders = viewModel.orderAttentionCount.value ?: 0
        if (settings.orderManagementEnabled && orders > 0) {
            rows += AttentionRow(
                icon = R.drawable.ic_dashboard_orders,
                message = resources.getQuantityString(R.plurals.dashboard_attention_orders, orders, orders),
                action = getString(R.string.dashboard_attention_review_orders)
            ) { openOrders() }
        }
        val alerts = viewModel.expiryAlerts.value.orEmpty()
        if (settings.batchTrackingEnabled && alerts.isNotEmpty()) {
            val filter = if (alerts.any { it.expired }) "expired" else "soon"
            rows += AttentionRow(
                icon = R.drawable.ic_calendar,
                message = resources.getQuantityString(
                    R.plurals.dashboard_attention_expiry,
                    alerts.size,
                    alerts.size
                ),
                action = getString(R.string.dashboard_attention_review_expiry)
            ) {
                findNavController().navigate(
                    R.id.action_dashboard_to_stock,
                    bundleOf("batchFilter" to filter)
                )
            }
        }
        binding.attentionRows.removeAllViews()
        binding.textAttentionEmpty.isVisible = rows.isEmpty()
        binding.textAttentionCount.isVisible = rows.isNotEmpty()
        binding.textAttentionCount.text = rows.size.toString()
        rows.forEach { row ->
            val line = ItemDashboardAttentionBinding.inflate(layoutInflater, binding.attentionRows, true)
            line.imageAttention.setImageResource(row.icon)
            line.textAttentionMessage.text = row.message
            line.textAttentionAction.text = row.action
            line.root.setOnClickListener { row.onClick() }
        }
    }

    private data class AttentionRow(
        val icon: Int,
        val message: String,
        val action: String,
        val onClick: () -> Unit
    )

    private fun setupQuickActions(savedInstanceState: Bundle?) {
        actionsExpanded = savedInstanceState?.getBoolean(STATE_ACTIONS_EXPANDED) == true
        val behavior = BottomSheetBehavior.from(binding.quickActionsSheet)
        behavior.isHideable = false
        behavior.isFitToContents = true
        behavior.isDraggable = true
        behavior.isGestureInsetBottomIgnored = true
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING ||
                    newState == BottomSheetBehavior.STATE_SETTLING
                ) {
                    actionGestureActive = true
                    if (newState == BottomSheetBehavior.STATE_DRAGGING) ignoreHandleClick = true
                    return
                }
                if (newState == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                    bottomSheet.post {
                        if (_binding == null) return@post
                        BottomSheetBehavior.from(bottomSheet).state = if (actionsExpanded) {
                            BottomSheetBehavior.STATE_EXPANDED
                        } else {
                            BottomSheetBehavior.STATE_COLLAPSED
                        }
                    }
                    return
                }
                if (newState == BottomSheetBehavior.STATE_EXPANDED ||
                    newState == BottomSheetBehavior.STATE_COLLAPSED
                ) {
                    actionGestureActive = false
                    actionsExpanded = newState == BottomSheetBehavior.STATE_EXPANDED
                    renderActions(if (actionsExpanded) 1f else 0f)
                    applyListScrolling()
                    updateSheetBounds()
                    bottomSheet.post { ignoreHandleClick = false }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                renderActions(slideOffset)
            }
        })
        binding.dashboardRoot.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (_binding == null) return@addOnLayoutChangeListener
            updateSheetBounds()
        }
        binding.quickActionsMotion.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (_binding == null || bottom - top == oldBottom - oldTop) return@addOnLayoutChangeListener
            updateSheetBounds()
        }
        binding.quickActionsHandle.setOnClickListener {
            if (ignoreHandleClick) return@setOnClickListener
            toggleQuickActions()
        }
        binding.quickActionsTitleRow.setOnClickListener {
            if (ignoreHandleClick) return@setOnClickListener
            toggleQuickActions()
        }
        renderActions(if (actionsExpanded) 1f else 0f)
        applyListScrolling()
    }

    /**
     * Nested scrolling is what lets a downward drag collapse the sheet.
     * Leave it alone until the sheet settles, then match the settled position.
     */
    private fun applyListScrolling() {
        if (!QuickActionsMotion.shouldRetargetListScroll(actionGestureActive)) return
        val scroll = QuickActionsMotion.listScrollEnabled(actionsExpanded)
        binding.quickActionsExpanded.isNestedScrollingEnabled = scroll
        if (!scroll) binding.quickActionsExpanded.scrollTo(0, 0)
    }

    private fun toggleQuickActions() {
        val behavior = BottomSheetBehavior.from(binding.quickActionsSheet)
        behavior.state = if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            BottomSheetBehavior.STATE_COLLAPSED
        } else {
            BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun captureActionsState() {
        val state = BottomSheetBehavior.from(binding.quickActionsSheet).state
        if (state == BottomSheetBehavior.STATE_EXPANDED) actionsExpanded = true
        if (state == BottomSheetBehavior.STATE_COLLAPSED) actionsExpanded = false
    }

    private fun updateSheetBounds() {
        val root = binding.dashboardRoot
        if (root.height == 0) return
        val peek = collapsedPeek()
        val behavior = BottomSheetBehavior.from(binding.quickActionsSheet)
        if (behavior.peekHeight != peek) behavior.peekHeight = peek
        val pad = peek + dp(12)
        if (binding.dashboardScroll.paddingBottom != pad) {
            binding.dashboardScroll.updatePadding(bottom = pad)
        }
        if (actionGestureActive) return
        val header = trayHeaderHeight()
        val content = binding.quickActionsMotion.height
        if (header == 0 || content == 0) return
        val scrollPad = binding.quickActionsExpanded.let { it.paddingTop + it.paddingBottom }
        val natural = header + content + scrollPad
        val sheetHeight = QuickActionsMotion.expandedTrayHeight(
            naturalPx = natural,
            availablePx = root.height,
            peekPx = peek,
            minVisibleDashboardPx = dp(QuickActionsMotion.MIN_VISIBLE_DASHBOARD_DP)
        )
        val viewport = QuickActionsMotion.actionViewportHeight(
            contentPx = content + scrollPad,
            sheetPx = sheetHeight,
            headerPx = header
        )
        val scrolls = QuickActionsMotion.trayScrolls(content + scrollPad, viewport)
        val scrollHeight = if (scrolls) viewport else ViewGroup.LayoutParams.WRAP_CONTENT
        val scrollParams = binding.quickActionsExpanded.layoutParams as LinearLayout.LayoutParams
        if (scrollParams.height != scrollHeight || scrollParams.weight != 0f) {
            scrollParams.height = scrollHeight
            scrollParams.weight = 0f
            binding.quickActionsExpanded.layoutParams = scrollParams
        }
        val sheetParams = binding.quickActionsSheet.layoutParams
        if (sheetParams.height != sheetHeight) {
            sheetParams.height = sheetHeight
            binding.quickActionsSheet.layoutParams = sheetParams
        }
        paintOutline(actionOffset, sheetHeight, peek)
    }

    private fun collapsedPeek(): Int {
        val header = trayHeaderHeight()
        return if (header == 0) dp(156) else header + dp(100)
    }

    private fun trayHeaderHeight(): Int =
        binding.quickActionsHandle.height + binding.quickActionsTitleRow.height

    private fun updateActionConstraints(vendors: Boolean, orders: Boolean, warranty: Boolean) {
        val motion = binding.quickActionsMotion
        val collapsed = motion.getConstraintSet(R.id.collapsed) ?: return
        val expanded = motion.getConstraintSet(R.id.expanded) ?: return
        fun show(on: Boolean) = if (on) View.VISIBLE else View.GONE
        listOf(collapsed, expanded).forEach { set ->
            set.setVisibility(R.id.cardVendors, show(vendors))
            set.setVisibility(R.id.cardOrders, show(orders))
            set.setVisibility(R.id.cardWarrantyService, show(warranty))
        }
        val master = dp(if (vendors) 96 else 92)
        val tools = dp(if (orders) 108 else 92)
        listOf(R.id.cardItems, R.id.cardCustomers, R.id.cardVendors).forEach { id ->
            expanded.constrainHeight(id, master)
        }
        listOf(R.id.cardStock, R.id.cardAnalysis, R.id.cardOrders).forEach { id ->
            collapsed.constrainHeight(id, tools)
            expanded.constrainHeight(id, tools)
        }
        val toolsTop = dp(4 + 92 + 8 + (if (vendors) 96 else 92) + 8)
        collapsed.setMargin(R.id.cardStock, ConstraintSet.TOP, toolsTop)
        expanded.setMargin(R.id.cardStock, ConstraintSet.TOP, toolsTop)
        motion.updateState(R.id.collapsed, collapsed)
        motion.updateState(R.id.expanded, expanded)
        motion.progress = actionOffset
        motion.post {
            if (_binding == null) return@post
            updateSheetBounds()
        }
    }

    private fun renderActions(offset: Float) {
        val t = offset.coerceIn(0f, 1f)
        actionOffset = t
        binding.quickActionsMotion.progress = t
        val titleRes = if (QuickActionsMotion.useExpandedTitle(t)) {
            R.string.dashboard_actions
        } else {
            R.string.dashboard_quick_actions
        }
        val title = getString(titleRes)
        if (binding.textQuickActions.text != title) {
            binding.textQuickActions.text = title
        }
        paintTray(t)
        paintActions(t)
        val settled = QuickActionsMotion.clicksSettled(t)
        setCardClicks(primaryCards(), settled)
        setCardClicks(extraCards(), settled && t >= 0.96f)
        val revealExtras = QuickActionsMotion.extraAlpha(QuickAction.INVENTORY, t) > 0.05f
        if (revealExtras != extrasRevealed) {
            extrasRevealed = revealExtras
            val mode = if (revealExtras) {
                View.IMPORTANT_FOR_ACCESSIBILITY_YES
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            extraCards().forEach { it.importantForAccessibility = mode }
        }
        val description = if (t >= 0.5f) {
            R.string.dashboard_collapse_quick_actions
        } else {
            R.string.dashboard_expand_quick_actions
        }
        val label = getString(description)
        if (binding.quickActionsHandle.contentDescription != label) {
            binding.quickActionsHandle.contentDescription = label
        }
        if (binding.quickActionsTitleRow.contentDescription != label) {
            binding.quickActionsTitleRow.contentDescription = label
        }
    }

    private fun paintTray(t: Float) {
        val palette = trayPalette()
        val fill = trayFill()
        val tray = ColorUtils.blendARGB(palette.collapsedFill, palette.expandedFill, t)
        fill.setColor(tray)
        fill.setStroke(0, android.graphics.Color.TRANSPARENT)
        paintOutline(t, binding.quickActionsSheet.height, collapsedPeek())
        val ink = ColorUtils.blendARGB(palette.onCollapsed, palette.onExpanded, t)
        binding.textQuickActions.setTextColor(ink)
        binding.quickActionsChevron.imageTintList = ColorStateList.valueOf(ink)
        binding.quickActionsChevron.rotation = QuickActionsMotion.chevronRotation(t)
        grabFill?.setColor(palette.accent)
        val backingAlpha = (QuickActionsMotion.iconBackingAlpha(t) * 255f).toInt().coerceIn(0, 255)
        iconBackings().forEach { backing ->
            backing.setColor(palette.iconBacking)
            backing.alpha = backingAlpha
        }
    }

    private fun paintActions(t: Float) {
        val palette = trayPalette()
        val elevation = 6f * resources.displayMetrics.density * t
        val labelScale = QuickActionsMotion.labelScale(t)
        primaryLooks().forEach { look ->
            val tile = ColorUtils.blendARGB(palette.collapsedFill, look.endColor, t)
            look.card.setCardBackgroundColor(tile)
            look.card.cardElevation = elevation
            val scale = QuickActionsMotion.iconScale(t, look.iconDp)
            look.icon.scaleX = scale
            look.icon.scaleY = scale
            val iconInk = QuickActionsMotion.tileInk(t, palette.accent, palette.onTile)
            val labelInk = QuickActionsMotion.tileInk(t, palette.onCollapsed, palette.onTile)
            look.icon.imageTintList = ColorStateList.valueOf(iconInk)
            look.label.scaleX = labelScale
            look.label.scaleY = labelScale
            look.label.setTextColor(labelInk)
        }
        extraLooks().forEach { look ->
            look.card.cardElevation = elevation
            val scale = QuickActionsMotion.iconScale(t, look.iconDp)
            look.icon.scaleX = scale
            look.icon.scaleY = scale
        }
    }

    private fun setCardClicks(cards: List<MaterialCardView>, enabled: Boolean) {
        cards.forEach { card ->
            card.isClickable = enabled
            card.isFocusable = enabled
        }
    }

    private fun primaryCards(): List<MaterialCardView> = listOf(
        binding.cardNewBill,
        binding.cardBillsHistory,
        binding.cardItems,
        binding.cardCustomers
    )

    private fun extraCards(): List<MaterialCardView> = listOf(
        binding.cardVendors,
        binding.cardStock,
        binding.cardAnalysis,
        binding.cardOrders,
        binding.cardWarrantyService,
        binding.cardSettings
    )

    private fun primaryLooks(): List<ActionPaint> {
        val masterIcon = if (vendorsEnabled) 28f else 36f
        return listOf(
            ActionPaint(binding.cardNewBill, binding.iconNewBill, binding.labelNewBill, color(R.color.orange_700), 36f),
            ActionPaint(binding.cardBillsHistory, binding.iconBillsHistory, binding.labelBillsHistory, color(R.color.orange_600), 36f),
            ActionPaint(binding.cardItems, binding.iconItems, binding.labelItems, color(R.color.orange_500), masterIcon),
            ActionPaint(binding.cardCustomers, binding.iconCustomers, binding.labelCustomers, color(R.color.orange_800), masterIcon)
        )
    }

    private fun extraLooks(): List<ActionPaint> {
        val masterIcon = if (vendorsEnabled) 28f else 36f
        val toolIcon = if (ordersEnabled) 28f else 36f
        return listOf(
            ActionPaint(binding.cardVendors, binding.iconVendors, binding.labelVendors, color(R.color.orange_900), masterIcon),
            ActionPaint(binding.cardStock, binding.iconStock, binding.labelStock, color(R.color.orange_800), toolIcon),
            ActionPaint(binding.cardAnalysis, binding.iconAnalysis, binding.labelAnalysis, color(R.color.orange_700), toolIcon),
            ActionPaint(binding.cardOrders, binding.iconOrders, binding.labelOrders, color(R.color.orange_600), toolIcon),
            ActionPaint(binding.cardWarrantyService, binding.iconWarranty, binding.labelWarranty, color(R.color.orange_800), 36f),
            ActionPaint(binding.cardSettings, binding.iconSettings, binding.labelSettings, color(R.color.orange_900), 36f)
        )
    }

    private fun color(resId: Int): Int = requireContext().getColor(resId)

    private fun trayPalette(): TrayPalette {
        trayPalette?.let { return it }
        val sheet = binding.quickActionsSheet
        val collapsed = color(R.color.surface_muted)
        val expanded = MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorSurface)
        val onSurface = MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSurface)
        val accent = MaterialColors.getColor(sheet, androidx.appcompat.R.attr.colorPrimary)
        val onTile = MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnPrimary)
        val palette = TrayPalette(
            collapsedFill = collapsed,
            expandedFill = expanded,
            onCollapsed = onSurface,
            onExpanded = onSurface,
            accent = accent,
            onTile = onTile,
            iconBacking = ColorUtils.setAlphaComponent(accent, 64),
            collapsedBorder = color(R.color.quick_actions_stroke),
            expandedBorder = color(R.color.quick_actions_stroke_open)
        )
        trayPalette = palette
        return palette
    }

    private fun trayFill(): GradientDrawable {
        trayFill?.let { return it }
        val drawable = binding.quickActionsSheet.background.mutate() as GradientDrawable
        binding.quickActionsSheet.background = drawable
        val grab = binding.quickActionsGrab.background.mutate() as GradientDrawable
        binding.quickActionsGrab.background = grab
        grabFill = grab
        trayFill = drawable
        return drawable
    }

    private fun trayOutline(): QuickActionsOutline {
        trayOutline?.let { return it }
        val outline = QuickActionsOutline(
            cornerRadiusPx = resources.getDimension(R.dimen.quick_actions_corner_radius),
            thicknessPx = resources.getDimension(R.dimen.quick_actions_outline)
        )
        binding.quickActionsSheet.foreground = outline
        trayOutline = outline
        return outline
    }

    private fun paintOutline(offset: Float, sheetPx: Int, peekPx: Int) {
        val palette = trayPalette()
        val outline = trayOutline()
        val border = ColorUtils.blendARGB(
            palette.collapsedBorder,
            palette.expandedBorder,
            QuickActionsMotion.trayBorderAlpha(offset)
        )
        val alpha = (QuickActionsMotion.trayOutlineAlpha(offset) * 255f).toInt().coerceIn(0, 255)
        outline.color = ColorUtils.setAlphaComponent(border, alpha)
        outline.visibleHeightPx = QuickActionsMotion.visibleTrayHeight(sheetPx, peekPx, offset)
    }

    private fun iconBackings(): List<GradientDrawable> {
        if (iconBackings.isNotEmpty()) return iconBackings
        iconBackings = listOf(
            binding.backingNewBill,
            binding.backingBillsHistory,
            binding.backingItems,
            binding.backingCustomers
        ).map { view ->
            val drawable = view.background.mutate() as GradientDrawable
            view.background = drawable
            drawable
        }
        return iconBackings
    }

    private data class TrayPalette(
        val collapsedFill: Int,
        val expandedFill: Int,
        val onCollapsed: Int,
        val onExpanded: Int,
        val accent: Int,
        val onTile: Int,
        val iconBacking: Int,
        val collapsedBorder: Int,
        val expandedBorder: Int
    )

    private data class ActionPaint(
        val card: MaterialCardView,
        val icon: ImageView,
        val label: TextView,
        val endColor: Int,
        val iconDp: Float
    )

    private fun openNewBill() {
        findNavController().navigate(R.id.action_dashboard_to_bill)
    }

    private fun openBillsHistory() {
        findNavController().navigate(R.id.action_dashboard_to_bills_history)
    }

    private fun openItems() {
        findNavController().navigate(R.id.action_dashboard_to_items)
    }

    private fun openCustomers() {
        findNavController().navigate(R.id.action_dashboard_to_customers)
    }

    private fun openVendors() {
        findNavController().navigate(R.id.action_dashboard_to_vendors)
    }

    private fun openInventory() {
        findNavController().navigate(R.id.action_dashboard_to_stock)
    }

    private fun openAnalysis() {
        findNavController().navigate(R.id.action_dashboard_to_analysis)
    }

    private fun openOrders() {
        findNavController().navigate(R.id.action_dashboard_to_orders)
    }

    private fun openWarranty() {
        findNavController().navigate(R.id.action_dashboard_to_warranty)
    }

    private fun openSettings() {
        findNavController().navigate(R.id.action_dashboard_to_settings)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        trayFill = null
        trayOutline = null
        grabFill = null
        iconBackings = emptyList()
        trayPalette = null
        _binding = null
    }

    private fun updateToolbarTitle(name: String) {
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = name
    }

    companion object {
        private const val STATE_ACTIONS_EXPANDED = "quick_actions_expanded"
    }
}
