package com.example.data.local.db

import androidx.room.TypeConverter
import com.example.domain.model.ExpiryType
import com.example.domain.model.HouseInfoCategory
import com.example.domain.model.LocationType
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.MovementType
import com.example.domain.model.ThemePreference
import com.example.domain.model.UnitOfMeasure

class Converters {
    @TypeConverter
    fun fromLocationType(value: LocationType): String = value.name

    @TypeConverter
    fun toLocationType(value: String): LocationType =
        runCatching { LocationType.valueOf(value) }.getOrDefault(LocationType.PANTRY)

    @TypeConverter
    fun fromUnitOfMeasure(value: UnitOfMeasure): String = value.name

    @TypeConverter
    fun toUnitOfMeasure(value: String): UnitOfMeasure =
        runCatching { UnitOfMeasure.valueOf(value) }.getOrDefault(UnitOfMeasure.PIECES)

    @TypeConverter
    fun fromExpiryType(value: ExpiryType): String = value.name

    @TypeConverter
    fun toExpiryType(value: String): ExpiryType =
        runCatching { ExpiryType.valueOf(value) }.getOrDefault(ExpiryType.USE_BY)

    @TypeConverter
    fun fromMovementType(value: MovementType): String = value.name

    @TypeConverter
    fun toMovementType(value: String): MovementType =
        runCatching { MovementType.valueOf(value) }.getOrDefault(MovementType.ADJUSTMENT)

    @TypeConverter
    fun fromHouseInfoCategory(value: HouseInfoCategory): String = value.name

    @TypeConverter
    fun toHouseInfoCategory(value: String): HouseInfoCategory =
        runCatching { HouseInfoCategory.valueOf(value) }.getOrDefault(HouseInfoCategory.PHONE_NUMBER)

    @TypeConverter
    fun fromMeasurementUnit(value: MeasurementUnit): String = value.name

    @TypeConverter
    fun toMeasurementUnit(value: String): MeasurementUnit =
        runCatching { MeasurementUnit.valueOf(value) }.getOrDefault(MeasurementUnit.CM)

    @TypeConverter
    fun fromThemePreference(value: ThemePreference): String = value.name

    @TypeConverter
    fun toThemePreference(value: String): ThemePreference =
        runCatching { ThemePreference.valueOf(value) }.getOrDefault(ThemePreference.SYSTEM)
}
