package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.SettingsDao
import com.example.data.local.dao.StorageLocationDao
import com.example.data.local.db.DatabaseSeeder
import com.example.data.local.db.PantryDatabase
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SettingsEntity
import com.example.data.local.entity.StorageLocationEntity
import com.example.data.preferences.UserPreferencesDataStore
import com.example.domain.model.ThemePreference
import com.example.domain.model.UnitOfMeasure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val database: PantryDatabase,
    private val settingsDao: SettingsDao,
    private val categoryDao: CategoryDao,
    private val locationDao: StorageLocationDao,
    private val productDao: ProductDao,
    private val preferencesDataStore: UserPreferencesDataStore
) {
    fun observeSettings(): Flow<SettingsEntity> =
        settingsDao.observeSettings().map { it ?: SettingsEntity(id = 1) }

    fun observeThemePreference(): Flow<ThemePreference> = preferencesDataStore.themePreference

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun observeLocations(): Flow<List<StorageLocationEntity>> = locationDao.observeAll()

    suspend fun updateTheme(theme: ThemePreference) {
        val current = settingsDao.getSettings() ?: SettingsEntity(id = 1)
        settingsDao.upsert(current.copy(themeMode = theme))
        preferencesDataStore.setThemePreference(theme)
    }

    suspend fun updateNotifications(
        enabled: Boolean,
        notify7: Boolean,
        notify3: Boolean,
        notify1: Boolean,
        notify0: Boolean
    ) {
        val current = settingsDao.getSettings() ?: SettingsEntity(id = 1)
        settingsDao.upsert(
            current.copy(
                notificationsEnabled = enabled,
                notify7Days = notify7,
                notify3Days = notify3,
                notify1Day = notify1,
                notify0Days = notify0
            )
        )
    }

    suspend fun updateDefaultUnit(unit: UnitOfMeasure) {
        val current = settingsDao.getSettings() ?: SettingsEntity(id = 1)
        settingsDao.upsert(current.copy(defaultUnit = unit))
    }

    suspend fun saveCategory(category: CategoryEntity) {
        val normalized = category.copy(
            id = category.id.ifBlank { UUID.randomUUID().toString() },
            name = category.name.trim()
        )
        if (normalized.name.isBlank()) return
        categoryDao.upsert(normalized)
    }

    suspend fun deleteCustomCategory(categoryId: String) {
        database.withTransaction {
            val target = categoryDao.getById(DatabaseSeeder.DEFAULT_CATEGORY_OTHER_ID)
                ?: categoryDao.getAll().firstOrNull { it.id != categoryId }
            if (target != null) {
                productDao.reassignCategory(categoryId, target.id)
            }
            categoryDao.deleteCustomById(categoryId)
        }
    }
}
