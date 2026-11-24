package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.relation.BatchWithProduct
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductBatchDao {
    @Transaction
    @Query(
        """
        SELECT * FROM product_batches 
        WHERE is_exhausted = 0 AND remaining_quantity > 0 
        ORDER BY CASE WHEN expiry_date IS NULL THEN 1 ELSE 0 END ASC, expiry_date ASC, created_at ASC
        """
    )
    fun observeActiveBatchesWithProduct(): Flow<List<BatchWithProduct>>

    @Transaction
    @Query(
        """
        SELECT * FROM product_batches 
        WHERE product_id = :productId 
        ORDER BY is_exhausted ASC, CASE WHEN expiry_date IS NULL THEN 1 ELSE 0 END ASC, expiry_date ASC, created_at ASC
        """
    )
    fun observeBatchesForProduct(productId: String): Flow<List<ProductBatchEntity>>

    @Query(
        """
        SELECT * FROM product_batches 
        WHERE product_id = :productId AND is_exhausted = 0 AND remaining_quantity > 0 
        ORDER BY CASE WHEN expiry_date IS NULL THEN 1 ELSE 0 END ASC, expiry_date ASC, created_at ASC
        """
    )
    suspend fun getActiveBatchesForProductFefo(productId: String): List<ProductBatchEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM product_batches 
        WHERE is_exhausted = 0 AND remaining_quantity > 0 AND expiry_date IS NOT NULL
        ORDER BY expiry_date ASC
        """
    )
    suspend fun getActiveBatchesWithExpiry(): List<BatchWithProduct>

    @Query("SELECT * FROM product_batches WHERE id = :batchId LIMIT 1")
    suspend fun getById(batchId: String): ProductBatchEntity?

    @Query("SELECT * FROM product_batches")
    suspend fun getAll(): List<ProductBatchEntity>

    @Query("SELECT COALESCE(SUM(remaining_quantity), 0.0) FROM product_batches WHERE product_id = :productId AND is_exhausted = 0")
    suspend fun getAvailableQuantityForProduct(productId: String): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(batch: ProductBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(batches: List<ProductBatchEntity>)

    @Update
    suspend fun update(batch: ProductBatchEntity)

    @Query("DELETE FROM product_batches")
    suspend fun deleteAll()
}
