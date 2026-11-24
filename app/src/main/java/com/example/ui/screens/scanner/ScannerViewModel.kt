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
import com.example.domain.usecase.PantryDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScannerUiState {
    data object Scanning : ScannerUiState
    data class Loading(val barcode: String) : ScannerUiState
    data class Result(val lookup: BarcodeLookupResult) : ScannerUiState
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val barcodeRepository: BarcodeRepository,
    private val pantryRepository: PantryRepository,
    private val settingsRepository: SettingsRepository,
    private val workScheduler: WorkScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Scanning)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val locations: StateFlow<List<StorageLocationEntity>> = settingsRepository.observeLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onBarcodeDetected(barcode: String) {
        val current = _uiState.value
        if (current !is ScannerUiState.Scanning) return
        val cleaned = barcode.trim()
        if (cleaned.isEmpty()) return

        _uiState.value = ScannerUiState.Loading(cleaned)
        viewModelScope.launch {
            val result = barcodeRepository.lookupBarcode(cleaned)
            _uiState.value = ScannerUiState.Result(result)
        }
    }

    fun resumeScanning() {
        _uiState.value = ScannerUiState.Scanning
    }

    fun addBatchToLocalProduct(
        productId: String,
        unit: com.example.domain.model.UnitOfMeasure,
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
