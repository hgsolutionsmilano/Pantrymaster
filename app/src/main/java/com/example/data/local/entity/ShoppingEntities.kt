package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.UnitOfMeasure
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "shopping_lists",
    indices = [Index(value = ["created_at"])]
)
data class ShoppingListEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false
)

@Serializable
@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["list_id"]),
        Index(value = ["product_id"]),
        Index(value = ["category_id"]),
        Index(value = ["is_purchased"])
    ]
)
data class ShoppingItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "list_id")
    val listId: String,
    @ColumnInfo(name = "product_id")
    val productId: String? = null,
    val name: String,
    val quantity: Double,
    val unit: UnitOfMeasure,
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,
    @ColumnInfo(name = "estimated_price")
    val estimatedPrice: Double? = null,
    @ColumnInfo(name = "actual_price")
    val actualPrice: Double? = null,
    @ColumnInfo(name = "is_purchased")
    val isPurchased: Boolean = false,
    @ColumnInfo(name = "purchased_at")
    val purchasedAt: Long? = null,
    @ColumnInfo(name = "added_to_pantry_batch_id")
    val addedToPantryBatchId: String? = null,
    val notes: String = ""
)
