package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.ShoppingItemEntity
import com.example.data.local.entity.ShoppingListEntity
import com.example.data.local.relation.ShoppingListWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Transaction
    @Query("SELECT * FROM shopping_lists WHERE is_archived = 0 ORDER BY created_at DESC")
    fun observeActiveListsWithItems(): Flow<List<ShoppingListWithItems>>

    @Query("SELECT * FROM shopping_lists ORDER BY created_at DESC")
    suspend fun getAllLists(): List<ShoppingListEntity>

    @Query("SELECT * FROM shopping_items")
    suspend fun getAllItems(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): ShoppingItemEntity?

    @Query("SELECT COUNT(*) FROM shopping_items si INNER JOIN shopping_lists sl ON sl.id = si.list_id WHERE sl.is_archived = 0 AND si.is_purchased = 0")
    fun observePendingItemsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(list: ShoppingListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLists(lists: List<ShoppingListEntity>)

    @Query("DELETE FROM shopping_lists WHERE id = :listId")
    suspend fun deleteListById(listId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Update
    suspend fun updateItem(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: String)

    @Query("DELETE FROM shopping_items WHERE list_id = :listId AND is_purchased = 1")
    suspend fun clearPurchasedInList(listId: String)

    @Query("DELETE FROM shopping_lists")
    suspend fun deleteAllLists()
}
