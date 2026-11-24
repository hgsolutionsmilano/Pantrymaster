package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.ImageStorageManager
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.RecipeDao
import com.example.data.local.dao.ShoppingDao
import com.example.data.local.db.PantryDatabase
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeIngredientEntity
import com.example.data.local.entity.ShoppingItemEntity
import com.example.data.local.entity.ShoppingListEntity
import com.example.data.local.relation.RecipeWithIngredients
import com.example.domain.usecase.AvailableIngredientStock
import com.example.domain.usecase.IngredientMatchDetail
import com.example.domain.usecase.PantryDateUtils
import com.example.domain.usecase.RecipeIngredientSpec
import com.example.domain.usecase.RecipeMatchEvaluation
import com.example.domain.usecase.RecipeMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class EvaluatedRecipe(
    val recipeWithIngredients: RecipeWithIngredients,
    val evaluation: RecipeMatchEvaluation
)

@Singleton
class RecipeRepository @Inject constructor(
    private val database: PantryDatabase,
    private val recipeDao: RecipeDao,
    private val productDao: ProductDao,
    private val shoppingDao: ShoppingDao,
    private val imageStorageManager: ImageStorageManager
) {
    fun observeEvaluatedRecipes(): Flow<List<EvaluatedRecipe>> =
        combine(
            recipeDao.observeAllWithIngredients(),
            productDao.observeAllWithDetails()
        ) { recipes, products ->
            val today = PantryDateUtils.todayStartMillis()
            val stock = products.map { p ->
                AvailableIngredientStock(
                    productId = p.product.id,
                    productName = p.product.name,
                    availableQuantity = p.totalAvailableQuantity,
                    unit = p.product.unit,
                    earliestExpiryMillis = p.earliestExpiryDate
                )
            }
            recipes.map { r ->
                val specs = r.ingredients.map { ing ->
                    RecipeIngredientSpec(
                        ingredientId = ing.id,
                        productId = ing.productId,
                        ingredientName = ing.ingredientName,
                        requiredQuantity = ing.requiredQuantity,
                        unit = ing.unit
                    )
                }
                EvaluatedRecipe(
                    recipeWithIngredients = r,
                    evaluation = RecipeMatcher.evaluate(specs, stock, todayMillis = today)
                )
            }.sortedWith(
                compareByDescending<EvaluatedRecipe> { it.evaluation.usesExpiringProductsCount > 0 }
                    .thenByDescending { it.evaluation.priorityScore }
                    .thenBy { it.recipeWithIngredients.recipe.name }
            )
        }

    fun observeRecipe(recipeId: String): Flow<EvaluatedRecipe?> =
        combine(
            recipeDao.observeByIdWithIngredients(recipeId),
            productDao.observeAllWithDetails()
        ) { recipe, products ->
            recipe ?: return@combine null
            val today = PantryDateUtils.todayStartMillis()
            val stock = products.map { p ->
                AvailableIngredientStock(
                    productId = p.product.id,
                    productName = p.product.name,
                    availableQuantity = p.totalAvailableQuantity,
                    unit = p.product.unit,
                    earliestExpiryMillis = p.earliestExpiryDate
                )
            }
            val specs = recipe.ingredients.map { ing ->
                RecipeIngredientSpec(
                    ingredientId = ing.id,
                    productId = ing.productId,
                    ingredientName = ing.ingredientName,
                    requiredQuantity = ing.requiredQuantity,
                    unit = ing.unit
                )
            }
            EvaluatedRecipe(
                recipeWithIngredients = recipe,
                evaluation = RecipeMatcher.evaluate(specs, stock, todayMillis = today)
            )
        }

    suspend fun saveRecipe(
        recipe: RecipeEntity,
        ingredients: List<RecipeIngredientEntity>
    ): String {
        val recipeId = recipe.id.ifBlank { UUID.randomUUID().toString() }
        val existing = recipeDao.getByIdWithIngredients(recipeId)
        if (existing?.recipe?.imagePath != null && existing.recipe.imagePath != recipe.imagePath) {
            imageStorageManager.deleteImageFile(existing.recipe.imagePath)
        }
        val normalizedRecipe = recipe.copy(id = recipeId)
        val normalizedIngredients = ingredients.map { ing ->
            ing.copy(
                id = ing.id.ifBlank { UUID.randomUUID().toString() },
                recipeId = recipeId
            )
        }
        recipeDao.saveRecipeWithIngredients(normalizedRecipe, normalizedIngredients)
        return recipeId
    }

    suspend fun deleteRecipe(recipeId: String) {
        val existing = recipeDao.getByIdWithIngredients(recipeId)
        imageStorageManager.deleteImageFile(existing?.recipe?.imagePath)
        recipeDao.deleteRecipeById(recipeId)
    }

    suspend fun addMissingIngredientsToShoppingList(
        targetListId: String?,
        missingIngredients: List<IngredientMatchDetail>,
        recipeName: String
    ): Int {
        if (missingIngredients.isEmpty()) return 0
        var addedCount = 0
        database.withTransaction {
            val lists = shoppingDao.getAllLists().filter { !it.isArchived }
            val listId = targetListId ?: lists.firstOrNull()?.id ?: run {
                val newListId = UUID.randomUUID().toString()
                shoppingDao.upsertList(
                    ShoppingListEntity(id = newListId, name = "Spesa Ricette")
                )
                newListId
            }
            val existingItems = shoppingDao.getAllItems().filter { it.listId == listId && !it.isPurchased }
            for (missing in missingIngredients) {
                if (missing.missingQuantity <= 0.0) continue
                val alreadyExists = existingItems.any {
                    it.name.equals(missing.ingredientName, ignoreCase = true)
                }
                if (!alreadyExists) {
                    val linkedProduct = missing.productId?.let { productDao.getById(it) }
                    shoppingDao.upsertItem(
                        ShoppingItemEntity(
                            id = UUID.randomUUID().toString(),
                            listId = listId,
                            productId = missing.productId,
                            name = missing.ingredientName,
                            quantity = missing.missingQuantity,
                            unit = missing.unit,
                            categoryId = linkedProduct?.categoryId,
                            notes = "Per ricetta: $recipeName"
                        )
                    )
                    addedCount++
                }
            }
        }
        return addedCount
    }
}
