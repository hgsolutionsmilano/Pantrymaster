package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.data.local.ImageStorageManager
import com.example.data.local.db.DatabaseSeeder
import com.example.data.local.db.PantryDatabase
import com.example.data.local.entity.HouseInfoEntryEntity
import com.example.data.local.entity.HouseInfoPhotoEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SettingsEntity
import com.example.data.local.entity.ShoppingItemEntity
import com.example.data.local.entity.ShoppingListEntity
import com.example.data.preferences.UserPreferencesDataStore
import com.example.data.remote.OpenFoodFactsApi
import com.example.data.remote.OpenFoodFactsProductDto
import com.example.data.remote.OpenFoodFactsResponse
import com.example.data.repository.BarcodeLookupResult
import com.example.data.repository.BarcodeRepository
import com.example.data.repository.InventoryOperationResult
import com.example.data.repository.NewBatchInput
import com.example.data.repository.PantryRepository
import com.example.data.worker.ExpiryNotificationWorker
import com.example.domain.model.ExpiryType
import com.example.domain.model.HouseInfoCategory
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.MovementType
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.PantryDateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `room relations work for shopping lists and house info`() = runTest {
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

        db.houseInfoDao().saveEntryWithPhotos(
            entry = HouseInfoEntryEntity(
                id = "info_1",
                category = HouseInfoCategory.MEASUREMENT,
                title = "Finestra cucina",
                widthValue = "110",
                heightValue = "130",
                measurementUnit = MeasurementUnit.CM,
                isPinned = true
            ),
            photos = listOf(
                HouseInfoPhotoEntity(
                    id = "ph_1",
                    entryId = "info_1",
                    localFilePath = "/tmp/finestra.jpg",
                    caption = "Vista interna",
                    sortOrder = 0
                )
            )
        )
        val entry = db.houseInfoDao().getByIdWithPhotos("info_1")
        assertNotNull(entry)
        assertEquals(1, entry!!.photos.size)
        assertEquals("Vista interna", entry.photos[0].caption)
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

    @Test
    fun `migration 2 to 3 replaces recipes with house_info tables cleanly`() {
        val dbName = "migration_2_3_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE recipes (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE recipe_ingredients (id TEXT PRIMARY KEY NOT NULL, recipe_id TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE settings (id INTEGER PRIMARY KEY NOT NULL, theme_mode TEXT NOT NULL)")
                        db.execSQL("INSERT INTO settings VALUES (1, 'LIGHT')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val rawDb = helper.writableDatabase
        PantryDatabase.MIGRATION_2_3.migrate(rawDb)

        rawDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('recipes','recipe_ingredients')").use { c ->
            assertEquals(0, c.count)
        }
        rawDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('house_info_entries','house_info_photos')").use { c ->
            assertEquals(2, c.count)
        }
        rawDb.query("SELECT theme_mode FROM settings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("LIGHT", c.getString(0))
        }

        rawDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `saveProduct with image and initial batch succeeds and edit preserves batches`() = runTest {
        // Test saving even on an empty unseeded database with an image and initial batch
        val productId = repository.saveProduct(
            product = ProductEntity(
                id = "",
                name = "Latte Fresco",
                brand = "Granarolo",
                categoryId = "non_existent_cat",
                defaultLocationId = "non_existent_loc",
                unit = UnitOfMeasure.LITERS
            ),
            localImagePath = "/tmp/test_milk.jpg",
            initialBatch = NewBatchInput(
                quantity = 2.0,
                unit = UnitOfMeasure.LITERS,
                locationId = "non_existent_loc"
            )
        )

        val created = db.productDao().getProductWithDetails(productId)
        assertNotNull(created)
        assertEquals("/tmp/test_milk.jpg", created!!.primaryImagePath)
        assertEquals(1, created.activeBatches.size)
        assertEquals(2.0, created.totalAvailableQuantity, 0.001)

        // Editing the product must NOT cascade-delete batches or movements
        repository.saveProduct(
            product = created.product.copy(name = "Latte Fresco Alta Qualità"),
            localImagePath = null,
            initialBatch = null
        )

        val updated = db.productDao().getProductWithDetails(productId)
        assertNotNull(updated)
        assertEquals("Latte Fresco Alta Qualità", updated!!.product.name)
        assertEquals(1, updated.activeBatches.size)
        assertEquals("/tmp/test_milk.jpg", updated.primaryImagePath)

        // Removing the image explicitly clears primaryImagePath
        repository.saveProduct(
            product = updated.product,
            localImagePath = null,
            initialBatch = null,
            removeImage = true
        )
        val cleared = db.productDao().getProductWithDetails(productId)
        assertNotNull(cleared)
        assertNull(cleared!!.primaryImagePath)
        assertNull(cleared.product.imageId)
    }

    @Test
    fun `barcode lookup uses preferred defaultUnit when remote quantity has no explicit unit`() = runTest {
        db.categoryDao().insertAll(DatabaseSeeder.defaultCategories())
        db.settingsDao().upsert(SettingsEntity(id = 1, defaultUnit = UnitOfMeasure.GRAMS))

        val fakeApi = object : OpenFoodFactsApi {
            override suspend fun getProductByBarcode(barcode: String): OpenFoodFactsResponse {
                val qty = when (barcode) {
                    "8001" -> "original taste" // contains 'g' & 'l' letters inside words, must fallback!
                    "8002" -> "500 g"
                    else -> null
                }
                return OpenFoodFactsResponse(
                    code = barcode,
                    status = 1,
                    product = OpenFoodFactsProductDto(
                        productNameIt = "Prodotto Test $barcode",
                        quantity = qty
                    )
                )
            }
        }
        val barcodeRepo = BarcodeRepository(
            productDao = db.productDao(),
            categoryDao = db.categoryDao(),
            settingsDao = db.settingsDao(),
            openFoodFactsApi = fakeApi,
            imageStorageManager = ImageStorageManager(context, OkHttpClient())
        )

        val fallbackRes = barcodeRepo.lookupBarcode("8001")
        assertTrue(fallbackRes is BarcodeLookupResult.RemoteProduct)
        assertEquals(UnitOfMeasure.GRAMS, (fallbackRes as BarcodeLookupResult.RemoteProduct).suggestedUnit)

        val explicitRes = barcodeRepo.lookupBarcode("8002")
        assertTrue(explicitRes is BarcodeLookupResult.RemoteProduct)
        assertEquals(UnitOfMeasure.GRAMS, (explicitRes as BarcodeLookupResult.RemoteProduct).suggestedUnit)

        db.settingsDao().upsert(SettingsEntity(id = 1, defaultUnit = UnitOfMeasure.MILLILITERS))
        val blankRes = barcodeRepo.lookupBarcode("8003")
        assertTrue(blankRes is BarcodeLookupResult.RemoteProduct)
        assertEquals(UnitOfMeasure.MILLILITERS, (blankRes as BarcodeLookupResult.RemoteProduct).suggestedUnit)
    }

    @Test
    fun `expiry worker matches intermediate days and supports manual retrigger`() = runTest {
        db.storageLocationDao().insertAll(DatabaseSeeder.defaultLocations())
        db.categoryDao().insertAll(DatabaseSeeder.defaultCategories())
        db.settingsDao().upsert(
            SettingsEntity(
                id = 1,
                notificationsEnabled = true,
                notify7Days = true,
                notify3Days = true,
                notify1Day = true,
                notify0Days = true
            )
        )

        val today = PantryDateUtils.todayStartMillis()
        repository.saveProduct(
            product = ProductEntity(
                id = "prod_yogurt",
                name = "Yogurt Greco",
                categoryId = "cat_dairy",
                defaultLocationId = "loc_fridge",
                unit = UnitOfMeasure.PIECES
            ),
            initialBatch = NewBatchInput(
                quantity = 2.0,
                unit = UnitOfMeasure.PIECES,
                purchaseDate = today,
                expiryDate = PantryDateUtils.addDays(today, 5),
                expiryType = ExpiryType.USE_BY,
                locationId = "loc_fridge"
            )
        )

        val prefs = UserPreferencesDataStore(context)
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return ExpiryNotificationWorker(
                    appContext = appContext,
                    params = workerParameters,
                    batchDao = db.productBatchDao(),
                    reminderDao = db.reminderDao(),
                    settingsDao = db.settingsDao(),
                    preferencesDataStore = prefs
                )
            }
        }

        val firstWorker = TestListenableWorkerBuilder<ExpiryNotificationWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(ExpiryNotificationWorker.KEY_MANUAL_TRIGGER to false))
            .build()
        val firstRes = firstWorker.doWork()
        assertTrue(firstRes is ListenableWorker.Result.Success)
        assertEquals(1, (firstRes as ListenableWorker.Result.Success).outputData.getInt(ExpiryNotificationWorker.KEY_NOTIFIED_COUNT, 0))

        // Automatic second run should skip already-notified threshold
        val autoRepeatWorker = TestListenableWorkerBuilder<ExpiryNotificationWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(ExpiryNotificationWorker.KEY_MANUAL_TRIGGER to false))
            .build()
        val repeatRes = autoRepeatWorker.doWork() as ListenableWorker.Result.Success
        assertEquals(0, repeatRes.outputData.getInt(ExpiryNotificationWorker.KEY_NOTIFIED_COUNT, -1))

        // Manual trigger should still report active alert
        val manualWorker = TestListenableWorkerBuilder<ExpiryNotificationWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(ExpiryNotificationWorker.KEY_MANUAL_TRIGGER to true))
            .build()
        val manualRes = manualWorker.doWork() as ListenableWorker.Result.Success
        assertEquals(1, manualRes.outputData.getInt(ExpiryNotificationWorker.KEY_NOTIFIED_COUNT, 0))
    }
}
