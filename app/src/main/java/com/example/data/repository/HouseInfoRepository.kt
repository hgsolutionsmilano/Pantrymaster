package com.example.data.repository

import android.net.Uri
import com.example.data.local.ImageStorageManager
import com.example.data.local.dao.HouseInfoDao
import com.example.data.local.entity.HouseInfoEntryEntity
import com.example.data.local.entity.HouseInfoPhotoEntity
import com.example.data.local.relation.HouseInfoWithPhotos
import com.example.domain.model.HouseInfoCategory
import com.example.domain.model.MeasurementUnit
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class HouseInfoPhotoDraft(
    val id: String? = null,
    val localFilePath: String,
    val caption: String = ""
)

@Singleton
class HouseInfoRepository @Inject constructor(
    private val houseInfoDao: HouseInfoDao,
    private val imageStorageManager: ImageStorageManager
) {
    fun observeAll(): Flow<List<HouseInfoWithPhotos>> = houseInfoDao.observeAllWithPhotos()

    fun observePinned(): Flow<List<HouseInfoWithPhotos>> = houseInfoDao.observePinnedWithPhotos()

    fun observeById(id: String): Flow<HouseInfoWithPhotos?> = houseInfoDao.observeByIdWithPhotos(id)

    suspend fun saveEntry(
        entryId: String?,
        category: HouseInfoCategory,
        title: String,
        subtitle: String,
        primaryValue: String,
        secondaryValue: String,
        secretValue: String,
        widthValue: String,
        heightValue: String,
        depthValue: String,
        measurementUnit: MeasurementUnit,
        notes: String,
        isPinned: Boolean,
        photos: List<HouseInfoPhotoDraft>
    ): String {
        val now = System.currentTimeMillis()
        val id = entryId ?: UUID.randomUUID().toString()
        val existing = if (entryId != null) houseInfoDao.getByIdWithPhotos(entryId) else null

        val entity = HouseInfoEntryEntity(
            id = id,
            category = category,
            title = title.trim(),
            subtitle = subtitle.trim(),
            primaryValue = primaryValue.trim(),
            secondaryValue = secondaryValue.trim(),
            secretValue = secretValue.trim(),
            widthValue = widthValue.trim(),
            heightValue = heightValue.trim(),
            depthValue = depthValue.trim(),
            measurementUnit = measurementUnit,
            notes = notes.trim(),
            isPinned = isPinned,
            createdAt = existing?.entry?.createdAt ?: now,
            updatedAt = now
        )

        val photoEntities = photos.mapIndexed { index, draft ->
            HouseInfoPhotoEntity(
                id = draft.id ?: UUID.randomUUID().toString(),
                entryId = id,
                localFilePath = draft.localFilePath,
                caption = draft.caption.trim(),
                sortOrder = index,
                createdAt = now
            )
        }

        val keptPaths = photoEntities.map { it.localFilePath }.toSet()
        existing?.photos
            ?.filter { it.localFilePath !in keptPaths }
            ?.forEach { removed -> imageStorageManager.deleteImageFile(removed.localFilePath) }

        houseInfoDao.saveEntryWithPhotos(entity, photoEntities)
        return id
    }

    suspend fun togglePinned(entryId: String, currentlyPinned: Boolean) {
        houseInfoDao.setPinned(
            id = entryId,
            pinned = !currentlyPinned,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteEntry(entryId: String) {
        val existing = houseInfoDao.getByIdWithPhotos(entryId)
        existing?.photos?.forEach { photo ->
            imageStorageManager.deleteImageFile(photo.localFilePath)
        }
        houseInfoDao.deleteEntryById(entryId)
    }

    suspend fun copyPickedImage(uri: Uri): String? = imageStorageManager.copyFromUri(uri)

    fun deleteUnusedPhotoFile(path: String?) {
        imageStorageManager.deleteImageFile(path)
    }
}
