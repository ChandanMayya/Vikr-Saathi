package com.kex.vikrsaathi.ui.bill

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.entity.BillVersionItem
import com.kex.vikrsaathi.domain.bill.WarrantyPolicy
import com.kex.vikrsaathi.domain.bill.warrantySnapshot
import com.kex.vikrsaathi.data.entity.BillVersionPayment
import com.kex.vikrsaathi.data.model.BillVersionDetails
import com.kex.vikrsaathi.databinding.ItemBillVersionBinding
import com.kex.vikrsaathi.databinding.ItemBillVersionLineBinding
import com.kex.vikrsaathi.ui.finance.FinanceUi
import com.kex.vikrsaathi.util.PriceCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class BillVersionAdapter(
    private val currencySymbol: String,
    private val onSelect: (Int) -> Unit,
    private val onEditCurrent: () -> Unit,
    private val onRestore: (BillVersionDetails) -> Unit,
    private val onPrint: (BillVersionDetails) -> Unit,
    private val onDuplicate: (BillVersionDetails) -> Unit
) : RecyclerView.Adapter<BillVersionAdapter.Holder>() {

    private val whenFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
    private var versions: List<BillVersionDetails> = emptyList()
    private var selectedVersionNumber: Int? = null

    fun submit(versions: List<BillVersionDetails>, selectedVersionNumber: Int?) {
        this.versions = versions
        this.selectedVersionNumber = selectedVersionNumber
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemBillVersionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = versions.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(versions[position], position == versions.lastIndex)
    }

    inner class Holder(
        private val binding: ItemBillVersionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(details: BillVersionDetails, isLast: Boolean) {
            val version = details.version
            val context = binding.root.context
            val selected = version.versionNumber == selectedVersionNumber
            val money = { amount: Double -> PriceCalculator.formatAmount(amount, currencySymbol) }

            binding.textVersionTitle.text = if (details.isCurrent) {
                context.getString(R.string.version_current, version.versionNumber)
            } else {
                context.getString(R.string.bill_version_label, version.versionNumber)
            }
            binding.textVersionTotal.text = context.getString(R.string.version_total, money(version.total))
            binding.textVersionWhen.text = whenFormat.format(Date(version.createdAt))
            binding.textVersionActor.text = context.getString(R.string.version_actor, version.actorLabel)
            binding.textVersionSummary.text = version.changeSummary
            binding.rowReadOnly.isVisible = !details.isCurrent
            binding.buttonEditCurrent.isVisible = details.isCurrent
            binding.buttonEditCurrent.setOnClickListener { onEditCurrent() }
            binding.viewVersionLine.isVisible = !isLast

            val dotColor = context.getColor(
                if (details.isCurrent) R.color.orange_primary else R.color.stroke_button
            )
            binding.viewVersionDot.backgroundTintList = ColorStateList.valueOf(dotColor)
            binding.cardVersion.strokeWidth = if (details.isCurrent || selected) {
                (2 * context.resources.displayMetrics.density).toInt()
            } else {
                (1 * context.resources.displayMetrics.density).toInt()
            }
            binding.cardVersion.strokeColor = context.getColor(
                if (details.isCurrent || selected) R.color.orange_primary else R.color.stroke_subtle
            )
            binding.cardVersion.setOnClickListener { onSelect(version.versionNumber) }

            binding.layoutVersionDetail.isVisible = selected
            if (!selected) return

            binding.textVersionDetailTitle.text = if (details.isCurrent) {
                context.getString(R.string.version_details_current, version.versionNumber)
            } else {
                context.getString(R.string.version_details, version.versionNumber)
            }
            binding.textSaleDate.text = context.getString(
                R.string.version_sale_date,
                whenFormat.format(Date(version.saleDate))
            )
            binding.textCustomer.text = version.customerName
            val meta = listOf(version.customerPhone, version.customerAddress)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            binding.textCustomerMeta.isVisible = meta.isNotBlank()
            binding.textCustomerMeta.text = meta

            binding.layoutLines.removeAllViews()
            val inflater = LayoutInflater.from(context)
            details.items.forEach { item ->
                val line = ItemBillVersionLineBinding.inflate(inflater, binding.layoutLines, true)
                bindLine(line, item, money)
            }

            val subtotal = details.items.sumOf { it.finalPrice * it.quantity + it.roundOff }
            val discount = details.items.sumOf { (it.mrp - it.finalPrice) * it.quantity }
            binding.textSubtotal.text = context.getString(R.string.subtotal, money(subtotal))
            binding.textDiscount.isVisible = abs(discount) >= 0.005
            binding.textDiscount.text = context.getString(R.string.version_discount, money(discount))
            binding.textRoundOff.isVisible = abs(version.roundOff) >= 0.005
            binding.textRoundOff.text = context.getString(
                R.string.bill_round_off_label,
                money(version.roundOff)
            )
            binding.textDetailTotal.text = context.getString(R.string.grand_total, money(version.total))
            binding.textPayment.text = paymentText(details.payments, money, context)

            val historical = !details.isCurrent
            binding.rowVersionActions.isVisible = historical
            binding.textRestoreHint.isVisible = historical
            if (historical) {
                val newest = versions.maxOf { it.version.versionNumber }
                binding.textRestoreHint.text = context.getString(
                    R.string.version_restore_hint,
                    newest + 1,
                    newest
                )
                binding.buttonRestoreVersion.setOnClickListener { onRestore(details) }
                binding.buttonPrintVersion.setOnClickListener { onPrint(details) }
                binding.buttonDuplicateVersion.setOnClickListener { onDuplicate(details) }
            }
        }

        private fun bindLine(
            line: ItemBillVersionLineBinding,
            item: BillVersionItem,
            money: (Double) -> String
        ) {
            val amount = item.finalPrice * item.quantity + item.roundOff
            line.textLineName.text = item.itemName
            line.textLineQty.text = line.root.context.getString(
                R.string.version_line_qty,
                item.quantity,
                money(item.finalPrice)
            )
            line.textLineAmount.text = money(amount)
            val notes = buildList {
                val warranty = item.warrantySnapshot()
                if (warranty != null) {
                    add(WarrantyPolicy.headline(warranty))
                    WarrantyPolicy.validUntil(warranty)?.let { add(it) }
                }
                if (item.discount > 0.001) add("Disc ${trimNumber(item.discount)}%")
                if (abs(item.roundOff) >= 0.005) {
                    add("R/off ${money(item.roundOff)}")
                }
            }
            line.textLineMeta.isVisible = notes.isNotEmpty()
            line.textLineMeta.text = notes.joinToString(" · ")
        }

        private fun paymentText(
            payments: List<BillVersionPayment>,
            money: (Double) -> String,
            context: android.content.Context
        ): String {
            if (payments.isEmpty()) return context.getString(R.string.payment_summary_legacy)
            return payments.joinToString("\n") { portion ->
                val label = FinanceUi.paymentLabel(context, portion.method)
                "$label ${money(portion.amountPaise / 100.0)}"
            }
        }

        private fun trimNumber(value: Double): String {
            val nearest = value.toInt()
            return if (abs(value - nearest) < 0.001) {
                nearest.toString()
            } else {
                String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
            }
        }
    }
}
