package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ShoppingItemEntity
import com.example.data.local.relation.BatchWithProduct
import com.example.data.local.relation.ProductWithDetails
import com.example.data.repository.EvaluatedRecipe
import com.example.data.repository.LowStockSuggestion
import com.example.data.repository.PantryRepository
import com.example.data.repository.RecipeRepository
import com.example.data.repository.ShoppingRepository
import com.example.domain.model.ExpiryStatus
import com.example.domain.usecase.ExpiryCalculator
import com.example.domain.usecase.PantryDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeDashboardUiState(
    val isLoading: Boolean = true,
    val totalProductsInStock: Int = 0,
    val totalRegisteredProducts: Int = 0,
    val expiringSoonBatches: List<BatchWithProduct> = emptyList(),
    val expiredBatches: List<BatchWithProduct> = emptyList(),
    val consumeFirstBatches: List<BatchWithProduct> = emptyList(),
    val lowStockSuggestions: List<LowStockSuggestion> = emptyList(),
    val recentProducts: List<ProductWithDetails> = emptyList(),
    val pendingShoppingItems: List<ShoppingItemEntity> = emptyList(),
    val pendingShoppingCount: Int = 0,
    val suggestedRecipes: List<EvaluatedRecipe> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pantryRepository: PantryRepository,
    private val shoppingRepository: ShoppingRepository,
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    val uiState: StateFlow<HomeDashboardUiState> = combine(
        pantryRepository.observeAllProducts(),
        pantryRepository.observeActiveBatches(),
        shoppingRepository.observeListsWithItems(),
        shoppingRepository.observeLowStockSuggestions(),
        recipeRepository.observeEvaluatedRecipes()
    ) { products, activeBatches, shoppingLists, lowStock, recipes ->
        val today = PantryDateUtils.todayStartMillis()
        val expiringSoon = mutableListOf<BatchWithProduct>()
        val expired = mutableListOf<BatchWithProduct>()

        for (b in activeBatches) {
            val eval = ExpiryCalculator.evaluate(
                expiryDateMillis = b.batch.expiryDate,
                expiryType = b.batch.expiryType,
                todayMillis = today,
                soonThresholdDays = 7
            )
            when (eval.status) {
                ExpiryStatus.EXPIRED -> expired.add(b)
                ExpiryStatus.EXPIRING_TODAY, ExpiryStatus.EXPIRING_SOON -> expiringSoon.add(b)
                else -> Unit
            }
        }

        val consumeFirst = activeBatches
            .filter { it.batch.expiryDate != null }
            .sortedBy { it.batch.expiryDate }
            .take(6)

        val pendingItems = shoppingLists
            .flatMap { it.items }
            .filter { !it.isPurchased }

        val inStockProducts = products.count { it.totalAvailableQuantity > 0.0 }
        val recent = products.sortedByDescending { it.product.createdAt }.take(5)

        val consumptionRecipes = recipes
            .filter { it.evaluation.usesExpiringProductsCount > 0 || it.evaluation.availabilityPercent >= 60 }
            .take(3)

        HomeDashboardUiState(
            isLoading = false,
            totalProductsInStock = inStockProducts,
            totalRegisteredProducts = products.size,
            expiringSoonBatches = expiringSoon,
            expiredBatches = expired,
            consumeFirstBatches = consumeFirst,
            lowStockSuggestions = lowStock,
            recentProducts = recent,
            pendingShoppingItems = pendingItems.take(5),
            pendingShoppingCount = pendingItems.size,
            suggestedRecipes = consumptionRecipes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeDashboardUiState()
    )

    fun quickConsumeBatchOneUnit(batchWithProduct: BatchWithProduct) {
        viewModelScope.launch {
            val amount = minOf(1.0, batchWithProduct.batch.remainingQuantity)
            if (amount > 0.0) {
                pantryRepository.consumeOrWasteBatch(
                    batchId = batchWithProduct.batch.id,
                    quantity = amount,
                    isWaste = false,
                    description = "Consumo rapido da Dashboard"
                )
            }
        }
    }
}
