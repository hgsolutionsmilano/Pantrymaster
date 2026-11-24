package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.ImageStorageManager
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
import com.example.data.local.db.DatabaseSeeder
import com.example.data.local.db.PantryDatabase
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductImageEntity
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeIngredientEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SettingsEntity
import com.example.data.local.entity.ShoppingItemEntity
import com.example.data.local.entity.ShoppingListEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.StorageLocationEntity
import com.example.data.preferences.UserPreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PantryBackupPayload(
    val schemaVersion: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val categories: List<CategoryEntity>,
    val storageLocations: List<StorageLocationEntity>,
    val products: List<ProductEntity>,
    val productImages: List<ProductImageEntity>,
    val batches: List<ProductBatchEntity>,
    val movements: List<StockMovementEntity>,
    val shoppingLists: List<ShoppingListEntity>,
    val shoppingItems: List<ShoppingItemEntity>,
    val recipes: List<RecipeEntity>,
    val recipeIngredients: List<RecipeIngredientEntity>,
    val reminders: List<ReminderEntity>,
    val settings: SettingsEntity
)

sealed class BackupOperationResult {
    data class Success(val message: String) : BackupOperationResult()
    data class Error(val message: String) : BackupOperationResult()
}

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: PantryDatabase,
    private val categoryDao: CategoryDao,
    private val locationDao: StorageLocationDao,
    private val productDao: ProductDao,
    private val productImageDao: ProductImageDao,
    private val batchDao: ProductBatchDao,
    private val movementDao: StockMovementDao,
    private val shoppingDao: ShoppingDao,
    private val recipeDao: RecipeDao,
    private val reminderDao: ReminderDao,
    private val settingsDao: SettingsDao,
    private val imageStorageManager: ImageStorageManager,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val json: Json
) {
    private suspend fun buildPayload(): PantryBackupPayload {
        return PantryBackupPayload(
            categories = categoryDao.getAll(),
            storageLocations = locationDao.getAll(),
            products = productDao.getAll(),
            productImages = productImageDao.getAll(),
            batches = batchDao.getAll(),
            movements = movementDao.getAll(),
            shoppingLists = shoppingDao.getAllLists(),
            shoppingItems = shoppingDao.getAllItems(),
            recipes = recipeDao.getAllRecipes(),
            recipeIngredients = recipeDao.getAllIngredients(),
            reminders = reminderDao.getAll(),
            settings = settingsDao.getSettings() ?: SettingsEntity(id = 1)
        )
    }

    suspend fun exportJsonToUri(uri: Uri): BackupOperationResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildPayload()
            val text = json.encodeToString(PantryBackupPayload.serializer(), payload)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            } ?: return@withContext BackupOperationResult.Error("Impossibile aprire il file di destinazione.")
            BackupOperationResult.Success(
                "Esportazione JSON completata (${payload.products.size} prodotti, ${payload.batches.size} lotti)."
            )
        }.getOrElse { e ->
            BackupOperationResult.Error("Errore esportazione JSON: ${e.localizedMessage ?: "sconosciuto"}")
        }
    }

    suspend fun exportFullBackupZipToUri(uri: Uri): BackupOperationResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildPayload()
            val jsonBytes = json.encodeToString(PantryBackupPayload.serializer(), payload)
                .toByteArray(Charsets.UTF_8)
            val images = imageStorageManager.listAllImageFiles()

            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("pantrymaster_data.json"))
                    zip.write(jsonBytes)
                    zip.closeEntry()

                    for (file in images) {
                        if (file.isFile) {
                            zip.putNextEntry(ZipEntry("images/${file.name}"))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            } ?: return@withContext BackupOperationResult.Error("Impossibile creare l'archivio di backup.")

            BackupOperationResult.Success(
                "Backup completo salvato (${payload.products.size} prodotti, ${images.size} immagini)."
            )
        }.getOrElse { e ->
            BackupOperationResult.Error("Errore creazione backup: ${e.localizedMessage ?: "sconosciuto"}")
        }
    }

    suspend fun importJsonFromUri(uri: Uri): BackupOperationResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext BackupOperationResult.Error("Impossibile leggere il file selezionato.")
            val payload = json.decodeFromString(PantryBackupPayload.serializer(), text)
            restorePayload(payload)
            BackupOperationResult.Success(
                "Importazione completata: ${payload.products.size} prodotti, ${payload.recipes.size} ricette."
            )
        }.getOrElse { e ->
            BackupOperationResult.Error("File JSON non valido: ${e.localizedMessage ?: "errore formato"}")
        }
    }

    suspend fun importFullBackupZipFromUri(uri: Uri): BackupOperationResult = withContext(Dispatchers.IO) {
        runCatching {
            var jsonString: String? = null
            val restoredImagesDir = imageStorageManager.getImagesDir()
            val restoredPaths = mutableMapOf<String, String>()

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory) {
                            if (name == "pantrymaster_data.json") {
                                val buffer = ByteArrayOutputStream()
                                zip.copyTo(buffer)
                                jsonString = buffer.toString("UTF-8")
                            } else if (name.startsWith("images/")) {
                                val fileName = File(name).name
                                val outFile = File(restoredImagesDir, fileName)
                                FileOutputStream(outFile).use { fos ->
                                    zip.copyTo(fos)
                                }
                                restoredPaths[fileName] = outFile.absolutePath
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return@withContext BackupOperationResult.Error("Impossibile leggere l'archivio selezionato.")

            val rawJson = jsonString
                ?: return@withContext BackupOperationResult.Error("Archivio di backup non valido (dati mancanti).")
            val decoded = json.decodeFromString(PantryBackupPayload.serializer(), rawJson)

            // Remap image paths to the current app filesDir/images path
            val remappedImages = decoded.productImages.map { img ->
                val fileName = File(img.localFilePath).name
                val updated = restoredPaths[fileName] ?: img.localFilePath
                img.copy(localFilePath = updated)
            }
            val remappedRecipes = decoded.recipes.map { rec ->
                val path = rec.imagePath ?: return@map rec
                val fileName = File(path).name
                rec.copy(imagePath = restoredPaths[fileName] ?: path)
            }

            restorePayload(decoded.copy(productImages = remappedImages, recipes = remappedRecipes))
            BackupOperationResult.Success(
                "Ripristino completato (${decoded.products.size} prodotti, ${restoredPaths.size} immagini)."
            )
        }.getOrElse { e ->
            BackupOperationResult.Error("Errore ripristino backup: ${e.localizedMessage ?: "errore"}")
        }
    }

    private suspend fun restorePayload(payload: PantryBackupPayload) {
        val locations = payload.storageLocations.ifEmpty { DatabaseSeeder.defaultLocations() }
        val categories = payload.categories.ifEmpty { DatabaseSeeder.defaultCategories() }

        database.withTransaction {
            shoppingDao.deleteAllLists()
            recipeDao.deleteAllRecipes()
            reminderDao.deleteAll()
            movementDao.deleteAll()
            batchDao.deleteAll()
            productImageDao.deleteAll()
            productDao.deleteAll()

            locationDao.insertAll(locations)
            categoryDao.insertAll(categories)
            productDao.insertAll(payload.products)
            productImageDao.insertAll(payload.productImages)
            batchDao.insertAll(payload.batches)
            movementDao.insertAll(payload.movements)
            shoppingDao.insertLists(payload.shoppingLists)
            shoppingDao.insertItems(payload.shoppingItems)
            recipeDao.insertRecipes(payload.recipes)
            recipeDao.insertIngredients(payload.recipeIngredients)
            reminderDao.insertAll(payload.reminders)
            settingsDao.upsert(payload.settings)
        }
        preferencesDataStore.setThemePreference(payload.settings.themeMode)
    }
}
