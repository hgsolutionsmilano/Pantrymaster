package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.LocationType
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "storage_locations",
    indices = [Index(value = ["location_type"], unique = true)]
)
data class StorageLocationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "location_type")
    val locationType: LocationType,
    @ColumnInfo(name = "icon_name")
    val iconName: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int
)
