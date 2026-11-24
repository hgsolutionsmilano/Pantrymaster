package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.dao.ProductBatchDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ShoppingDao
import com.example.data.local.dao.StockMovementDao
import com.example.data.local.db.DatabaseSeeder
import com.example.data.local.db.PantryDatabase
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ShoppingItemEntity
import com.example.data.local.entity.ShoppingListEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.relation.ProductWithDetails
import com.example.data.local.relation.ShoppingListWithItems
import com.example.domain.model.ExpiryType
import com.example.domain.model.MovementType
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.PantryDateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class LowStockSuggestion(
    val product: ProductEntity,
    val currentQuantity: Double,
    val missingQuantity: Double
)

@Singleton
class ShoppingRepository @Inject constructor(
    private val database: PantryDatabase,
    private val shoppingDao: ShoppingDao,
    private val productDao: ProductDao,
    private val batchDao: ProductBatchDao,
    private val movementDao: StockMovementDao
) {
    fun observeListsWithItems(): Flow<List<ShoppingListWithItems>> =
        shoppingDao.observeActiveListsWithItems()

    fun observePendingCount(): Flow<Int> = shoppingDao.observePendingItemsCount()

    fun observeLowStockSuggestions(): Flow<List<LowStockSuggestion>> =
        productDao.observeAllWithDetails().map { products: List<ProductWithDetails> ->
            products.mapNotNull { details ->
                val min = details.product.minStockQuantity
                if (min <= 0.0) return@mapNotNull null
                val available = details.totalAvailableQuantity
                if (available + 1e-6 < min) {
                    LowStockSuggestion(
                        product = details.product,
                        currentQuantity = available,
                        missingQuantity = (min - available).coerceAtLeast(1.0)
                    )
                } else {
                    null
                }
            }
        }

    suspend fun createList(name: String): String {
        val id = UUID.randomUUID().toString()
        shoppingDao.upsertList(
            ShoppingListEntity(
                id = id,
                name = name.trim().ifBlank { "Lista della spesa" }
            )
        )
        return id
    }

    suspend fun renameList(list: ShoppingListEntity, newName: String) {
        if (newName.isBlank()) return
        shoppingDao.upsertList(list.copy(name = newName.trim()))
    }

    suspend fun deleteList(listId: String) {
        shoppingDao.deleteListById(listId)
    }

    suspend fun saveItem(item: ShoppingItemEntity) {
        val normalized = item.copy(
            id = item.id.ifBlank { UUID.randomUUID().toString() },
            quantity = item.quantity.coerceAtLeast(0.1)
        )
        shoppingDao.upsertItem(normalized)
    }

    suspend fun addLowStockSuggestionsToList(listId: String, suggestions: List<LowStockSuggestion>) {
        database.withTransaction {
            val existingItems = shoppingDao.getAllItems().filter { it.listId == listId && !it.isPurchased }
            for (s in suggestions) {
                val alreadyInList = existingItems.any {
                    it.productId == s.product.id || it.name.equals(s.product.name, ignoreCase = true)
                }
                if (!alreadyInList) {
                    shoppingDao.upsertItem(
                        ShoppingItemEntity(
                            id = UUID.randomUUID().toString(),
                            listId = listId,
                            productId = s.product.id,
                            name = s.product.name,
                            quantity = s.missingQuantity,
                            unit = s.product.unit,
                            categoryId = s.product.categoryId,
                            notes = "Sotto scorta minima"
                        )
                    )
                }
            }
        }
    }

    /**
     * Marks purchased status WITHOUT modifying pantry quantities.
     * Pantry quantities are only updated when the user explicitly confirms insertion into pantry.
     */
    suspend fun setItemPurchased(
        item: ShoppingItemEntity,
        purchased: Boolean,
        actualPrice: Double? = item.actualPrice
    ) {
        shoppingDao.updateItem(
            item.copy(
                isPurchased = purchased,
                purchasedAt = if (purchased) System.currentTimeMillis() else null,
                actualPrice = actualPrice?.takeIf { it >= 0.0 }
            )
        )
    }

    suspend fun deleteItem(itemId: String) {
        shoppingDao.deleteItemById(itemId)
    }

    suspend fun clearPurchased(listId: String) {
        shoppingDao.clearPurchasedInList(listId)
    }

    suspend fun confirmMoveToPantry(
        itemId: String,
        locationId: String,
        expiryDate: Long?,
        expiryType: ExpiryType,
        purchasePrice: Double?
    ): InventoryOperationResult {
        return database.withTransaction {
            val item = shoppingDao.getItemById(itemId)
                ?: return@withTransaction InventoryOperationResult.Error("Articolo non trovato.")
            if (item.addedToPantryBatchId != null) {
                return@withTransaction InventoryOperationResult.Error("Articolo già inserito in dispensa.")
            }

            val productId = item.productId ?: run {
                val newProductId = UUID.randomUUID().toString()
                productDao.upsert(
                    ProductEntity(
                        id = newProductId,
                        name = item.name,
                        categoryId = item.categoryId ?: DatabaseSeeder.DEFAULT_CATEGORY_OTHER_ID,
                        defaultLocationId = locationId,
                        unit = item.unit
                    )
                )
                newProductId
            }

            val batchId = UUID.randomUUID().toString()
            val effectivePrice = purchasePrice ?: item.actualPrice ?: item.estimatedPrice
            batchDao.upsert(
                ProductBatchEntity(
                    id = batchId,
                    productId = productId,
                    initialQuantity = item.quantity,
                    remainingQuantity = item.quantity,
                    unit = item.unit,
                    purchaseDate = PantryDateUtils.todayStartMillis(),
                    expiryDate = expiryDate,
                    expiryType = expiryType,
                    locationId = locationId,
                    purchasePrice = effectivePrice?.takeIf { it >= 0.0 },
                    notes = "Acquistato da lista spesa"
                )
            )
            movementDao.insert(
                StockMovementEntity(
                    id = UUID.randomUUID().toString(),
                    productId = productId,
                    batchId = batchId,
                    type = MovementType.PURCHASE,
                    quantity = item.quantity,
                    unit = item.unit,
                    priceAmount = effectivePrice?.takeIf { it >= 0.0 },
                    toLocationId = locationId,
                    description = "Acquisto confermato da lista della spesa"
                )
            )
            shoppingDao.updateItem(
                item.copy(
                    productId = productId,
                    isPurchased = true,
                    purchasedAt = item.purchasedAt ?: System.currentTimeMillis(),
                    actualPrice = effectivePrice,
                    addedToPantryBatchId = batchId
                )
            )
            InventoryOperationResult.Success
        }
    }
}
