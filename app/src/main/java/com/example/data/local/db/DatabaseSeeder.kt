package com.example.data.local.db

import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.SettingsDao
import com.example.data.local.dao.StorageLocationDao
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SettingsEntity
import com.example.data.local.entity.StorageLocationEntity
import com.example.domain.model.LocationType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val categoryDao: CategoryDao,
    private val storageLocationDao: StorageLocationDao,
    private val settingsDao: SettingsDao
) {
    private val mutex = Mutex()

    suspend fun ensureSeeded() {
        mutex.withLock {
            if (storageLocationDao.count() == 0) {
                storageLocationDao.insertAll(defaultLocations())
            }
            if (categoryDao.count() == 0) {
                categoryDao.insertAll(defaultCategories())
            }
            if (settingsDao.getSettings() == null) {
                settingsDao.upsert(SettingsEntity(id = 1))
            }
        }
    }

    companion object {
        const val DEFAULT_CATEGORY_OTHER_ID = "cat_other"
        const val DEFAULT_LOCATION_PANTRY_ID = "loc_pantry"

        fun defaultLocations(): List<StorageLocationEntity> = listOf(
            StorageLocationEntity("loc_fridge", "Frigorifero", LocationType.FRIDGE, "kitchen", 0),
            StorageLocationEntity("loc_freezer", "Freezer", LocationType.FREEZER, "ac_unit", 1),
            StorageLocationEntity("loc_pantry", "Dispensa", LocationType.PANTRY, "inventory_2", 2),
            StorageLocationEntity("loc_other", "Altro", LocationType.OTHER, "more_horiz", 3)
        )

        fun defaultCategories(): List<CategoryEntity> = listOf(
            CategoryEntity("cat_produce", "Frutta e Verdura", "eco", "#3E7B52", true, 0),
            CategoryEntity("cat_dairy", "Latticini e Uova", "egg", "#5A8F7B", true, 1),
            CategoryEntity("cat_meat", "Carne e Pesce", "set_meal", "#A35648", true, 2),
            CategoryEntity("cat_grains", "Cereali e Pasta", "bakery_dining", "#B68D40", true, 3),
            CategoryEntity("cat_canned", "Conserve e Barattoli", "inventory", "#7B6D5A", true, 4),
            CategoryEntity("cat_beverages", "Bevande", "local_cafe", "#487B8C", true, 5),
            CategoryEntity("cat_frozen", "Surgelati", "ac_unit", "#507FA8", true, 6),
            CategoryEntity("cat_condiments", "Oli e Condimenti", "water_drop", "#8A9A5B", true, 7),
            CategoryEntity("cat_snacks", "Dolci e Snack", "cookie", "#A57865", true, 8),
            CategoryEntity("cat_other", "Altro", "category", "#6C757D", true, 99)
        )
    }
}
