package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.ProductBatchDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ProductImageDao
import com.example.data.local.dao.RecipeDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.SettingsDao
import com.example.data.local.dao.ShoppingDao
import com.example.data.local.dao.StockMovementDao
import com.example.data.local.dao.StorageLocationDao
import com.example.data.local.db.PantryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePantryDatabase(@ApplicationContext context: Context): PantryDatabase {
        return Room.databaseBuilder(
            context,
            PantryDatabase::class.java,
            PantryDatabase.DATABASE_NAME
        )
            .addMigrations(PantryDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideCategoryDao(db: PantryDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideStorageLocationDao(db: PantryDatabase): StorageLocationDao = db.storageLocationDao()

    @Provides
    fun provideProductDao(db: PantryDatabase): ProductDao = db.productDao()

    @Provides
    fun provideProductImageDao(db: PantryDatabase): ProductImageDao = db.productImageDao()

    @Provides
    fun provideProductBatchDao(db: PantryDatabase): ProductBatchDao = db.productBatchDao()

    @Provides
    fun provideStockMovementDao(db: PantryDatabase): StockMovementDao = db.stockMovementDao()

    @Provides
    fun provideShoppingDao(db: PantryDatabase): ShoppingDao = db.shoppingDao()

    @Provides
    fun provideRecipeDao(db: PantryDatabase): RecipeDao = db.recipeDao()

    @Provides
    fun provideReminderDao(db: PantryDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun provideSettingsDao(db: PantryDatabase): SettingsDao = db.settingsDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
