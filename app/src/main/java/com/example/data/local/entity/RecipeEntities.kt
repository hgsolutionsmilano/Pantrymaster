package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.RecipeDifficulty
import com.example.domain.model.UnitOfMeasure
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "recipes",
    indices = [Index(value = ["name"])]
)
data class RecipeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,
    val description: String = "",
    @ColumnInfo(name = "prep_time_minutes")
    val prepTimeMinutes: Int = 20,
    val difficulty: RecipeDifficulty = RecipeDifficulty.EASY,
    val servings: Int = 2,
    val instructions: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["recipe_id"]),
        Index(value = ["product_id"])
    ]
)
data class RecipeIngredientEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "recipe_id")
    val recipeId: String,
    @ColumnInfo(name = "product_id")
    val productId: String? = null,
    @ColumnInfo(name = "ingredient_name")
    val ingredientName: String,
    @ColumnInfo(name = "required_quantity")
    val requiredQuantity: Double,
    val unit: UnitOfMeasure,
    val notes: String = ""
)
