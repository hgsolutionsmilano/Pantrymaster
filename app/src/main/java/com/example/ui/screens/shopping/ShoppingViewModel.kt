package com.example.ui.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ShoppingItemEntity
import com.example.data.local.entity.StorageLocationEntity
import com.example.data.local.relation.ShoppingListWithItems
import com.example.data.preferences.UserPreferencesDataStore
import com.example.data.repository.InventoryOperationResult
import com.example.data.repository.LowStockSuggestion
import com.example.data.repository.SettingsRepository
import com.example.data.repository.ShoppingRepository
import com.example.data.worker.WorkScheduler
import com.example.domain.model.ExpiryType
import com.example.domain.model.UnitOfMeasure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingUiState(
    val isLoading: Boolean = true,
    val lists: List<ShoppingListWithItems> = emptyList(),
    val selectedListId: String? = null,
    val lowStockSuggestions: List<LowStockSuggestion> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val locations: List<StorageLocationEntity> = emptyList(),
    val defaultUnit: UnitOfMeasure = UnitOfMeasure.PIECES
) {
    val activeList: ShoppingListWithItems?
        get() = lists.firstOrNull { it.list.id == selectedListId } ?: lists.firstOrNull()
}

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
    private val settingsRepository: SettingsRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val workScheduler: WorkScheduler
) : ViewModel() {

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    val uiState: StateFlow<ShoppingUiState> = combine(
        shoppingRepository.observeListsWithItems(),
        preferencesDataStore.selectedShoppingListId,
        shoppingRepository.observeLowStockSuggestions(),
        settingsRepository.observeCategories(),
        combine(
            settingsRepository.observeLocations(),
            settingsRepository.observeSettings()
        ) { locations, settings -> locations to settings.defaultUnit }
    ) { lists, selectedId, suggestions, categories, locAndUnit ->
        ShoppingUiState(
            isLoading = false,
            lists = lists,
            selectedListId = selectedId,
            lowStockSuggestions = suggestions,
            categories = categories,
            locations = locAndUnit.first,
            defaultUnit = locAndUnit.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ShoppingUiState()
    )

    fun selectList(listId: String) {
        viewModelScope.launch {
            preferencesDataStore.setSelectedShoppingListId(listId)
        }
    }

    fun createList(name: String) {
        viewModelScope.launch {
            val id = shoppingRepository.createList(name)
            preferencesDataStore.setSelectedShoppingListId(id)
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            shoppingRepository.deleteList(listId)
        }
    }

    fun saveItem(
        existingId: String?,
        listId: String,
        name: String,
        quantity: Double,
        unit: UnitOfMeasure,
        categoryId: String?,
        estimatedPrice: Double?,
        actualPrice: Double?,
        notes: String
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            shoppingRepository.saveItem(
                ShoppingItemEntity(
                    id = existingId.orEmpty(),
                    listId = listId,
                    name = name.trim(),
                    quantity = quantity,
                    unit = unit,
                    categoryId = categoryId,
                    estimatedPrice = estimatedPrice,
                    actualPrice = actualPrice,
                    notes = notes.trim()
                )
            )
        }
    }

    fun togglePurchased(item: ShoppingItemEntity, purchased: Boolean, actualPrice: Double? = item.actualPrice) {
        viewModelScope.launch {
            shoppingRepository.setItemPurchased(item, purchased, actualPrice)
        }
    }

    fun addLowStockSuggestions(listId: String) {
        val suggestions = uiState.value.lowStockSuggestions
        if (suggestions.isEmpty()) return
        viewModelScope.launch {
            shoppingRepository.addLowStockSuggestionsToList(listId, suggestions)
            _feedback.value = "${suggestions.size} prodotti sotto scorta aggiunti alla lista."
        }
    }

    fun confirmMoveToPantry(
        itemId: String,
        locationId: String,
        expiryDate: Long?,
        expiryType: ExpiryType,
        purchasePrice: Double?
    ) {
        viewModelScope.launch {
            val res = shoppingRepository.confirmMoveToPantry(
                itemId = itemId,
                locationId = locationId,
                expiryDate = expiryDate,
                expiryType = expiryType,
                purchasePrice = purchasePrice
            )
            when (res) {
                InventoryOperationResult.Success -> {
                    workScheduler.triggerImmediateExpiryCheck()
                    _feedback.value = "Articolo inserito in dispensa come nuovo lotto."
                }
                is InventoryOperationResult.Error -> _feedback.value = res.message
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch { shoppingRepository.deleteItem(itemId) }
    }

    fun clearPurchased(listId: String) {
        viewModelScope.launch { shoppingRepository.clearPurchased(listId) }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}
