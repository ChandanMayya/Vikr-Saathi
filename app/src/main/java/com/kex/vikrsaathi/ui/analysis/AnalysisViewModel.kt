package com.kex.vikrsaathi.ui.analysis

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.model.analytics.AnalyticsDashboard
import com.kex.vikrsaathi.data.repository.AnalyticsRepository
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.util.AnalyticsDateRange
import com.kex.vikrsaathi.util.AnalyticsReportExcelExporter
import com.kex.vikrsaathi.util.AnalyticsReportPdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AnalysisViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _dateRange = MutableLiveData(AnalyticsDateRange.LAST_7_DAYS)
    val dateRange: LiveData<AnalyticsDateRange> = _dateRange

    private val _currencySymbol = MutableLiveData("₹")
    val currencySymbol: LiveData<String> = _currencySymbol

    private val _dashboard = MutableLiveData(AnalyticsDashboard.EMPTY)
    val dashboard: LiveData<AnalyticsDashboard> = _dashboard

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    init {
        refresh()
    }

    fun setDateRange(range: AnalyticsDateRange) {
        if (_dateRange.value == range) return
        _dateRange.value = range
        refresh()
    }

    fun refresh() {
        val range = _dateRange.value ?: AnalyticsDateRange.LAST_7_DAYS
        viewModelScope.launch {
            _loading.value = true
            _currencySymbol.value = settingsRepository.currencySymbol
            _dashboard.value = analyticsRepository.loadDashboard(range)
            _loading.value = false
        }
    }

    fun exportPdf(context: Context, rangeLabel: String, onResult: (File?) -> Unit) {
        val dashboard = _dashboard.value ?: AnalyticsDashboard.EMPTY
        if (dashboard.summary.billCount == 0 && dashboard.inventoryValue.itemCount == 0) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                AnalyticsReportPdfGenerator.generate(
                    context = context,
                    dashboard = dashboard,
                    shopName = settingsRepository.shopName,
                    rangeLabel = rangeLabel,
                    currencySymbol = settingsRepository.currencySymbol
                )
            }
            onResult(file)
        }
    }

    fun exportExcel(context: Context, rangeLabel: String, onResult: (File?) -> Unit) {
        val dashboard = _dashboard.value ?: AnalyticsDashboard.EMPTY
        if (dashboard.summary.billCount == 0 && dashboard.inventoryValue.itemCount == 0) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                AnalyticsReportExcelExporter.export(
                    context = context,
                    dashboard = dashboard,
                    rangeLabel = rangeLabel,
                    currencySymbol = settingsRepository.currencySymbol
                )
            }
            onResult(file)
        }
    }
}
