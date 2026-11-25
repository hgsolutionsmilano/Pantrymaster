package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.HouseInfoEntryEntity
import com.example.data.local.entity.HouseInfoPhotoEntity
import com.example.data.local.relation.HouseInfoWithPhotos
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseInfoDao {
    @Transaction
    @Query("SELECT * FROM house_info_entries ORDER BY is_pinned DESC, updated_at DESC")
    fun observeAllWithPhotos(): Flow<List<HouseInfoWithPhotos>>

    @Transaction
    @Query("SELECT * FROM house_info_entries WHERE is_pinned = 1 ORDER BY updated_at DESC")
    fun observePinnedWithPhotos(): Flow<List<HouseInfoWithPhotos>>

    @Transaction
    @Query("SELECT * FROM house_info_entries WHERE id = :id LIMIT 1")
    fun observeByIdWithPhotos(id: String): Flow<HouseInfoWithPhotos?>

    @Transaction
    @Query("SELECT * FROM house_info_entries WHERE id = :id LIMIT 1")
    suspend fun getByIdWithPhotos(id: String): HouseInfoWithPhotos?

    @Query("SELECT * FROM house_info_entries ORDER BY is_pinned DESC, updated_at DESC")
    suspend fun getAllEntries(): List<HouseInfoEntryEntity>

    @Query("SELECT * FROM house_info_photos ORDER BY sort_order ASC")
    suspend fun getAllPhotos(): List<HouseInfoPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: HouseInfoEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<HouseInfoEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<HouseInfoPhotoEntity>)

    @Query("DELETE FROM house_info_photos WHERE entry_id = :entryId")
    suspend fun deletePhotosForEntry(entryId: String)

    @Query("UPDATE house_info_entries SET is_pinned = :pinned, updated_at = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long)

    @Query("DELETE FROM house_info_entries WHERE id = :id")
    suspend fun deleteEntryById(id: String)

    @Query("DELETE FROM house_info_entries")
    suspend fun deleteAllEntries()

    @Transaction
    suspend fun saveEntryWithPhotos(
        entry: HouseInfoEntryEntity,
        photos: List<HouseInfoPhotoEntity>
    ) {
        upsertEntry(entry)
        deletePhotosForEntry(entry.id)
        if (photos.isNotEmpty()) {
            insertPhotos(photos)
        }
    }
}
