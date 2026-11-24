package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.ImageStorageManager
import com.example.data.local.dao.ProductBatchDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ProductImageDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.StockMovementDao
import com.example.data.local.db.DatabaseSeeder
import com.example.data.local.db.PantryDatabase
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductImageEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.relation.BatchWithProduct
import com.example.data.local.relation.MovementWithProduct
import com.example.data.local.relation.ProductWithDetails
import com.example.domain.model.ExpiryType
import com.example.domain.model.MovementType
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.BatchAllocationInput
import com.example.domain.usecase.FefoConsumptionCalculator
import com.example.domain.usecase.PantryDateUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class NewBatchInput(
    val quantity: Double,
    val unit: UnitOfMeasure,
    val purchaseDate: Long = PantryDateUtils.todayStartMillis(),
    val expiryDate: Long? = null,
    val expiryType: ExpiryType = ExpiryType.USE_BY,
    val locationId: String = DatabaseSeeder.DEFAULT_LOCATION_PANTRY_ID,
    val purchasePrice: Double? = null,
    val notes: String = "",
    val movementType: MovementType = MovementType.PURCHASE
)

sealed class InventoryOperationResult {
    data object Success : InventoryOperationResult()
    data class Error(val message: String) : InventoryOperationResult()
}

@Singleton
class PantryRepository @Inject constructor(
    private val database: PantryDatabase,
    private val productDao: ProductDao,
    private val productImageDao: ProductImageDao,
    private val batchDao: ProductBatchDao,
    private val movementDao: StockMovementDao,
    private val reminderDao: ReminderDao,
    private val imageStorageManager: ImageStorageManager
) {
    fun observeAllProducts(): Flow<List<ProductWithDetails>> = productDao.observeAllWithDetails()

    fun observeProduct(productId: String): Flow<ProductWithDetails?> =
        productDao.observeProductWithDetails(productId)

    fun observeRecentProducts(limit: Int = 6): Flow<List<ProductWithDetails>> =
        productDao.observeRecentProducts(limit)

    fun observeActiveBatches(): Flow<List<BatchWithProduct>> =
        batchDao.observeActiveBatchesWithProduct()

    fun observeBatchesForProduct(productId: String): Flow<List<ProductBatchEntity>> =
        batchDao.observeBatchesForProduct(productId)

    fun observeAllMovements(): Flow<List<MovementWithProduct>> =
        movementDao.observeAllWithProduct()

    fun observeMovementsForProduct(productId: String): Flow<List<MovementWithProduct>> =
        movementDao.observeForProduct(productId)

    suspend fun findProductByBarcode(barcode: String): ProductEntity? =
        productDao.getByBarcode(barcode.trim())

    suspend fun saveProduct(
        product: ProductEntity,
        localImagePath: String? = null,
        sourceImageUrl: String? = null,
        initialBatch: NewBatchInput? = null
    ): String {
        var finalProductId = product.id.ifBlank { UUID.randomUUID().toString() }
        database.withTransaction {
            val existing = productDao.getById(finalProductId)
            var imageId = existing?.imageId

            if (!localImagePath.isNullOrBlank()) {
                val oldImages = productImageDao.getForProduct(finalProductId)
                oldImages.forEach { old ->
                    if (old.localFilePath != localImagePath) {
                        imageStorageManager.deleteImageFile(old.localFilePath)
                    }
                }
                productImageDao.deleteForProduct(finalProductId)
                val newImage = ProductImageEntity(
                    id = UUID.randomUUID().toString(),
                    productId = finalProductId,
                    localFilePath = localImagePath,
                    sourceUrl = sourceImageUrl
                )
                productImageDao.upsert(newImage)
                imageId = newImage.id
            }

            val normalized = product.copy(
                id = finalProductId,
                barcode = product.barcode?.trim()?.takeIf { it.isNotEmpty() },
                imageId = imageId,
                minStockQuantity = product.minStockQuantity.coerceAtLeast(0.0),
                updatedAt = System.currentTimeMillis()
            )
            productDao.upsert(normalized)

            if (initialBatch != null && initialBatch.quantity > 0.0) {
                insertBatchAndMovementLocked(finalProductId, initialBatch)
            }
        }
        return finalProductId
    }

    suspend fun addBatch(productId: String, input: NewBatchInput): InventoryOperationResult {
        if (input.quantity <= 0.0) {
            return InventoryOperationResult.Error("La quantità del lotto deve essere maggiore di zero.")
        }
        return database.withTransaction {
            insertBatchAndMovementLocked(productId, input)
            InventoryOperationResult.Success
        }
    }

    private suspend fun insertBatchAndMovementLocked(productId: String, input: NewBatchInput) {
        val batchId = UUID.randomUUID().toString()
        val batch = ProductBatchEntity(
            id = batchId,
            productId = productId,
            initialQuantity = input.quantity,
            remainingQuantity = input.quantity,
            unit = input.unit,
            purchaseDate = input.purchaseDate,
            expiryDate = input.expiryDate,
            expiryType = input.expiryType,
            locationId = input.locationId,
            purchasePrice = input.purchasePrice?.takeIf { it >= 0.0 },
            notes = input.notes,
            isExhausted = false
        )
        batchDao.upsert(batch)

        val movementType = if (input.movementType == MovementType.PURCHASE) {
            MovementType.PURCHASE
        } else {
            MovementType.MANUAL_ADD
        }
        movementDao.insert(
            StockMovementEntity(
                id = UUID.randomUUID().toString(),
                productId = productId,
                batchId = batchId,
                type = movementType,
                quantity = input.quantity,
                unit = input.unit,
                priceAmount = input.purchasePrice?.takeIf { it >= 0.0 },
                toLocationId = input.locationId,
                description = input.notes.ifBlank {
                    if (movementType == MovementType.PURCHASE) "Carico nuovo lotto acquistato"
                    else "Carico manuale nuovo lotto"
                }
            )
        )
    }

    suspend fun consumeProductFefo(
        productId: String,
        requestedQuantity: Double,
        description: String = "Consumo registrato (FEFO)"
    ): InventoryOperationResult {
        if (requestedQuantity <= 0.0) {
            return InventoryOperationResult.Error("La quantità da consumare deve essere positiva.")
        }
        return database.withTransaction {
            val activeBatches = batchDao.getActiveBatchesForProductFefo(productId)
            val totalAvailable = activeBatches.sumOf { it.remainingQuantity }
            if (totalAvailable + 1e-6 < requestedQuantity) {
                return@withTransaction InventoryOperationResult.Error(
                    "Disponibilità insufficiente in dispensa."
                )
            }
            val result = FefoConsumptionCalculator.allocate(
                batches = activeBatches.map {
                    BatchAllocationInput(
                        batchId = it.id,
                        remainingQuantity = it.remainingQuantity,
                        expiryDateMillis = it.expiryDate,
                        createdAtMillis = it.createdAt
                    )
                },
                requestedQuantity = requestedQuantity
            )
            val batchById = activeBatches.associateBy { it.id }
            for (alloc in result.allocations) {
                val batch = batchById[alloc.batchId] ?: continue
                batchDao.update(
                    batch.copy(
                        remainingQuantity = alloc.newRemainingQuantity,
                        isExhausted = alloc.isExhausted
                    )
                )
                movementDao.insert(
                    StockMovementEntity(
                        id = UUID.randomUUID().toString(),
                        productId = productId,
                        batchId = batch.id,
                        type = MovementType.CONSUME,
                        quantity = alloc.consumedQuantity,
                        unit = batch.unit,
                        fromLocationId = batch.locationId,
                        description = description
                    )
                )
                if (alloc.isExhausted) {
                    reminderDao.deleteForBatch(batch.id)
                }
            }
            InventoryOperationResult.Success
        }
    }

    suspend fun consumeOrWasteBatch(
        batchId: String,
        quantity: Double,
        isWaste: Boolean,
        description: String = ""
    ): InventoryOperationResult {
        if (quantity <= 0.0) {
            return InventoryOperationResult.Error("La quantità deve essere maggiore di zero.")
        }
        return database.withTransaction {
            val batch = batchDao.getById(batchId)
                ?: return@withTransaction InventoryOperationResult.Error("Lotto non trovato.")
            if (batch.remainingQuantity + 1e-6 < quantity) {
                return@withTransaction InventoryOperationResult.Error("Quantità superiore alla residua.")
            }
            val updatedRemaining = (batch.remainingQuantity - quantity).coerceAtLeast(0.0)
            val exhausted = updatedRemaining <= 1e-6
            batchDao.update(
                batch.copy(
                    remainingQuantity = if (exhausted) 0.0 else updatedRemaining,
                    isExhausted = exhausted
                )
            )
            val type = if (isWaste) MovementType.WASTE else MovementType.CONSUME
            movementDao.insert(
                StockMovementEntity(
                    id = UUID.randomUUID().toString(),
                    productId = batch.productId,
                    batchId = batch.id,
                    type = type,
                    quantity = quantity,
                    unit = batch.unit,
                    fromLocationId = batch.locationId,
                    description = description.ifBlank {
                        if (isWaste) "Prodotto scartato" else "Consumo da lotto"
                    }
                )
            )
            if (exhausted) {
                reminderDao.deleteForBatch(batch.id)
            }
            InventoryOperationResult.Success
        }
    }

    suspend fun adjustBatchQuantity(
        batchId: String,
        newQuantity: Double,
        description: String = "Rettifica inventario"
    ): InventoryOperationResult {
        if (newQuantity < 0.0) {
            return InventoryOperationResult.Error("La quantità non può essere negativa.")
        }
        return database.withTransaction {
            val batch = batchDao.getById(batchId)
                ?: return@withTransaction InventoryOperationResult.Error("Lotto non trovato.")
            val delta = kotlin.math.abs(newQuantity - batch.remainingQuantity)
            if (delta <= 1e-6) return@withTransaction InventoryOperationResult.Success
            val exhausted = newQuantity <= 1e-6
            batchDao.update(
                batch.copy(
                    remainingQuantity = if (exhausted) 0.0 else newQuantity,
                    isExhausted = exhausted
                )
            )
            movementDao.insert(
                StockMovementEntity(
                    id = UUID.randomUUID().toString(),
                    productId = batch.productId,
                    batchId = batch.id,
                    type = MovementType.ADJUSTMENT,
                    quantity = delta,
                    unit = batch.unit,
                    fromLocationId = batch.locationId,
                    description = "$description (${batch.remainingQuantity} -> $newQuantity)"
                )
            )
            if (exhausted) {
                reminderDao.deleteForBatch(batch.id)
            }
            InventoryOperationResult.Success
        }
    }

    suspend fun transferBatchLocation(
        batchId: String,
        toLocationId: String,
        description: String = "Trasferimento posizione"
    ): InventoryOperationResult {
        return database.withTransaction {
            val batch = batchDao.getById(batchId)
                ?: return@withTransaction InventoryOperationResult.Error("Lotto non trovato.")
            if (batch.locationId == toLocationId) {
                return@withTransaction InventoryOperationResult.Success
            }
            val fromLoc = batch.locationId
            batchDao.update(batch.copy(locationId = toLocationId))
            movementDao.insert(
                StockMovementEntity(
                    id = UUID.randomUUID().toString(),
                    productId = batch.productId,
                    batchId = batch.id,
                    type = MovementType.TRANSFER,
                    quantity = batch.remainingQuantity,
                    unit = batch.unit,
                    fromLocationId = fromLoc,
                    toLocationId = toLocationId,
                    description = description
                )
            )
            InventoryOperationResult.Success
        }
    }

    suspend fun updateBatchDetails(
        batchId: String,
        expiryDate: Long?,
        expiryType: ExpiryType,
        purchasePrice: Double?,
        notes: String
    ): InventoryOperationResult {
        return database.withTransaction {
            val batch = batchDao.getById(batchId)
                ?: return@withTransaction InventoryOperationResult.Error("Lotto non trovato.")
            val dateChanged = batch.expiryDate != expiryDate
            batchDao.update(
                batch.copy(
                    expiryDate = expiryDate,
                    expiryType = expiryType,
                    purchasePrice = purchasePrice?.takeIf { it >= 0.0 },
                    notes = notes
                )
            )
            if (dateChanged) {
                reminderDao.deleteForBatch(batchId)
            }
            InventoryOperationResult.Success
        }
    }

    suspend fun cancelBatch(
        batchId: String,
        description: String = "Lotto annullato"
    ): InventoryOperationResult {
        return database.withTransaction {
            val batch = batchDao.getById(batchId)
                ?: return@withTransaction InventoryOperationResult.Error("Lotto non trovato.")
            val qty = batch.remainingQuantity
            batchDao.update(batch.copy(remainingQuantity = 0.0, isExhausted = true))
            if (qty > 0.0) {
                movementDao.insert(
                    StockMovementEntity(
                        id = UUID.randomUUID().toString(),
                        productId = batch.productId,
                        batchId = batch.id,
                        type = MovementType.BATCH_CANCEL,
                        quantity = qty,
                        unit = batch.unit,
                        fromLocationId = batch.locationId,
                        description = description
                    )
                )
            }
            reminderDao.deleteForBatch(batchId)
            InventoryOperationResult.Success
        }
    }

    suspend fun deleteProduct(productId: String) {
        database.withTransaction {
            val images = productImageDao.getForProduct(productId)
            images.forEach { imageStorageManager.deleteImageFile(it.localFilePath) }
            productDao.deleteById(productId)
        }
    }
}
