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
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_location_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["default_location_id"]),
        Index(value = ["barcode"]),
        Index(value = ["name"])
    ]
)
data class ProductEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val brand: String = "",
    val barcode: String? = null,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "default_location_id")
    val defaultLocationId: String,
    @ColumnInfo(name = "image_id")
    val imageId: String? = null,
    val description: String = "",
    val unit: UnitOfMeasure = UnitOfMeasure.PIECES,
    @ColumnInfo(name = "min_stock_quantity")
    val minStockQuantity: Double = 0.0,
    val notes: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
