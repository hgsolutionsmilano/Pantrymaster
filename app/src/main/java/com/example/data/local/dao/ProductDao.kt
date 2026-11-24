package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductImageEntity
import com.example.data.local.relation.ProductWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Transaction
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun observeAllWithDetails(): Flow<List<ProductWithDetails>>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    fun observeProductWithDetails(productId: String): Flow<ProductWithDetails?>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductWithDetails(productId: String): ProductWithDetails?

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductEntity?

    @Transaction
    @Query("SELECT * FROM products ORDER BY created_at DESC LIMIT :limit")
    fun observeRecentProducts(limit: Int): Flow<List<ProductWithDetails>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Update
    suspend fun update(product: ProductEntity)

    @Query("UPDATE products SET category_id = :targetCategoryId WHERE category_id = :sourceCategoryId")
    suspend fun reassignCategory(sourceCategoryId: String, targetCategoryId: String)

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteById(productId: String)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface ProductImageDao {
    @Query("SELECT * FROM product_images")
    suspend fun getAll(): List<ProductImageEntity>

    @Query("SELECT * FROM product_images WHERE product_id = :productId")
    suspend fun getForProduct(productId: String): List<ProductImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(image: ProductImageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<ProductImageEntity>)

    @Query("DELETE FROM product_images WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM product_images WHERE product_id = :productId")
    suspend fun deleteForProduct(productId: String)

    @Query("DELETE FROM product_images")
    suspend fun deleteAll()
}
