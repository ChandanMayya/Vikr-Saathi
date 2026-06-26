package com.kex.vikrsaathi.ui.settings.reset

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.backup.BackupImportResult
import com.kex.vikrsaathi.data.reset.ResetHistoryEntry
import com.kex.vikrsaathi.data.reset.ResetManager
import com.kex.vikrsaathi.data.reset.ResetOptions
import com.kex.vikrsaathi.data.reset.ResetResult
import kotlinx.coroutines.launch

class ResetViewModel(
    private val resetManager: ResetManager
) : ViewModel() {

    private val _isWorking = MutableLiveData(false)
    val isWorking: LiveData<Boolean> = _isWorking

    private val _progressMessage = MutableLiveData("")
    val progressMessage: LiveData<String> = _progressMessage

    private val _progressPercent = MutableLiveData(0)
    val progressPercent: LiveData<Int> = _progressPercent

    private val _resetComplete = MutableLiveData<ResetResult?>(null)
    val resetComplete: LiveData<ResetResult?> = _resetComplete

    private val _restoreComplete = MutableLiveData<BackupImportResult?>(null)
    val restoreComplete: LiveData<BackupImportResult?> = _restoreComplete

    private val _historyEntries = MutableLiveData<List<ResetHistoryEntry>>(emptyList())
    val historyEntries: LiveData<List<ResetHistoryEntry>> = _historyEntries

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    fun clearResetResult() {
        _resetComplete.value = null
    }

    fun clearRestoreResult() {
        _restoreComplete.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun refreshHistory() {
        _historyEntries.value = resetManager.loadHistory()
    }

    fun performReset(options: ResetOptions) {
        if (_isWorking.value == true) return
        if (!options.hasAnySelected()) {
            _errorMessage.value = "Select at least one category to reset."
            return
        }
        viewModelScope.launch {
            _isWorking.value = true
            _resetComplete.value = null
            try {
                val result = resetManager.performReset(options) { message, percent ->
                    _progressMessage.postValue(message)
                    _progressPercent.postValue(percent)
                }
                _resetComplete.value = result
                refreshHistory()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Reset failed"
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun restoreFromHistory(entryId: String) {
        if (_isWorking.value == true) return
        viewModelScope.launch {
            _isWorking.value = true
            _restoreComplete.value = null
            try {
                val result = resetManager.restoreFromHistory(entryId) { message, percent ->
                    _progressMessage.postValue(message)
                    _progressPercent.postValue(percent)
                }
                _restoreComplete.value = result
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Restore failed"
            } finally {
                _isWorking.value = false
            }
        }
    }
}
