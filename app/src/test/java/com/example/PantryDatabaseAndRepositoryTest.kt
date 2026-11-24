package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ImageStorageManager
import com.example.data.local.db.DatabaseSeeder
import com.example.data.local.db.PantryDatabase
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeIngredientEntity
import com.example.data.local.entity.ShoppingItemEntity
import com.example.data.local.entity.ShoppingListEntity
import com.example.data.repository.InventoryOperationResult
import com.example.data.repository.NewBatchInput
import com.example.data.repository.PantryRepository
import com.example.domain.model.ExpiryType
import com.example.domain.model.MovementType
import com.example.domain.model.RecipeDifficulty
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.PantryDateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PantryDatabaseAndRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: PantryDatabase
    private lateinit var repository: PantryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PantryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val imageManager = ImageStorageManager(context, OkHttpClient())
        repository = PantryRepository(
            database = db,
            productDao = db.productDao(),
            productImageDao = db.productImageDao(),
            batchDao = db.productBatchDao(),
            movementDao = db.stockMovementDao(),
            reminderDao = db.reminderDao(),
            imageStorageManager = imageManager
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `load, FEFO consume, preserve exhausted history, and reject negative stock`() = runTest {
        db.storageLocationDao().insertAll(DatabaseSeeder.defaultLocations())
        db.categoryDao().insertAll(DatabaseSeeder.defaultCategories())

        val today = PantryDateUtils.todayStartMillis()
        val prodId = repository.saveProduct(
            product = ProductEntity(
                id = "prod_milk",
                name = "Latte Fresco",
                categoryId = "cat_dairy",
                defaultLocationId = "loc_fridge",
                unit = UnitOfMeasure.LITERS
            ),
            initialBatch = NewBatchInput(
                quantity = 2.0,
                unit = UnitOfMeasure.LITERS,
                purchaseDate = today,
                expiryDate = PantryDateUtils.addDays(today, 5),
                expiryType = ExpiryType.USE_BY,
                locationId = "loc_fridge",
                purchasePrice = 3.20,
                movementType = MovementType.PURCHASE
            )
        )

        // Add an earlier-expiring batch (should be consumed FIRST via FEFO)
        val addRes = repository.addBatch(
            productId = prodId,
            input = NewBatchInput(
                quantity = 1.0,
                unit = UnitOfMeasure.LITERS,
                purchaseDate = today,
                expiryDate = PantryDateUtils.addDays(today, 2),
                expiryType = ExpiryType.USE_BY,
                locationId = "loc_fridge",
                purchasePrice = 1.60,
                movementType = MovementType.PURCHASE
            )
        )
        assertTrue(addRes is InventoryOperationResult.Success)

        // Consume 1.5 L -> should exhaust the 2-day batch (1.0) and take 0.5 from the 5-day batch
        val consumeRes = repository.consumeProductFefo(prodId, 1.5)
        assertTrue(consumeRes is InventoryOperationResult.Success)

        val details = db.productDao().getProductWithDetails(prodId)
        assertNotNull(details)
        assertEquals(1.5, details!!.totalAvailableQuantity, 0.001)
        // Exhausted batch must still be preserved in batches table!
        assertEquals(2, details.batches.size)
        assertEquals(1, details.activeBatches.size)
        assertTrue(details.batches.any { it.isExhausted && it.remainingQuantity == 0.0 })

        // Negative or excessive consumption must fail cleanly
        val failExcess = repository.consumeProductFefo(prodId, 5.0)
        assertTrue(failExcess is InventoryOperationResult.Error)

        val failNegative = repository.adjustBatchQuantity(details.activeBatches.first().id, -1.0)
        assertTrue(failNegative is InventoryOperationResult.Error)

        // Verify movements audit trail
        val movements = db.stockMovementDao().getAll()
        assertEquals(4, movements.size) // 2 purchases + 2 FEFO consumes
    }

    @Test
    fun `room relations work for shopping lists and recipes`() = runTest {
        db.storageLocationDao().insertAll(DatabaseSeeder.defaultLocations())
        db.categoryDao().insertAll(DatabaseSeeder.defaultCategories())

        db.shoppingDao().upsertList(ShoppingListEntity("list_1", "Settimanale"))
        db.shoppingDao().upsertItem(
            ShoppingItemEntity(
                id = "item_1",
                listId = "list_1",
                name = "Pomodori",
                quantity = 1.0,
                unit = UnitOfMeasure.KILOGRAMS,
                categoryId = "cat_produce"
            )
        )
        val lists = db.shoppingDao().observeActiveListsWithItems().first()
        assertEquals(1, lists.size)
        assertEquals(1, lists[0].items.size)
        assertEquals("Pomodori", lists[0].items[0].name)

        db.recipeDao().saveRecipeWithIngredients(
            recipe = RecipeEntity(
                id = "rec_1",
                name = "Pasta al Pomodoro",
                prepTimeMinutes = 15,
                difficulty = RecipeDifficulty.EASY,
                servings = 2
            ),
            ingredients = listOf(
                RecipeIngredientEntity(
                    id = "ing_1",
                    recipeId = "rec_1",
                    ingredientName = "Pasta",
                    requiredQuantity = 200.0,
                    unit = UnitOfMeasure.GRAMS
                )
            )
        )
        val recipe = db.recipeDao().getByIdWithIngredients("rec_1")
        assertNotNull(recipe)
        assertEquals(1, recipe!!.ingredients.size)
    }

    @Test
    fun `migration 1 to 2 adds new columns and preserves existing rows`() {
        val dbName = "migration_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE settings (
                                id INTEGER PRIMARY KEY NOT NULL,
                                theme_mode TEXT NOT NULL,
                                notifications_enabled INTEGER NOT NULL,
                                notify_7_days INTEGER NOT NULL,
                                notify_3_days INTEGER NOT NULL,
                                notify_1_day INTEGER NOT NULL,
                                notify_0_days INTEGER NOT NULL,
                                default_unit TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE shopping_items (
                                id TEXT PRIMARY KEY NOT NULL,
                                list_id TEXT NOT NULL,
                                product_id TEXT,
                                name TEXT NOT NULL,
                                quantity REAL NOT NULL,
                                unit TEXT NOT NULL,
                                category_id TEXT,
                                estimated_price REAL,
                                actual_price REAL,
                                is_purchased INTEGER NOT NULL,
                                purchased_at INTEGER,
                                added_to_pantry_batch_id TEXT
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO settings VALUES (1, 'DARK', 1, 1, 1, 1, 1, 'PIECES')"
                        )
                        db.execSQL(
                            "INSERT INTO shopping_items VALUES ('s1', 'l1', NULL, 'Pane', 2.0, 'PIECES', NULL, 2.5, NULL, 0, NULL, NULL)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val rawDb = helper.writableDatabase
        PantryDatabase.MIGRATION_1_2.migrate(rawDb)

        rawDb.query("SELECT theme_mode, notification_hour, notification_minute FROM settings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("DARK", c.getString(0))
            assertEquals(9, c.getInt(1))
            assertEquals(0, c.getInt(2))
        }

        rawDb.query("SELECT name, notes FROM shopping_items WHERE id = 's1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Pane", c.getString(0))
            assertEquals("", c.getString(1))
        }

        rawDb.close()
        context.deleteDatabase(dbName)
    }
}
