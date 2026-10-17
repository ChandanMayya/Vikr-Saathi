package com.kex.vikrsaathi.ui.finance

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.domain.finance.PackMath

class PackFields(
    val unit: TextInputEditText,
    val factor: TextInputEditText,
    val preview: TextView
)

object PackForm {

    fun add(fragment: Fragment, parent: LinearLayout, unitName: String = "", factor: Int = 1): PackFields {
        val unit = FinanceForm.text(fragment, parent, fragment.getString(R.string.pack_unit), initial = unitName)
        val factorField = FinanceForm.text(
            fragment,
            parent,
            fragment.getString(R.string.pack_factor),
            InputType.TYPE_CLASS_NUMBER,
            factor.coerceAtLeast(1).toString()
        )
        val preview = TextView(fragment.requireContext()).apply {
            setPadding(0, 8, 0, 0)
        }
        parent.addView(preview, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return PackFields(unit, factorField, preview)
    }

    fun apply(fields: PackFields, unitName: String, factor: Int, locked: Boolean) {
        fields.unit.setText(unitName)
        fields.factor.setText(factor.coerceAtLeast(1).toString())
        fields.unit.isEnabled = !locked
        fields.factor.isEnabled = !locked
    }

    fun prefill(fields: PackFields, item: Item?) {
        if (item != null && item.purchaseUnit.isNotBlank() && item.unitsPerPurchase > 0) {
            apply(fields, item.purchaseUnit, item.unitsPerPurchase, locked = false)
        } else {
            apply(fields, item?.stockUnit().orEmpty(), 1, locked = false)
        }
    }

    fun read(fields: PackFields): Pair<String, Int>? {
        val factor = fields.factor.text?.toString()?.toIntOrNull() ?: return null
        if (PackMath.validateTransaction(fields.unit.text?.toString().orEmpty().trim(), factor) != null) return null
        return fields.unit.text?.toString().orEmpty().trim() to factor
    }

    fun watch(fields: PackFields, sources: List<TextInputEditText>, onChange: () -> Unit) {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onChange()
        }
        (sources + fields.unit + fields.factor).forEach { it.addTextChangedListener(watcher) }
        onChange()
    }

    fun setRateHint(rate: TextInputEditText, unitName: String) {
        val layout = rate.parent as? TextInputLayout ?: return
        val unit = unitName.trim()
        layout.hint = if (unit.isBlank()) {
            rate.context.getString(R.string.rate_label)
        } else {
            rate.context.getString(R.string.pack_rate_per, unit)
        }
    }
}
