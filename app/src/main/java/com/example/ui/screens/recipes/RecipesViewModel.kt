package com.example.ui.screens.recipes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ImageStorageManager
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeIngredientEntity
import com.example.data.local.relation.ProductWithDetails
import com.example.data.local.relation.ShoppingListWithItems
import com.example.data.repository.EvaluatedRecipe
import com.example.data.repository.PantryRepository
import com.example.data.repository.RecipeRepository
import com.example.data.repository.ShoppingRepository
import com.example.domain.model.RecipeDifficulty
import com.example.domain.usecase.IngredientMatchDetail
import com.example.domain.usecase.RecipeFeasibility
import com.example.domain.usecase.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecipeFilterTab(val labelIt: String) {
    ALL("Tutte"),
    MAKEABLE("Realizzabili"),
    PARTIAL("Parziali"),
    RESCUE_EXPIRING("Salva scadenze")
}

data class RecipesUiState(
    val isLoading: Boolean = true,
    val recipes: List<EvaluatedRecipe> = emptyList(),
    val selectedTab: RecipeFilterTab = RecipeFilterTab.ALL,
    val searchQuery: String = "",
    val pantryProducts: List<ProductWithDetails> = emptyList(),
    val shoppingLists: List<ShoppingListWithItems> = emptyList()
)

@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val pantryRepository: PantryRepository,
    private val shoppingRepository: ShoppingRepository,
    private val imageStorageManager: ImageStorageManager
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(RecipeFilterTab.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    val uiState: StateFlow<RecipesUiState> = combine(
        recipeRepository.observeEvaluatedRecipes(),
        _selectedTab,
        _searchQuery,
        pantryRepository.observeAllProducts(),
        shoppingRepository.observeListsWithItems()
    ) { allRecipes, tab, query, products, lists ->
        val q = query.trim().lowercase()
        val filtered = allRecipes.filter { evaluated ->
            val matchesQuery = q.isEmpty() ||
                evaluated.recipeWithIngredients.recipe.name.lowercase().contains(q) ||
                evaluated.recipeWithIngredients.ingredients.any {
                    it.ingredientName.lowercase().contains(q)
                }

            val matchesTab = when (tab) {
                RecipeFilterTab.ALL -> true
                RecipeFilterTab.MAKEABLE -> evaluated.evaluation.feasibility == RecipeFeasibility.MAKEABLE
                RecipeFilterTab.PARTIAL -> evaluated.evaluation.feasibility == RecipeFeasibility.PARTIAL
                RecipeFilterTab.RESCUE_EXPIRING -> evaluated.evaluation.usesExpiringProductsCount > 0
            }
            matchesQuery && matchesTab
        }

        RecipesUiState(
            isLoading = false,
            recipes = filtered,
            selectedTab = tab,
            searchQuery = query,
            pantryProducts = products,
            shoppingLists = lists
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecipesUiState()
    )

    fun observeRecipe(recipeId: String): StateFlow<EvaluatedRecipe?> =
        recipeRepository.observeRecipe(recipeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectTab(tab: RecipeFilterTab) {
        _selectedTab.value = tab
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    suspend fun copyPickedImage(uri: Uri): String? = imageStorageManager.copyFromUri(uri)

    fun saveRecipe(
        recipeId: String?,
        name: String,
        imagePath: String?,
        description: String,
        prepTimeMinutes: Int,
        difficulty: RecipeDifficulty,
        servings: Int,
        instructions: String,
        ingredients: List<RecipeIngredientEntity>,
        onSaved: (String) -> Unit
    ) {
        if (name.isBlank()) {
            _feedback.value = "Il nome della ricetta è obbligatorio."
            return
        }
        if (ingredients.isEmpty()) {
            _feedback.value = "Aggiungi almeno un ingrediente."
            return
        }
        viewModelScope.launch {
            val savedId = recipeRepository.saveRecipe(
                recipe = RecipeEntity(
                    id = recipeId.orEmpty(),
                    name = name.trim(),
                    imagePath = imagePath,
                    description = description.trim(),
                    prepTimeMinutes = prepTimeMinutes.coerceAtLeast(1),
                    difficulty = difficulty,
                    servings = servings.coerceAtLeast(1),
                    instructions = instructions.trim()
                ),
                ingredients = ingredients
            )
            onSaved(savedId)
        }
    }

    fun deleteRecipe(recipeId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            recipeRepository.deleteRecipe(recipeId)
            onDeleted()
        }
    }

    fun addMissingToShoppingList(
        targetListId: String?,
        missing: List<IngredientMatchDetail>,
        recipeName: String
    ) {
        viewModelScope.launch {
            val count = recipeRepository.addMissingIngredientsToShoppingList(
                targetListId = targetListId,
                missingIngredients = missing,
                recipeName = recipeName
            )
            _feedback.value = if (count > 0) {
                "$count ingredienti mancanti aggiunti alla lista della spesa."
            } else {
                "Gli ingredienti mancanti sono già presenti nella lista."
            }
        }
    }

    fun cookRecipeConsumeIngredients(evaluated: EvaluatedRecipe) {
        viewModelScope.launch {
            var consumedCount = 0
            val productsById = uiState.value.pantryProducts.associateBy { it.product.id }
            for (detail in evaluated.evaluation.details) {
                val productId = detail.productId ?: continue
                val product = productsById[productId]?.product ?: continue
                val amountInProductUnit = UnitConverter.convert(
                    quantity = minOf(detail.requiredQuantity, detail.availableQuantity),
                    from = detail.unit,
                    to = product.unit
                ) ?: continue
                if (amountInProductUnit > 0.0) {
                    pantryRepository.consumeProductFefo(
                        productId = productId,
                        requestedQuantity = amountInProductUnit,
                        description = "Preparazione ricetta: ${evaluated.recipeWithIngredients.recipe.name}"
                    )
                    consumedCount++
                }
            }
            _feedback.value = if (consumedCount > 0) {
                "Ingredienti scaricati dalla dispensa (FEFO)."
            } else {
                "Nessun ingrediente collegato alla dispensa da scaricare."
            }
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }
}
