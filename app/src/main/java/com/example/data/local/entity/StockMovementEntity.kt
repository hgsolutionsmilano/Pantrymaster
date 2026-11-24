package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.MovementType
import com.example.domain.model.UnitOfMeasure
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["batch_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["type"])
    ]
)
data class StockMovementEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "batch_id")
    val batchId: String? = null,
    val type: MovementType,
    val quantity: Double,
    val unit: UnitOfMeasure,
    @ColumnInfo(name = "price_amount")
    val priceAmount: Double? = null,
    @ColumnInfo(name = "from_location_id")
    val fromLocationId: String? = null,
    @ColumnInfo(name = "to_location_id")
    val toLocationId: String? = null,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
