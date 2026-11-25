package com.example.ui.screens.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.StorageLocationEntity
import com.example.data.repository.BarcodeLookupResult
import com.example.data.repository.BarcodeRepository
import com.example.data.repository.InventoryOperationResult
import com.example.data.repository.NewBatchInput
import com.example.data.repository.PantryRepository
import com.example.data.repository.SettingsRepository
import com.example.data.worker.WorkScheduler
import com.example.domain.model.ExpiryType
import com.example.domain.model.MovementType
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.ExpiryDateOcrParser
import com.example.domain.usecase.OcrDateConsensusTracker
import com.example.domain.usecase.OcrLineCandidate
import com.example.domain.usecase.PantryDateUtils
import com.example.domain.usecase.ShelfLifeEstimate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OcrCaptureStatus {
    data object Searching : OcrCaptureStatus
    data class Detected(
        val expiryDateMillis: Long,
        val expiryType: ExpiryType,
        val matchedText: String,
        val fromHistoricalEstimate: Boolean = false
    ) : OcrCaptureStatus
}

sealed interface BarcodeLookupAsyncState {
    data object Loading : BarcodeLookupAsyncState
    data class Ready(
        val result: BarcodeLookupResult,
        val shelfLifeEstimate: ShelfLifeEstimate? = null
    ) : BarcodeLookupAsyncState
}

sealed interface ScannerUiState {
    data object ScanningBarcode : ScannerUiState

    data class CapturingExpiry(
        val barcode: String,
        val lookupState: BarcodeLookupAsyncState = BarcodeLookupAsyncState.Loading,
        val ocrStatus: OcrCaptureStatus = OcrCaptureStatus.Searching,
        val isAutoCapturePaused: Boolean = false,
        val pendingConfirmAfterLookup: Boolean = false,
        val pendingExpiryMillis: Long? = null,
        val pendingExpiryType: ExpiryType = ExpiryType.USE_BY,
        val pendingSourceLabel: String? = null
    ) : ScannerUiState

