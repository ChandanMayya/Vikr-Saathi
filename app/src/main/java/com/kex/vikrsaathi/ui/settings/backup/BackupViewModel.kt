package com.kex.vikrsaathi.ui.settings.backup

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.backup.BackupExportOptions
import com.kex.vikrsaathi.data.backup.BackupImportResult
import com.kex.vikrsaathi.data.backup.BackupManager
import com.kex.vikrsaathi.data.backup.BackupManifest
import com.kex.vikrsaathi.util.BackupSaveResult
import kotlinx.coroutines.launch

class BackupViewModel(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _isWorking = MutableLiveData(false)
    val isWorking: LiveData<Boolean> = _isWorking

    private val _progressMessage = MutableLiveData("")
    val progressMessage: LiveData<String> = _progressMessage

    private val _progressPercent = MutableLiveData(0)
    val progressPercent: LiveData<Int> = _progressPercent

    private val _exportComplete = MutableLiveData<BackupSaveResult?>(null)
    val exportComplete: LiveData<BackupSaveResult?> = _exportComplete

    private val _importManifest = MutableLiveData<BackupManifest?>(null)
    val importManifest: LiveData<BackupManifest?> = _importManifest

    private val _importResult = MutableLiveData<BackupImportResult?>(null)
    val importResult: LiveData<BackupImportResult?> = _importResult

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private var pendingImportJson: String? = null

    fun clearExportResult() {
        _exportComplete.value = null
    }

    fun clearImportManifest() {
        _importManifest.value = null
        pendingImportJson = null
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun exportBackup(options: BackupExportOptions) {
        if (_isWorking.value == true) return
        if (!options.hasAnySelected()) {
            _errorMessage.value = "Select at least one category to export."
            return
        }
        viewModelScope.launch {
            _isWorking.value = true
            _exportComplete.value = null
            try {
                val result = backupManager.exportBackup(options) { message, percent ->
                    _progressMessage.postValue(message)
                    _progressPercent.postValue(percent)
                }
                _exportComplete.value = result
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Export failed"
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun loadImportPreview(uri: Uri) {
        if (_isWorking.value == true) return
        viewModelScope.launch {
            _isWorking.value = true
            _importManifest.value = null
            pendingImportJson = null
            try {
                _progressMessage.value = "Reading backup file…"
                _progressPercent.value = 10
                val manifest = backupManager.readManifest(uri)
                val json = backupManager.readBackupJson(uri)
                pendingImportJson = json
                _importManifest.value = manifest
                _progressPercent.value = 100
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not read backup file"
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun confirmImport() {
        val json = pendingImportJson
        if (json.isNullOrBlank() || _isWorking.value == true) return
        viewModelScope.launch {
            _isWorking.value = true
            _importResult.value = null
            try {
                val result = backupManager.importBackup(json) { message, percent ->
                    _progressMessage.postValue(message)
                    _progressPercent.postValue(percent)
                }
                _importResult.value = result
                pendingImportJson = null
                _importManifest.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Import failed"
            } finally {
                _isWorking.value = false
            }
        }
    }
}
