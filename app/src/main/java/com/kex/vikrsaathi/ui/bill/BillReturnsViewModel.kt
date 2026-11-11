package com.kex.vikrsaathi.ui.bill

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.entity.SalesReturn
import com.kex.vikrsaathi.data.entity.SalesReturnLine
import com.kex.vikrsaathi.data.repository.BillRepository
import com.kex.vikrsaathi.data.repository.CustomerFinanceRepository
import com.kex.vikrsaathi.data.repository.OperatorRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.domain.finance.RefundMethod
import com.kex.vikrsaathi.domain.money.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

data class BillReturnRow(
    val record: SalesReturn,
    val lines: List<SalesReturnLine>,
    val replacementNumber: String?
)

class BillReturnsViewModel(
    private val finance: CustomerFinanceRepository,
    private val bills: BillRepository,
    private val settings: SettingsRepository,
    private val operators: OperatorRepository
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
    private val _rows = MutableLiveData<List<BillReturnRow>>(emptyList())
    val rows: LiveData<List<BillReturnRow>> = _rows
    private val _header = MutableLiveData("")
    val header: LiveData<String> = _header
    private var billId: Long = -1L

    val currencySymbol: String get() = settings.currencySymbol

    fun load(id: Long) {
        billId = id
        viewModelScope.launch {
            val bill = bills.getBillWithDetails(id)?.bill
            _header.value = bill?.billNumber.orEmpty()
            val records = finance.returnsForBill(id)
            _rows.value = records.map { record ->
                val replacement = record.exchangeBillId?.let { bills.getBillWithDetails(it)?.bill?.billNumber }
                BillReturnRow(record, finance.linesForReturn(record.id), replacement)
            }
        }
    }

    fun formatWhen(millis: Long): String = dateFormat.format(Date(millis))

    fun money(paise: Long): String = com.kex.vikrsaathi.util.PriceCalculator.formatAmount(
        Money.paiseToRupees(paise),
        currencySymbol
    )

    fun void(returnId: Long, reason: String, onDone: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                finance.voidSalesReturn(returnId, reason, System.currentTimeMillis(), operators.actorLabel())
                load(billId)
                onDone()
            } catch (error: Throwable) {
                onError(error)
            }
        }
    }

    fun settlementLabel(record: SalesReturn): String {
        val method = when (record.refundMethod) {
            RefundMethod.CASH -> "Cash"
            RefundMethod.UPI -> "UPI"
            RefundMethod.STORE_CREDIT -> "Store credit"
            else -> ""
        }
        return method
    }
}
