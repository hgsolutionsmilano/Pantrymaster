package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.relation.CategorySpendAggregate
import com.example.data.local.relation.MovementWithProduct
import com.example.data.local.relation.TopUsedProductAggregate
import com.example.domain.model.MovementType
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Transaction
    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC")
    fun observeAllWithProduct(): Flow<List<MovementWithProduct>>

    @Transaction
    @Query("SELECT * FROM stock_movements WHERE product_id = :productId ORDER BY timestamp DESC")
    fun observeForProduct(productId: String): Flow<List<MovementWithProduct>>

    @Query("SELECT * FROM stock_movements WHERE timestamp BETWEEN :fromMillis AND :toMillis ORDER BY timestamp ASC")
    fun observeInRange(fromMillis: Long, toMillis: Long): Flow<List<StockMovementEntity>>

    @Query(
        """
        SELECT c.id AS categoryId,
               c.name AS categoryName,
               c.color_hex AS categoryColor,
               COALESCE(SUM(CASE WHEN sm.type = 'CONSUME' THEN sm.quantity ELSE 0 END), 0.0) AS totalConsumed,
               COALESCE(SUM(CASE WHEN sm.type = 'WASTE' THEN sm.quantity ELSE 0 END), 0.0) AS totalWasted,
               COALESCE(SUM(CASE WHEN sm.type IN ('PURCHASE', 'MANUAL_ADD') THEN sm.quantity ELSE 0 END), 0.0) AS totalPurchased,
               COALESCE(SUM(CASE WHEN sm.type IN ('PURCHASE', 'MANUAL_ADD') THEN COALESCE(sm.price_amount, 0.0) ELSE 0.0 END), 0.0) AS totalSpend
        FROM stock_movements sm
        INNER JOIN products p ON p.id = sm.product_id
        INNER JOIN categories c ON c.id = p.category_id
        WHERE sm.timestamp BETWEEN :fromMillis AND :toMillis
        GROUP BY c.id, c.name, c.color_hex
        ORDER BY (totalConsumed + totalPurchased) DESC
        """
    )
    fun observeCategoryAggregates(fromMillis: Long, toMillis: Long): Flow<List<CategorySpendAggregate>>

    @Query(
        """
        SELECT p.id AS productId,
               p.name AS productName,
               COALESCE(SUM(sm.quantity), 0.0) AS totalConsumed,
               COUNT(sm.id) AS movementCount
        FROM stock_movements sm
        INNER JOIN products p ON p.id = sm.product_id
        WHERE sm.type = 'CONSUME' AND sm.timestamp BETWEEN :fromMillis AND :toMillis
        GROUP BY p.id, p.name
        ORDER BY totalConsumed DESC, movementCount DESC
        LIMIT :limit
        """
    )
    fun observeTopUsedProducts(fromMillis: Long, toMillis: Long, limit: Int = 8): Flow<List<TopUsedProductAggregate>>

    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC")
    suspend fun getAll(): List<StockMovementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movement: StockMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movements: List<StockMovementEntity>)

    @Query("DELETE FROM stock_movements")
    suspend fun deleteAll()
}
