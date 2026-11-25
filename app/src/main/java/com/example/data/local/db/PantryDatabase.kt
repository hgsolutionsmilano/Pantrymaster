package com.example.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.HouseInfoDao
import com.example.data.local.dao.ProductBatchDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ProductImageDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.SettingsDao
import com.example.data.local.dao.ShoppingDao
import com.example.data.local.dao.StockMovementDao
import com.example.data.local.dao.StorageLocationDao
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.HouseInfoEntryEntity
import com.example.data.local.entity.HouseInfoPhotoEntity
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductImageEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SettingsEntity
import com.example.data.local.entity.ShoppingItemEntity
import com.example.data.local.entity.ShoppingListEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.StorageLocationEntity

@Database(
    entities = [
        CategoryEntity::class,
        StorageLocationEntity::class,
        ProductEntity::class,
        ProductImageEntity::class,
        ProductBatchEntity::class,
        StockMovementEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        HouseInfoEntryEntity::class,
        HouseInfoPhotoEntity::class,
        ReminderEntity::class,
        SettingsEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PantryDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun storageLocationDao(): StorageLocationDao
    abstract fun productDao(): ProductDao
    abstract fun productImageDao(): ProductImageDao
    abstract fun productBatchDao(): ProductBatchDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun houseInfoDao(): HouseInfoDao
    abstract fun reminderDao(): ReminderDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val DATABASE_NAME = "pantrymaster.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN notification_hour INTEGER NOT NULL DEFAULT 9"
                )
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN notification_minute INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE shopping_items ADD COLUMN notes TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_shopping_items_is_purchased ON shopping_items(is_purchased)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS recipe_ingredients")
                db.execSQL("DROP TABLE IF EXISTS recipes")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `house_info_entries` (
                        `id` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `subtitle` TEXT NOT NULL,
                        `primary_value` TEXT NOT NULL,
                        `secondary_value` TEXT NOT NULL,
                        `secret_value` TEXT NOT NULL,
                        `width_value` TEXT NOT NULL,
                        `height_value` TEXT NOT NULL,
                        `depth_value` TEXT NOT NULL,
                        `measurement_unit` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `is_pinned` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_house_info_entries_category` ON `house_info_entries` (`category`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_house_info_entries_is_pinned` ON `house_info_entries` (`is_pinned`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_house_info_entries_title` ON `house_info_entries` (`title`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `house_info_photos` (
                        `id` TEXT NOT NULL,
                        `entry_id` TEXT NOT NULL,
                        `local_file_path` TEXT NOT NULL,
                        `caption` TEXT NOT NULL,
                        `sort_order` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`entry_id`) REFERENCES `house_info_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_house_info_photos_entry_id` ON `house_info_photos` (`entry_id`)"
                )
            }
        }
    }
}