    data class Result(
        val lookup: BarcodeLookupResult,
        val capturedExpiryMillis: Long? = null,
        val capturedExpiryType: ExpiryType = ExpiryType.USE_BY,
        val expirySourceLabel: String? = null,
        val shelfLifeEstimate: ShelfLifeEstimate? = null
    ) : ScannerUiState
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val barcodeRepository: BarcodeRepository,
    private val pantryRepository: PantryRepository,
    private val settingsRepository: SettingsRepository,
    private val workScheduler: WorkScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.ScanningBarcode)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val locations: StateFlow<List<StorageLocationEntity>> = settingsRepository.observeLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val consensusTracker = OcrDateConsensusTracker()
    private var lookupJob: Job? = null

    fun onBarcodeDetected(barcode: String, cameraAvailable: Boolean = true) {
        val current = _uiState.value
        if (current !is ScannerUiState.ScanningBarcode) return
        val cleaned = barcode.trim()
        if (cleaned.isEmpty()) return

        consensusTracker.reset()
        lookupJob?.cancel()

        if (!cameraAvailable) {
            // If camera is unavailable, run lookup directly and go to Result.
            _uiState.value = ScannerUiState.CapturingExpiry(
                barcode = cleaned,
                lookupState = BarcodeLookupAsyncState.Loading,
                ocrStatus = OcrCaptureStatus.Searching,
                isAutoCapturePaused = true,
                pendingConfirmAfterLookup = true,
                pendingExpiryMillis = null,
                pendingExpiryType = ExpiryType.USE_BY,
                pendingSourceLabel = null
            )
            launchBarcodeLookup(cleaned)
            return
        }

        _uiState.value = ScannerUiState.CapturingExpiry(
            barcode = cleaned,
            lookupState = BarcodeLookupAsyncState.Loading,
            ocrStatus = OcrCaptureStatus.Searching,
            isAutoCapturePaused = false
        )
        launchBarcodeLookup(cleaned)
    }

    private fun launchBarcodeLookup(barcode: String) {
        lookupJob = viewModelScope.launch {
            val result = barcodeRepository.lookupBarcode(barcode)
            val estimate = if (result is BarcodeLookupResult.LocalProduct) {
                pantryRepository.estimateShelfLifeForProduct(result.product.id)
            } else {
                null
            }

            val latestState = _uiState.value
            if (latestState is ScannerUiState.CapturingExpiry && latestState.barcode == barcode) {
                if (latestState.pendingConfirmAfterLookup) {
                    _uiState.value = ScannerUiState.Result(
                        lookup = result,
                        capturedExpiryMillis = latestState.pendingExpiryMillis,
                        capturedExpiryType = latestState.pendingExpiryType,
                        expirySourceLabel = latestState.pendingSourceLabel,
                        shelfLifeEstimate = estimate
                    )
                } else {
                    _uiState.value = latestState.copy(
                        lookupState = BarcodeLookupAsyncState.Ready(
                            result = result,
                            shelfLifeEstimate = estimate
                        )
                    )
                }
            }
        }
    }

    fun onOcrLinesDetected(
        lines: List<OcrLineCandidate>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val state = _uiState.value as? ScannerUiState.CapturingExpiry ?: return
        if (state.isAutoCapturePaused) return

        val candidate = ExpiryDateOcrParser.parseBestCandidate(
            lines = lines,
            ignoredBarcode = state.barcode
        )
        val locked = consensusTracker.submit(candidate, nowMillis = nowMillis) ?: return

        _uiState.value = state.copy(
            ocrStatus = OcrCaptureStatus.Detected(
                expiryDateMillis = locked.expiryDateMillis,
                expiryType = locked.expiryType,
                matchedText = locked.matchedText,
                fromHistoricalEstimate = false
            ),
            isAutoCapturePaused = true
        )
    }

    fun updateCapturedExpiryType(expiryType: ExpiryType) {
        val state = _uiState.value as? ScannerUiState.CapturingExpiry ?: return
        val detected = state.ocrStatus as? OcrCaptureStatus.Detected ?: return
        _uiState.value = state.copy(
            ocrStatus = detected.copy(expiryType = expiryType)
        )
    }

    fun setManualExpiryInCapture(expiryDateMillis: Long) {
        val state = _uiState.value as? ScannerUiState.CapturingExpiry ?: return
        val currentType = (state.ocrStatus as? OcrCaptureStatus.Detected)?.expiryType
            ?: ExpiryType.USE_BY
        _uiState.value = state.copy(
            ocrStatus = OcrCaptureStatus.Detected(
                expiryDateMillis = expiryDateMillis,
                expiryType = currentType,
                matchedText = "Selezione manuale",
                fromHistoricalEstimate = false
            ),
            isAutoCapturePaused = true
        )
    }

    fun retryOcrCapture() {
        val state = _uiState.value as? ScannerUiState.CapturingExpiry ?: return
        consensusTracker.reset()
        _uiState.value = state.copy(
            ocrStatus = OcrCaptureStatus.Searching,
            isAutoCapturePaused = false,
            pendingConfirmAfterLookup = false
        )
    }

    fun confirmCapturedExpiry() {
        val state = _uiState.value as? ScannerUiState.CapturingExpiry ?: return
        val detected = state.ocrStatus as? OcrCaptureStatus.Detected ?: return
        val sourceLabel = if (detected.fromHistoricalEstimate) {
            detected.matchedText
        } else {
            "Da scansione OCR"
        }
        proceedToResult(
            state = state,
            expiryMillis = detected.expiryDateMillis,
            expiryType = detected.expiryType,
            sourceLabel = sourceLabel
        )
    }

    fun applyHistoricalEstimateAndConfirm() {
        val state = _uiState.value as? ScannerUiState.CapturingExpiry ?: return
        val ready = state.lookupState as? BarcodeLookupAsyncState.Ready ?: return
        val estimate = ready.shelfLifeEstimate ?: return
        proceedToResult(
            state = state,
            expiryMillis = estimate.estimatedExpiryMillis,
            expiryType = estimate.dominantExpiryType,
            sourceLabel = "Stima storica (~${estimate.averageDays} gg)"
        )
    }

    fun skipExpiryCapture() {
        val state = _uiState.value as? ScannerUiState.CapturingExpiry ?: return
        proceedToResult(
            state = state,
            expiryMillis = null,
            expiryType = ExpiryType.USE_BY,
            sourceLabel = null
        )
    }

    private fun proceedToResult(
        state: ScannerUiState.CapturingExpiry,
        expiryMillis: Long?,
        expiryType: ExpiryType,
        sourceLabel: String?
    ) {
        when (val lookup = state.lookupState) {
            is BarcodeLookupAsyncState.Ready -> {
                _uiState.value = ScannerUiState.Result(
                    lookup = lookup.result,
                    capturedExpiryMillis = expiryMillis,
                    capturedExpiryType = expiryType,
                    expirySourceLabel = sourceLabel,
                    shelfLifeEstimate = lookup.shelfLifeEstimate
                )
            }
            BarcodeLookupAsyncState.Loading -> {
                _uiState.value = state.copy(
                    isAutoCapturePaused = true,
                    pendingConfirmAfterLookup = true,
                    pendingExpiryMillis = expiryMillis,
                    pendingExpiryType = expiryType,
                    pendingSourceLabel = sourceLabel
                )
            }
        }
    }

    fun resumeScanning() {
        lookupJob?.cancel()
        consensusTracker.reset()
        _uiState.value = ScannerUiState.ScanningBarcode
    }

    fun addBatchToLocalProduct(
        productId: String,
        unit: UnitOfMeasure,
        quantity: Double,
        expiryDate: Long?,
        expiryType: ExpiryType,
        locationId: String,
        price: Double?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = pantryRepository.addBatch(
                productId = productId,
                input = NewBatchInput(
                    quantity = quantity,
                    unit = unit,
                    purchaseDate = PantryDateUtils.todayStartMillis(),
                    expiryDate = expiryDate,
                    expiryType = expiryType,
                    locationId = locationId,
                    purchasePrice = price,
                    movementType = MovementType.PURCHASE
                )
            )
            when (res) {
                InventoryOperationResult.Success -> {
                    workScheduler.triggerImmediateExpiryCheck()
                    _message.value = "Nuovo lotto aggiunto al prodotto esistente."
                    onSuccess()
                }
                is InventoryOperationResult.Error -> {
                    _message.value = res.message
                }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
