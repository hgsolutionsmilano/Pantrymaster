package com.example.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductImageEntity
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeIngredientEntity
import com.example.data.local.entity.ShoppingItemEntity
import com.example.data.local.entity.ShoppingListEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.StorageLocationEntity

data class ProductWithDetails(
    @Embedded val product: ProductEntity,
    @Relation(
        parentColumn = "category_id",
        entityColumn = "id"
    )
    val category: CategoryEntity?,
    @Relation(
        parentColumn = "default_location_id",
        entityColumn = "id"
    )
    val defaultLocation: StorageLocationEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "product_id"
    )
    val images: List<ProductImageEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "product_id"
    )
    val batches: List<ProductBatchEntity>
) {
    val activeBatches: List<ProductBatchEntity>
        get() = batches.filter { !it.isExhausted && it.remainingQuantity > 0.0 }
            .sortedWith(compareBy({ it.expiryDate == null }, { it.expiryDate ?: Long.MAX_VALUE }, { it.createdAt }))

    val totalAvailableQuantity: Double
        get() = activeBatches.sumOf { it.remainingQuantity }

    val earliestExpiryDate: Long?
        get() = activeBatches.mapNotNull { it.expiryDate }.minOrNull()

    val primaryImagePath: String?
        get() = images.firstOrNull { it.id == product.imageId }?.localFilePath
            ?: images.firstOrNull()?.localFilePath
}

data class BatchWithProduct(
    @Embedded val batch: ProductBatchEntity,
    @Relation(
        parentColumn = "product_id",
        entityColumn = "id"
    )
    val product: ProductEntity?,
    @Relation(
        parentColumn = "location_id",
        entityColumn = "id"
    )
    val location: StorageLocationEntity?
)

data class MovementWithProduct(
    @Embedded val movement: StockMovementEntity,
    @Relation(
        parentColumn = "product_id",
        entityColumn = "id"
    )
    val product: ProductEntity?
)

data class ShoppingListWithItems(
    @Embedded val list: ShoppingListEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "list_id"
    )
    val items: List<ShoppingItemEntity>
)

data class RecipeWithIngredients(
    @Embedded val recipe: RecipeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "recipe_id"
    )
    val ingredients: List<RecipeIngredientEntity>
)

data class CategorySpendAggregate(
    val categoryId: String,
    val categoryName: String,
    val categoryColor: String,
    val totalConsumed: Double,
    val totalWasted: Double,
    val totalPurchased: Double,
    val totalSpend: Double
)

data class TopUsedProductAggregate(
    val productId: String,
    val productName: String,
    val totalConsumed: Double,
    val movementCount: Int
)
