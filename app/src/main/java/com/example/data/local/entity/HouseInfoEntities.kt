package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.HouseInfoCategory
import com.example.domain.model.MeasurementUnit
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "house_info_entries",
    indices = [
        Index(value = ["category"]),
        Index(value = ["is_pinned"]),
        Index(value = ["title"])
    ]
)
data class HouseInfoEntryEntity(
    @PrimaryKey
    val id: String,
    val category: HouseInfoCategory,
    val title: String,
    val subtitle: String = "",
    @ColumnInfo(name = "primary_value")
    val primaryValue: String = "",
    @ColumnInfo(name = "secondary_value")
    val secondaryValue: String = "",
    @ColumnInfo(name = "secret_value")
    val secretValue: String = "",
    @ColumnInfo(name = "width_value")
    val widthValue: String = "",
    @ColumnInfo(name = "height_value")
    val heightValue: String = "",
    @ColumnInfo(name = "depth_value")
    val depthValue: String = "",
    @ColumnInfo(name = "measurement_unit")
    val measurementUnit: MeasurementUnit = MeasurementUnit.CM,
    val notes: String = "",
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "house_info_photos",
    foreignKeys = [
        ForeignKey(
            entity = HouseInfoEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["entry_id"])
    ]
)
data class HouseInfoPhotoEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "entry_id")
    val entryId: String,
    @ColumnInfo(name = "local_file_path")
    val localFilePath: String,
    val caption: String = "",
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
