package com.kex.vikrsaathi.ui.warranty

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.entity.WarrantyClaim
import com.kex.vikrsaathi.data.entity.WarrantyClaimEvent
import com.kex.vikrsaathi.data.model.WarrantySaleLine
import com.kex.vikrsaathi.data.repository.SettingsRepository
import com.kex.vikrsaathi.data.repository.WarrantyClaimOutcome
import com.kex.vikrsaathi.data.repository.WarrantyClaimRepository
import com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy
import kotlinx.coroutines.launch

class WarrantyServiceViewModel(
    private val repository: WarrantyClaimRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _lines = MutableLiveData<List<WarrantySaleLine>>(emptyList())
    val lines: LiveData<List<WarrantySaleLine>> = _lines

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _claims = MutableLiveData<List<WarrantyClaim>>(emptyList())
    val claims: LiveData<List<WarrantyClaim>> = _claims

    private val _selectedLine = MutableLiveData<WarrantySaleLine?>()
    val selectedLine: LiveData<WarrantySaleLine?> = _selectedLine

    private val _claim = MutableLiveData<WarrantyClaim?>()
    val claim: LiveData<WarrantyClaim?> = _claim

    private val _events = MutableLiveData<List<WarrantyClaimEvent>>(emptyList())
    val events: LiveData<List<WarrantyClaimEvent>> = _events

    private val _billExists = MutableLiveData(false)
    val billExists: LiveData<Boolean> = _billExists

    private var searchToken = 0
    private var lineToken = 0
    private var claimToken = 0
    private var saving = false

    private val _lineLoaded = MutableLiveData(false)
    val lineLoaded: LiveData<Boolean> = _lineLoaded

    private val _claimLoaded = MutableLiveData(false)
    val claimLoaded: LiveData<Boolean> = _claimLoaded

    val shopName: String
        get() = settingsRepository.shopName

    fun search(query: String) {
        val token = ++searchToken
        viewModelScope.launch {
            _loading.value = true
            val found = repository.search(query)
            if (token == searchToken) {
                _loading.value = false
                _lines.value = found
            }
        }
    }

    fun refreshClaims() {
        viewModelScope.launch { _claims.value = repository.history() }
    }

    fun loadLine(billItemId: Long) {
        val token = ++lineToken
        _lineLoaded.value = false
        viewModelScope.launch {
            val line = repository.saleLine(billItemId)
            if (token != lineToken) return@launch
            _selectedLine.value = line
            _lineLoaded.value = true
        }
    }

    fun saveClaim(
        billItemId: Long,
        issue: String,
        condition: String,
        note: String,
        onResult: (WarrantyClaimOutcome) -> Unit
    ) {
        if (saving) {
            onResult(WarrantyClaimOutcome.NotAllowed(WarrantyClaimPolicy.SAVE_IN_PROGRESS))
            return
        }
        saving = true
        viewModelScope.launch {
            val outcome = repository.createClaim(billItemId, issue, condition, note)
            saving = false
            onResult(outcome)
        }
    }

    fun loadClaim(claimId: Long) {
        val token = ++claimToken
        _claimLoaded.value = false
        viewModelScope.launch {
            val stored = repository.claim(claimId)
            if (token != claimToken) return@launch
            _claim.value = stored
            _events.value = if (stored == null) emptyList() else repository.events(claimId)
            _billExists.value = repository.billStillExists(stored?.billId)
            _claimLoaded.value = true
        }
    }

    fun updateStatus(
        claimId: Long,
        status: String,
        resolutionNote: String,
        onResult: (WarrantyClaimOutcome) -> Unit
    ) {
        runClaimChange(onResult) { repository.updateStatus(claimId, status, resolutionNote) }
    }

    fun recordServiceCentre(
        claimId: Long,
        centre: String,
        reference: String,
        sentEpochDay: Long?,
        onResult: (WarrantyClaimOutcome) -> Unit
    ) {
        runClaimChange(onResult) {
            repository.recordServiceCentre(claimId, centre, reference, sentEpochDay)
        }
    }

    fun saveProviderOutcome(
        claimId: Long,
        outcome: String,
        note: String,
        responseEpochDay: Long?,
        onResult: (WarrantyClaimOutcome) -> Unit
    ) {
        runClaimChange(onResult) {
            repository.saveProviderOutcome(claimId, outcome, note, responseEpochDay)
        }
    }

    fun saveHandover(
        claimId: Long,
        returnedEpochDay: Long?,
        recipient: String,
        note: String,
        onResult: (WarrantyClaimOutcome) -> Unit
    ) {
        runClaimChange(onResult) {
            repository.saveHandover(claimId, returnedEpochDay, recipient, note)
        }
    }

    private fun runClaimChange(
        onResult: (WarrantyClaimOutcome) -> Unit,
        action: suspend () -> WarrantyClaimOutcome
    ) {
        if (saving) {
            onResult(WarrantyClaimOutcome.NotAllowed(WarrantyClaimPolicy.SAVE_IN_PROGRESS))
            return
        }
        saving = true
        viewModelScope.launch {
            val outcome = action()
            saving = false
            if (outcome is WarrantyClaimOutcome.Saved) {
                loadClaim(outcome.claimId)
            }
            onResult(outcome)
        }
    }
}
