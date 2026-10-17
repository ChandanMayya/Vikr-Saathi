package com.kex.vikrsaathi.ui.bills

import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.databinding.ItemBillHistoryBinding
import com.kex.vikrsaathi.databinding.ItemBillHistoryCompactBinding
import com.kex.vikrsaathi.databinding.ItemBillHistoryDetailLineBinding
import com.kex.vikrsaathi.databinding.ItemBillHistoryDetailsBinding
import com.kex.vikrsaathi.databinding.ItemBillHistoryLineBinding
import com.kex.vikrsaathi.domain.finance.BillPaymentStatus
import com.kex.vikrsaathi.domain.finance.PaymentMethod
import com.kex.vikrsaathi.ui.finance.FinanceUi
import com.kex.vikrsaathi.util.ListViewMode
import com.kex.vikrsaathi.util.PriceCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BillHistoryAdapter(
    private val currencySymbol: String,
    private val onOpen: (BillWithDetails) -> Unit,
    private val onDuplicate: (BillWithDetails) -> Unit,
    private val onPrint: (BillWithDetails) -> Unit,
    private val onDelete: (BillWithDetails) -> Unit
) : ListAdapter<BillWithDetails, RecyclerView.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy · h:mm a", Locale.getDefault())
    private val expandedIds = mutableSetOf<Long>()

    var itemUnits: Map<Long, String> = emptyMap()
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var viewMode: ListViewMode = ListViewMode.COMFORTABLE
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun getItemViewType(position: Int): Int = when (viewMode) {
        ListViewMode.COMFORTABLE -> VIEW_COMFORTABLE
        ListViewMode.COMPACT -> VIEW_COMPACT
        ListViewMode.DETAILS -> VIEW_DETAILS
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_COMPACT -> CompactVH(ItemBillHistoryCompactBinding.inflate(inflater, parent, false))
            VIEW_DETAILS -> DetailsVH(ItemBillHistoryDetailsBinding.inflate(inflater, parent, false))
            else -> ComfortableVH(ItemBillHistoryBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val bill = getItem(position)
        when (holder) {
            is ComfortableVH -> holder.bind(bill)
            is CompactVH -> holder.bind(bill)
            is DetailsVH -> holder.bind(bill)
        }
    }

    inner class ComfortableVH(private val binding: ItemBillHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bill: BillWithDetails) {
            val lines = saleLines(bill)
            binding.textBillNumber.text = bill.bill.billNumber
            binding.textBillCustomer.text = customerName(bill)
            binding.textBillDate.text = dateFormat.format(Date(bill.bill.date))
            binding.textBillTotal.text = PriceCalculator.formatAmount(bill.bill.total, currencySymbol)
            binding.textItemsHeading.text = binding.root.context.getString(
                R.string.bill_history_items_count,
                lines.size
            )
            bindPayment(binding.textPaymentMethod, binding.textPaymentStatus, bill)
            binding.layoutPaymentMethod.visibility = binding.textPaymentMethod.visibility
            bindSaleLines(
                container = binding.layoutSaleLines,
                moreButton = binding.buttonMoreItems,
                lines = lines,
                billId = bill.bill.id,
                preview = COMFORTABLE_PREVIEW,
                detailed = false,
                showAllLabel = false,
                onToggle = { notifyBoundItem() }
            )
            binding.root.setOnClickListener { onOpen(bill) }
            binding.buttonPrintBill.setOnClickListener { onPrint(bill) }
            binding.buttonDuplicateBill.setOnClickListener { onDuplicate(bill) }
            binding.buttonDeleteBill.setOnClickListener { onDelete(bill) }
        }
    }

    inner class CompactVH(private val binding: ItemBillHistoryCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bill: BillWithDetails) {
            val lines = saleLines(bill)
            val expanded = bill.bill.id in expandedIds
            val context = binding.root.context
            binding.textBillNumber.text = bill.bill.billNumber
            binding.textBillCustomer.text = customerName(bill)
            binding.textBillTotal.text = PriceCalculator.formatAmount(bill.bill.total, currencySymbol)
            bindPaymentChip(binding.textPaymentMethod, bill)
            binding.textBillDate.text = compactMeta(context, bill)
            val shown = if (expanded) lines else lines.take(COMPACT_PREVIEW)
            binding.textItemSummary.isVisible = shown.isNotEmpty()
            binding.textItemSummary.text = shown.joinToString("   ·   ") { line ->
                "${line.itemName} · ${quantityRate(line)}"
            }
            val hidden = (lines.size - COMPACT_PREVIEW).coerceAtLeast(0)
            binding.buttonMoreItems.isVisible = hidden > 0
            binding.buttonMoreItems.text = if (expanded) {
                context.getString(R.string.bill_history_show_less)
            } else {
                context.getString(R.string.bill_history_more_items, hidden)
            }
            binding.buttonMoreItems.setOnClickListener {
                if (bill.bill.id in expandedIds) expandedIds.remove(bill.bill.id) else expandedIds.add(bill.bill.id)
                notifyBoundItem()
            }
            binding.root.setOnClickListener { onOpen(bill) }
            binding.buttonBillActions.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menuInflater.inflate(R.menu.menu_bill_history_row, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_history_print -> onPrint(bill)
                            R.id.action_history_duplicate -> onDuplicate(bill)
                            R.id.action_history_delete -> onDelete(bill)
                        }
                        true
                    }
                    show()
                }
            }
        }
    }

    inner class DetailsVH(private val binding: ItemBillHistoryDetailsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bill: BillWithDetails) {
            val lines = saleLines(bill)
            binding.textBillNumber.text = bill.bill.billNumber
            binding.textBillCustomer.text = customerName(bill)
            binding.textBillDate.text = dateTimeFormat.format(Date(bill.bill.date))
            binding.textBillTotal.text = PriceCalculator.formatAmount(bill.bill.total, currencySymbol)
            binding.textItemsHeading.text = binding.root.context.getString(
                R.string.bill_history_items_count,
                lines.size
            )
            bindPaymentChip(binding.textPaymentMethod, bill)
            binding.layoutPaymentMethod.visibility = binding.textPaymentMethod.visibility
            val subtotal = lines.sumOf { lineAmount(it) }
            val discount = lines.sumOf { PriceCalculator.discountAmount(it.mrp, it.discount, it.quantity) }
            binding.textBillSubtotal.text = PriceCalculator.formatAmount(subtotal, currencySymbol)
            binding.rowBillDiscount.isVisible = discount >= 0.005
            if (discount >= 0.005) {
                binding.textBillDiscount.text = PriceCalculator.formatAmount(discount, currencySymbol)
            }
            val roundOff = bill.bill.roundOff
            binding.rowBillRoundOff.isVisible = kotlin.math.abs(roundOff) >= 0.005
            if (kotlin.math.abs(roundOff) >= 0.005) {
                binding.textBillRoundOff.text = PriceCalculator.formatSignedAmount(roundOff, currencySymbol)
            }
            val grand = PriceCalculator.formatAmount(bill.bill.total, currencySymbol)
            binding.textSummaryGrandTotal.text = grand
            val paid = paidPaise(bill)
            val credit = creditPaise(bill)
            binding.rowPaid.isVisible = paid > 0L
            if (paid > 0L) {
                binding.textSummaryPaid.text = FinanceUi.formatPaise(paid, currencySymbol)
            }
            binding.rowOutstanding.isVisible = credit > 0L
            if (credit > 0L) {
                binding.textSummaryOutstanding.text = FinanceUi.formatPaise(credit, currencySymbol)
            }
            bindDetailLines(
                container = binding.layoutSaleLines,
                moreButton = binding.buttonMoreItems,
                lines = lines,
                billId = bill.bill.id,
                onToggle = { notifyBoundItem() }
            )
            binding.root.setOnClickListener { onOpen(bill) }
            binding.buttonPrintBill.setOnClickListener { onPrint(bill) }
            binding.buttonDuplicateBill.setOnClickListener { onDuplicate(bill) }
            binding.buttonDeleteBill.setOnClickListener { onDelete(bill) }
        }
    }

    private fun bindSaleLines(
        container: LinearLayout,
        moreButton: MaterialButton,
        lines: List<BillItem>,
        billId: Long,
        preview: Int,
        detailed: Boolean,
        showAllLabel: Boolean,
        onToggle: () -> Unit
    ) {
        container.removeAllViews()
        val expanded = billId in expandedIds
        val visible = if (expanded) lines else lines.take(preview)
        if (container.isVisible) {
            val inflater = LayoutInflater.from(container.context)
            visible.forEach { line ->
                val row = ItemBillHistoryLineBinding.inflate(inflater, container, false)
                row.textHistoryItemName.text = line.itemName
                row.textHistoryItemMeta.text = lineMeta(container.context, line, detailed)
                row.textHistoryItemAmount.text = PriceCalculator.formatAmount(lineAmount(line), currencySymbol)
                container.addView(row.root)
            }
        }
        val hidden = (lines.size - preview).coerceAtLeast(0)
        moreButton.isVisible = hidden > 0
        moreButton.text = when {
            expanded -> container.context.getString(R.string.bill_history_show_less)
            showAllLabel -> container.context.getString(R.string.bill_history_show_all)
            else -> container.context.getString(R.string.bill_history_more_items, hidden)
        }
        moreButton.setOnClickListener {
            if (billId in expandedIds) expandedIds.remove(billId) else expandedIds.add(billId)
            onToggle()
        }
    }

    private fun bindDetailLines(
        container: LinearLayout,
        moreButton: MaterialButton,
        lines: List<BillItem>,
        billId: Long,
        onToggle: () -> Unit
    ) {
        container.removeAllViews()
        val expanded = billId in expandedIds
        val visible = if (expanded) lines else lines.take(DETAILS_PREVIEW)
        val inflater = LayoutInflater.from(container.context)
        visible.forEach { line ->
            val row = ItemBillHistoryDetailLineBinding.inflate(inflater, container, false)
            row.textDetailItemName.text = line.itemName
            row.textDetailItemQty.text = labeledQtyRate(container.context, line)
            val adjust = lineAdjustments(container.context, line)
            row.textDetailItemAdjust.isVisible = adjust.isNotEmpty()
            row.textDetailItemAdjust.text = adjust
            row.textDetailItemAmount.text = PriceCalculator.formatAmount(lineAmount(line), currencySymbol)
            container.addView(row.root)
        }
        val hidden = (lines.size - DETAILS_PREVIEW).coerceAtLeast(0)
        moreButton.isVisible = hidden > 0
        moreButton.text = if (expanded) {
            container.context.getString(R.string.bill_history_show_less)
        } else {
            container.context.getString(R.string.bill_history_show_all)
        }
        moreButton.setOnClickListener {
            if (billId in expandedIds) expandedIds.remove(billId) else expandedIds.add(billId)
            onToggle()
        }
    }

    private fun lineAdjustments(context: android.content.Context, line: BillItem): String {
        val parts = mutableListOf<String>()
        if (kotlin.math.abs(line.discount) >= 0.05) {
            parts += context.getString(R.string.bill_history_line_discount, line.discount)
        }
        if (kotlin.math.abs(line.roundOff) >= 0.005) {
            parts += context.getString(R.string.round_off_short) + " " +
                PriceCalculator.formatSignedAmount(line.roundOff, currencySymbol)
        }
        return parts.joinToString(" · ")
    }

    private fun RecyclerView.ViewHolder.notifyBoundItem() {
        val pos = bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
    }

    private fun lineMeta(context: android.content.Context, line: BillItem, detailed: Boolean): String {
        val parts = mutableListOf(labeledQtyRate(context, line))
        if (detailed) {
            val adjust = lineAdjustments(context, line)
            if (adjust.isNotEmpty()) parts += adjust
        } else {
            val roundOff = if (kotlin.math.abs(line.roundOff) >= 0.005) {
                context.getString(R.string.round_off_short) + " " +
                    PriceCalculator.formatSignedAmount(line.roundOff, currencySymbol)
            } else {
                ""
            }
            if (roundOff.isNotEmpty()) parts += roundOff
        }
        return parts.joinToString(" · ")
    }

    private fun labeledQtyRate(context: android.content.Context, line: BillItem): String =
        context.getString(R.string.bill_history_qty, quantityLabel(line)) + " · " +
            context.getString(R.string.bill_history_rate, PriceCalculator.formatAmount(line.finalPrice, currencySymbol))

    private fun quantityLabel(line: BillItem): String {
        val unit = line.itemId?.let { itemUnits[it]?.takeIf { name -> name.isNotBlank() } }.orEmpty()
        return if (unit.isBlank()) line.quantity.toString() else "${line.quantity} $unit"
    }

    private fun quantityRate(line: BillItem): String =
        "${quantityLabel(line)} × ${PriceCalculator.formatAmount(line.finalPrice, currencySymbol)}"

    private fun lineAmount(line: BillItem): Double = line.finalPrice * line.quantity + line.roundOff

    private fun saleLines(bill: BillWithDetails): List<BillItem> = bill.items.sortedBy { it.id }

    private fun customerName(bill: BillWithDetails): String = bill.customer?.name?.takeIf { it.isNotBlank() } ?: "-"

    private fun paidPaise(bill: BillWithDetails): Long =
        bill.payments.filter { it.method != PaymentMethod.CREDIT }.sumOf { it.amountPaise }

    private fun creditPaise(bill: BillWithDetails): Long =
        bill.payments.filter { it.method == PaymentMethod.CREDIT }.sumOf { it.amountPaise }

    private fun compactMeta(context: android.content.Context, bill: BillWithDetails): CharSequence {
        val text = SpannableStringBuilder(dateFormat.format(Date(bill.bill.date)))
        fun appendStatus(label: String, colorRes: Int) {
            val start = text.length
            text.append("  ·  ").append(label)
            text.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, colorRes)),
                start,
                text.length,
                0
            )
        }
        val paid = paidPaise(bill)
        val credit = creditPaise(bill)
        if (paid > 0L) {
            appendStatus(
                context.getString(R.string.bill_history_paid, FinanceUi.formatPaise(paid, currencySymbol)),
                R.color.chart_accepted
            )
        }
        if (credit > 0L) {
            appendStatus(
                context.getString(R.string.bill_history_due, FinanceUi.formatPaise(credit, currencySymbol)),
                R.color.balance_payable
            )
        }
        return text
    }

    private fun bindPaymentChip(methodView: TextView, bill: BillWithDetails) {
        val recorded = bill.payments.isNotEmpty() ||
            (bill.bill.paymentLabel.isNotBlank() && bill.bill.paymentLabel != BillPaymentStatus.LABEL_LEGACY)
        methodView.isVisible = recorded
        if (recorded) {
            methodView.text = FinanceUi.paymentLabel(methodView.context, bill.bill.paymentLabel)
            methodView.setTextColor(
                ContextCompat.getColor(methodView.context, paymentColor(bill.bill.paymentLabel))
            )
        }
    }

    private fun bindPayment(methodView: TextView, statusView: TextView, bill: BillWithDetails) {
        val context = methodView.context
        bindPaymentChip(methodView, bill)
        val paid = paidPaise(bill)
        val credit = creditPaise(bill)
        val parts = mutableListOf<String>()
        if (paid > 0L) {
            parts += context.getString(R.string.bill_history_paid_line, FinanceUi.formatPaise(paid, currencySymbol))
        }
        if (credit > 0L) {
            parts += context.getString(
                R.string.bill_history_outstanding_line,
                FinanceUi.formatPaise(credit, currencySymbol)
            )
        }
        statusView.isVisible = parts.isNotEmpty()
        statusView.text = parts.joinToString("\n")
        val color = when {
            credit > 0L && paid == 0L -> R.color.balance_payable
            credit == 0L && paid > 0L -> R.color.chart_accepted
            else -> R.color.text_secondary_dark
        }
        statusView.setTextColor(ContextCompat.getColor(context, color))
    }

    private fun paymentColor(label: String): Int = when (label) {
        BillPaymentStatus.LABEL_CREDIT -> R.color.balance_payable
        BillPaymentStatus.LABEL_CASH, BillPaymentStatus.LABEL_UPI -> R.color.chart_accepted
        else -> R.color.text_accent
    }

    private class DiffCallback : DiffUtil.ItemCallback<BillWithDetails>() {
        override fun areItemsTheSame(oldItem: BillWithDetails, newItem: BillWithDetails) =
            oldItem.bill.id == newItem.bill.id

        override fun areContentsTheSame(oldItem: BillWithDetails, newItem: BillWithDetails) =
            oldItem == newItem
    }

    companion object {
        private const val VIEW_COMFORTABLE = 0
        private const val VIEW_COMPACT = 1
        private const val VIEW_DETAILS = 2
        private const val COMFORTABLE_PREVIEW = 2
        private const val COMPACT_PREVIEW = 2
        private const val DETAILS_PREVIEW = 8
    }
}
