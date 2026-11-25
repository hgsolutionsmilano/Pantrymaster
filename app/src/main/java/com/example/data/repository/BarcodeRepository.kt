package com.example.data.repository

import com.example.data.local.ImageStorageManager
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.SettingsDao
import com.example.data.local.db.DatabaseSeeder
import com.example.data.local.entity.ProductEntity
import com.example.data.remote.OpenFoodFactsApi
import com.example.domain.model.UnitOfMeasure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

sealed class BarcodeLookupResult {
    data class LocalProduct(val product: ProductEntity) : BarcodeLookupResult()
    data class RemoteProduct(
        val barcode: String,
        val name: String,
        val brand: String,
        val suggestedCategoryId: String,
        val description: String,
        val suggestedUnit: UnitOfMeasure,
        val imageUrl: String?,
        val localImagePath: String?
    ) : BarcodeLookupResult()
    data class NotFound(val barcode: String) : BarcodeLookupResult()
    data class NetworkError(val barcode: String, val message: String) : BarcodeLookupResult()
}

@Singleton
class BarcodeRepository @Inject constructor(
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val settingsDao: SettingsDao,
    private val openFoodFactsApi: OpenFoodFactsApi,
    private val imageStorageManager: ImageStorageManager
) {
    suspend fun lookupBarcode(rawBarcode: String): BarcodeLookupResult = withContext(Dispatchers.IO) {
        val barcode = rawBarcode.trim()
        if (barcode.isEmpty()) {
            return@withContext BarcodeLookupResult.NotFound(barcode)
        }

        val existing = productDao.getByBarcode(barcode)
        if (existing != null) {
            return@withContext BarcodeLookupResult.LocalProduct(existing)
        }

        try {
            val response = openFoodFactsApi.getProductByBarcode(barcode)
            val dto = response.product
            if (response.status != 1 || dto == null) {
                return@withContext BarcodeLookupResult.NotFound(barcode)
            }

            val name = dto.productNameIt?.takeIf { it.isNotBlank() }
                ?: dto.productName?.takeIf { it.isNotBlank() }
                ?: dto.genericNameIt?.takeIf { it.isNotBlank() }
                ?: dto.genericName?.takeIf { it.isNotBlank() }
                ?: ""

            if (name.isBlank()) {
                return@withContext BarcodeLookupResult.NotFound(barcode)
            }

            val brand = dto.brands.orEmpty().split(",").firstOrNull()?.trim().orEmpty()
            val imageUrl = dto.imageFrontUrl ?: dto.imageUrl
            val localPath = imageUrl?.let { imageStorageManager.downloadFromUrl(it) }
            val suggestedCategoryId = mapCategoryId(dto.categories, dto.categoriesTags)
            val defaultUnit = settingsDao.getSettings()?.defaultUnit ?: UnitOfMeasure.PIECES
            val suggestedUnit = inferUnit(dto.quantity, defaultUnit)

            BarcodeLookupResult.RemoteProduct(
                barcode = barcode,
                name = name.trim(),
                brand = brand,
                suggestedCategoryId = suggestedCategoryId,
                description = dto.quantity.orEmpty(),
                suggestedUnit = suggestedUnit,
                imageUrl = imageUrl,
                localImagePath = localPath
            )
        } catch (_: SocketTimeoutException) {
            BarcodeLookupResult.NetworkError(
                barcode = barcode,
                message = "Timeout di connessione con Open Food Facts. Puoi inserire il prodotto manualmente."
            )
        } catch (e: HttpException) {
            if (e.code() == 404) {
                BarcodeLookupResult.NotFound(barcode)
            } else {
                BarcodeLookupResult.NetworkError(
                    barcode = barcode,
                    message = "Risposta non valida dal servizio (${e.code()}). Inserisci manualmente."
                )
            }
        } catch (_: IOException) {
            BarcodeLookupResult.NetworkError(
                barcode = barcode,
                message = "Rete non disponibile. Inserisci i dati del prodotto manualmente."
            )
        } catch (_: Exception) {
            BarcodeLookupResult.NetworkError(
                barcode = barcode,
                message = "Impossibile leggere i dati remoti. Inserisci manualmente."
            )
        }
    }

    private suspend fun mapCategoryId(categoriesRaw: String?, tags: List<String>?): String {
        val text = (categoriesRaw.orEmpty() + " " + tags.orEmpty().joinToString(" ")).lowercase()
        val categories = categoryDao.getAll()
        val targetId = when {
            text.containsAny("milk", "dairy", "cheese", "yogurt", "latte", "formagg", "uova", "egg") -> "cat_dairy"
            text.containsAny("fruit", "vegetable", "produce", "frutta", "verdur", "insalat") -> "cat_produce"
            text.containsAny("meat", "fish", "seafood", "poultry", "carne", "pesce", "salum") -> "cat_meat"
            text.containsAny("pasta", "cereal", "bread", "rice", "flour", "farina", "riso", "pane", "bisc") -> "cat_grains"
            text.containsAny("canned", "conserv", "jar", "passata", "legum") -> "cat_canned"
            text.containsAny("beverage", "drink", "water", "juice", "coffee", "tea", "bevande", "succo", "vino", "birra") -> "cat_beverages"
            text.containsAny("frozen", "surgelat", "ice-cream", "gelat") -> "cat_frozen"
            text.containsAny("oil", "sauce", "vinegar", "spice", "olio", "condiment", "salsa", "aceto") -> "cat_condiments"
            text.containsAny("snack", "sweet", "chocolate", "candy", "dolc", "cioccol") -> "cat_snacks"
            else -> DatabaseSeeder.DEFAULT_CATEGORY_OTHER_ID
        }
        return categories.firstOrNull { it.id == targetId }?.id
            ?: categories.firstOrNull()?.id
            ?: DatabaseSeeder.DEFAULT_CATEGORY_OTHER_ID
    }

    private fun String.containsAny(vararg needles: String): Boolean = needles.any { this.contains(it) }

    private fun inferUnit(quantityStr: String?, fallbackUnit: UnitOfMeasure): UnitOfMeasure {
        val q = quantityStr?.lowercase()?.trim().orEmpty()
        if (q.isBlank()) return fallbackUnit
        return when {
            REGEX_ML.containsMatchIn(q) -> UnitOfMeasure.MILLILITERS
            REGEX_LITERS.containsMatchIn(q) -> UnitOfMeasure.LITERS
            REGEX_KG.containsMatchIn(q) -> UnitOfMeasure.KILOGRAMS
            REGEX_GRAMS.containsMatchIn(q) -> UnitOfMeasure.GRAMS
            REGEX_PIECES.containsMatchIn(q) -> UnitOfMeasure.PIECES
            else -> fallbackUnit
        }
    }

    companion object {
        private val REGEX_ML = Regex("""(?<![a-z])ml\b""")
        private val REGEX_LITERS = Regex("""(?<![a-z])(l|lt|cl)\b""")
        private val REGEX_KG = Regex("""(?<![a-z])kg\b""")
        private val REGEX_GRAMS = Regex("""(?<![a-z])(g|gr)\b""")
        private val REGEX_PIECES = Regex("""(?<![a-z])(pz|pezzi|pcs|uova|eggs?)\b""")
    }
}
