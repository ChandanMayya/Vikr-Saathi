package com.kex.vikrsaathi.ui.finance

import android.widget.LinearLayout
import androidx.core.util.Pair
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.kex.vikrsaathi.R
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object FinanceDates {

    private val format = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())

    fun format(millis: Long): String = format.format(Date(millis))

    fun todayStart(): Long = localStartFromPicker(MaterialDatePicker.todayInUtcMilliseconds())

    fun showRange(fragment: Fragment, onPicked: (startInclusive: Long, endInclusive: Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.ledger_dates)
            .build()
        picker.addOnPositiveButtonClickListener { selection: Pair<Long, Long> ->
            onPicked(localStartFromPicker(selection.first), localEndFromPicker(selection.second))
        }
        picker.show(fragment.parentFragmentManager, TAG_RANGE)
    }

    fun showSingle(fragment: Fragment, titleRes: Int, onPicked: (localStart: Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(titleRes)
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { onPicked(localStartFromPicker(it)) }
        picker.show(fragment.parentFragmentManager, TAG_SINGLE)
    }

    class LedgerRange(
        private val fragment: Fragment,
        private val button: MaterialButton,
        private val clear: MaterialButton,
        private val onChanged: () -> Unit
    ) {
        var from: Long? = null
        var to: Long? = null

        init {
            button.setOnClickListener {
                showRange(fragment) { start, end ->
                    from = start
                    to = end
                    refreshLabel()
                    onChanged()
                }
            }
            clear.setOnClickListener {
                from = null
                to = null
                refreshLabel()
                onChanged()
            }
            refreshLabel()
        }

        private fun refreshLabel() {
            val start = from
            val end = to
            button.text = if (start == null || end == null) {
                fragment.getString(R.string.ledger_all_dates)
            } else {
                fragment.getString(R.string.ledger_range, format(start), format(end))
            }
            clear.isVisible = start != null
        }
    }

    class DateField(private val fragment: Fragment) {
        var millis: Long = todayStart()
        val view: MaterialButton = MaterialButton(
            fragment.requireContext(),
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = label()
            setOnClickListener {
                showSingle(fragment, R.string.date_label) { picked ->
                    millis = picked
                    text = label()
                }
            }
        }

        private fun label(): String = fragment.getString(R.string.date_picked, format(millis))
    }

    private fun localStartFromPicker(utcMidnight: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun localEndFromPicker(utcMidnight: Long): Long {
        val start = localStartFromPicker(utcMidnight)
        return Calendar.getInstance().apply {
            timeInMillis = start
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    private const val TAG_RANGE = "finance_date_range"
    private const val TAG_SINGLE = "finance_date"
}
