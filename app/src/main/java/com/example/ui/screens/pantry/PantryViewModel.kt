package com.example.ui.screens.pantry

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ImageStorageManager
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.StorageLocationEntity
import com.example.data.local.relation.MovementWithProduct
import com.example.data.local.relation.ProductWithDetails
import com.example.data.preferences.UserPreferencesDataStore
import com.example.data.repository.InventoryOperationResult
import com.example.data.repository.NewBatchInput
import com.example.data.repository.PantryRepository
import com.example.data.repository.SettingsRepository
import com.example.data.worker.WorkScheduler
import com.example.domain.model.ExpiryType
import com.example.domain.model.MovementType
import com.example.domain.model.PantrySortOption
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.PantryDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PantryFilterState(
    val searchQuery: String = "",
    val selectedCategoryId: String? = null,
    val selectedLocationId: String? = null,
    val sortOption: PantrySortOption = PantrySortOption.EXPIRY_ASC
)

data class PantryListUiState(
    val isLoading: Boolean = true,
    val products: List<ProductWithDetails> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val locations: List<StorageLocationEntity> = emptyList(),
    val filter: PantryFilterState = PantryFilterState(),
    val isGridView: Boolean = false
)

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val pantryRepository: PantryRepository,
    private val settingsRepository: SettingsRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val workScheduler: WorkScheduler,
    private val imageStorageManager: ImageStorageManager
) : ViewModel() {

    private val _filterState = MutableStateFlow(PantryFilterState())
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    val uiState: StateFlow<PantryListUiState> = combine(
        pantryRepository.observeAllProducts(),
        settingsRepository.observeCategories(),
        settingsRepository.observeLocations(),
        _filterState,
        preferencesDataStore.isPantryGridView
    ) { allProducts, categories, locations, filter, isGrid ->
        val query = filter.searchQuery.trim().lowercase()
        val filtered = allProducts.filter { details ->
            val matchesQuery = query.isEmpty() ||
                details.product.name.lowercase().contains(query) ||
                details.product.brand.lowercase().contains(query) ||
                (details.product.barcode?.contains(query) == true)

            val matchesCat = filter.selectedCategoryId == null ||
                details.product.categoryId == filter.selectedCategoryId

            val matchesLoc = filter.selectedLocationId == null ||
                details.activeBatches.any { it.locationId == filter.selectedLocationId } ||
                (details.activeBatches.isEmpty() && details.product.defaultLocationId == filter.selectedLocationId)

            matchesQuery && matchesCat && matchesLoc
        }

        val sorted = when (filter.sortOption) {
            PantrySortOption.NAME_ASC -> filtered.sortedBy { it.product.name.lowercase() }
            PantrySortOption.EXPIRY_ASC -> filtered.sortedWith(
                compareBy<ProductWithDetails>(
                    { it.earliestExpiryDate == null },
                    { it.earliestExpiryDate ?: Long.MAX_VALUE },
                    { it.product.name.lowercase() }
                )
            )
            PantrySortOption.QUANTITY_DESC -> filtered.sortedByDescending { it.totalAvailableQuantity }
            PantrySortOption.QUANTITY_ASC -> filtered.sortedBy { it.totalAvailableQuantity }
        }

        PantryListUiState(
            isLoading = false,
            products = sorted,
            categories = categories,
            locations = locations,
            filter = filter,
            isGridView = isGrid
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PantryListUiState()
    )

    val allMovements: StateFlow<List<MovementWithProduct>> = pantryRepository.observeAllMovements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeProductDetail(productId: String): StateFlow<ProductWithDetails?> =
        pantryRepository.observeProduct(productId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun observeProductMovements(productId: String): StateFlow<List<MovementWithProduct>> =
        pantryRepository.observeMovementsForProduct(productId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearch(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun selectCategory(categoryId: String?) {
        _filterState.value = _filterState.value.copy(selectedCategoryId = categoryId)
    }

    fun selectLocation(locationId: String?) {
        _filterState.value = _filterState.value.copy(selectedLocationId = locationId)
    }

    fun selectSort(sortOption: PantrySortOption) {
        _filterState.value = _filterState.value.copy(sortOption = sortOption)
    }

    fun toggleGridView() {
        viewModelScope.launch {
            preferencesDataStore.setPantryGridView(!uiState.value.isGridView)
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    suspend fun copyPickedImage(uri: Uri): String? = imageStorageManager.copyFromUri(uri)

    fun saveProduct(
        productId: String?,
        name: String,
        brand: String,
        barcode: String?,
        categoryId: String,
        defaultLocationId: String,
        description: String,
        unit: UnitOfMeasure,
        minStockQuantity: Double,
        notes: String,
        localImagePath: String?,
        initialBatchQty: Double?,
        initialBatchExpiry: Long?,
        initialBatchExpiryType: ExpiryType,
        initialBatchPrice: Double?,
        onSaved: (String) -> Unit
    ) {
        if (name.isBlank()) {
            _feedbackMessage.value = "Il nome del prodotto è obbligatorio."
            return
        }
        viewModelScope.launch {
            val existing = productId?.let { id ->
                uiState.value.products.firstOrNull { it.product.id == id }?.product
            }
            val entity = ProductEntity(
                id = productId.orEmpty(),
                name = name.trim(),
                brand = brand.trim(),
                barcode = barcode?.trim()?.takeIf { it.isNotEmpty() },
                categoryId = categoryId,
                defaultLocationId = defaultLocationId,
                imageId = existing?.imageId,
                description = description.trim(),
                unit = unit,
                minStockQuantity = minStockQuantity.coerceAtLeast(0.0),
                notes = notes.trim(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            val batchInput = if (initialBatchQty != null && initialBatchQty > 0.0) {
                NewBatchInput(
                    quantity = initialBatchQty,
                    unit = unit,
                    purchaseDate = PantryDateUtils.todayStartMillis(),
                    expiryDate = initialBatchExpiry,
                    expiryType = initialBatchExpiryType,
                    locationId = defaultLocationId,
                    purchasePrice = initialBatchPrice,
                    movementType = MovementType.MANUAL_ADD
                )
            } else {
                null
            }
            val savedId = pantryRepository.saveProduct(
                product = entity,
                localImagePath = localImagePath,
                initialBatch = batchInput
            )
            if (batchInput != null) {
                workScheduler.triggerImmediateExpiryCheck()
            }
            onSaved(savedId)
        }
    }

    fun addBatch(
        productId: String,
        quantity: Double,
        unit: UnitOfMeasure,
        expiryDate: Long?,
        expiryType: ExpiryType,
        locationId: String,
        purchasePrice: Double?,
        notes: String,
        isPurchase: Boolean
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
                    purchasePrice = purchasePrice,
                    notes = notes,
                    movementType = if (isPurchase) MovementType.PURCHASE else MovementType.MANUAL_ADD
                )
            )
            when (res) {
                InventoryOperationResult.Success -> {
                    _feedbackMessage.value = "Lotto aggiunto con successo."
                    workScheduler.triggerImmediateExpiryCheck()
                }
                is InventoryOperationResult.Error -> _feedbackMessage.value = res.message
            }
        }
    }

    fun consumeProductFefo(productId: String, quantity: Double) {
        viewModelScope.launch {
            val res = pantryRepository.consumeProductFefo(productId, quantity)
            _feedbackMessage.value = when (res) {
                InventoryOperationResult.Success -> "Consumo FEFO registrato."
                is InventoryOperationResult.Error -> res.message
            }
        }
    }

    fun consumeOrWasteBatch(batch: ProductBatchEntity, quantity: Double, isWaste: Boolean) {
        viewModelScope.launch {
            val res = pantryRepository.consumeOrWasteBatch(batch.id, quantity, isWaste)
            _feedbackMessage.value = when (res) {
                InventoryOperationResult.Success -> if (isWaste) "Spreco registrato." else "Consumo registrato."
                is InventoryOperationResult.Error -> res.message
            }
        }
    }

    fun adjustBatch(batchId: String, newQuantity: Double) {
        viewModelScope.launch {
            val res = pantryRepository.adjustBatchQuantity(batchId, newQuantity)
            _feedbackMessage.value = when (res) {
                InventoryOperationResult.Success -> "Quantità rettificata."
                is InventoryOperationResult.Error -> res.message
            }
        }
    }

    fun transferBatch(batchId: String, toLocationId: String) {
        viewModelScope.launch {
            val res = pantryRepository.transferBatchLocation(batchId, toLocationId)
            _feedbackMessage.value = when (res) {
                InventoryOperationResult.Success -> "Lotto trasferito."
                is InventoryOperationResult.Error -> res.message
            }
        }
    }

    fun updateBatchDates(batchId: String, expiryDate: Long?, expiryType: ExpiryType, price: Double?, notes: String) {
        viewModelScope.launch {
            val res = pantryRepository.updateBatchDetails(batchId, expiryDate, expiryType, price, notes)
            when (res) {
                InventoryOperationResult.Success -> {
                    _feedbackMessage.value = "Lotto aggiornato."
                    workScheduler.triggerImmediateExpiryCheck()
                }
                is InventoryOperationResult.Error -> _feedbackMessage.value = res.message
            }
        }
    }

    fun cancelBatch(batchId: String) {
        viewModelScope.launch {
            val res = pantryRepository.cancelBatch(batchId)
            _feedbackMessage.value = when (res) {
                InventoryOperationResult.Success -> "Lotto annullato (storico preservato)."
                is InventoryOperationResult.Error -> res.message
            }
        }
    }

    fun deleteProduct(productId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            pantryRepository.deleteProduct(productId)
            onDeleted()
        }
    }
}
