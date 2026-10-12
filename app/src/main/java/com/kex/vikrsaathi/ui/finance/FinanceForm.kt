package com.kex.vikrsaathi.ui.finance

import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kex.vikrsaathi.data.entity.Item

object FinanceForm {

    fun column(fragment: Fragment): LinearLayout {
        return LinearLayout(fragment.requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
    }

    fun text(
        fragment: Fragment,
        parent: LinearLayout,
        hint: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        initial: String = ""
    ): TextInputEditText {
        val layout = outline(fragment, hint)
        val edit = TextInputEditText(layout.context).apply {
            this.inputType = inputType
            setText(initial)
        }
        layout.addView(edit, matchWidth())
        parent.addView(layout, spaced(fragment))
        return edit
    }

    fun picker(
        fragment: Fragment,
        parent: LinearLayout,
        hint: String,
        labels: List<String>,
        onPick: (Int) -> Unit
    ): MaterialAutoCompleteTextView {
        val layout = TextInputLayout(
            fragment.requireContext(),
            null,
            com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle
        ).apply { this.hint = hint }
        val input = MaterialAutoCompleteTextView(layout.context).apply {
            inputType = InputType.TYPE_NULL
            setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, labels))
            setOnItemClickListener { _, _, position, _ -> onPick(position) }
        }
        layout.addView(input, matchWidth())
        parent.addView(layout, spaced(fragment))
        return input
    }

    fun itemPicker(
        fragment: Fragment,
        parent: LinearLayout,
        hint: String,
        items: List<Item>,
        onPick: (Item) -> Unit
    ): MaterialAutoCompleteTextView {
        return picker(fragment, parent, hint, items.map { it.name }) { index ->
            onPick(items[index])
        }
    }

    private fun outline(fragment: Fragment, hint: String): TextInputLayout {
        return TextInputLayout(
            fragment.requireContext(),
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply { this.hint = hint }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun spaced(fragment: Fragment): LinearLayout.LayoutParams {
        val margin = (8 * fragment.resources.displayMetrics.density).toInt()
        return matchWidth().apply { topMargin = margin }
    }
}
