package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ExpiryType
import com.example.domain.model.UnitOfMeasure
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "product_batches",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["location_id"]),
        Index(value = ["expiry_date"]),
        Index(value = ["is_exhausted"])
    ]
)
data class ProductBatchEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "initial_quantity")
    val initialQuantity: Double,
    @ColumnInfo(name = "remaining_quantity")
    val remainingQuantity: Double,
    val unit: UnitOfMeasure,
    @ColumnInfo(name = "purchase_date")
    val purchaseDate: Long,
    @ColumnInfo(name = "expiry_date")
    val expiryDate: Long? = null,
    @ColumnInfo(name = "expiry_type")
    val expiryType: ExpiryType = ExpiryType.USE_BY,
    @ColumnInfo(name = "location_id")
    val locationId: String,
    @ColumnInfo(name = "purchase_price")
    val purchasePrice: Double? = null,
    val notes: String = "",
    @ColumnInfo(name = "is_exhausted")
    val isExhausted: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
