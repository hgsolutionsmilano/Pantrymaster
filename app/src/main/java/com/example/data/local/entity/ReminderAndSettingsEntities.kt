package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ThemePreference
import com.example.domain.model.UnitOfMeasure
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = ProductBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["batch_id", "threshold_days"], unique = true),
        Index(value = ["scheduled_date"])
    ]
)
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "batch_id")
    val batchId: String,
    @ColumnInfo(name = "threshold_days")
    val thresholdDays: Int,
    @ColumnInfo(name = "scheduled_date")
    val scheduledDate: Long,
    @ColumnInfo(name = "notified_at")
    val notifiedAt: Long? = null,
    @ColumnInfo(name = "is_dismissed")
    val isDismissed: Boolean = false
)

@Serializable
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "theme_mode")
    val themeMode: ThemePreference = ThemePreference.SYSTEM,
    @ColumnInfo(name = "notifications_enabled")
    val notificationsEnabled: Boolean = true,
    @ColumnInfo(name = "notify_7_days")
    val notify7Days: Boolean = true,
    @ColumnInfo(name = "notify_3_days")
    val notify3Days: Boolean = true,
    @ColumnInfo(name = "notify_1_day")
    val notify1Day: Boolean = true,
    @ColumnInfo(name = "notify_0_days")
    val notify0Days: Boolean = true,
    @ColumnInfo(name = "notification_hour")
    val notificationHour: Int = 9,
    @ColumnInfo(name = "notification_minute")
    val notificationMinute: Int = 0,
    @ColumnInfo(name = "default_unit")
    val defaultUnit: UnitOfMeasure = UnitOfMeasure.PIECES
)
