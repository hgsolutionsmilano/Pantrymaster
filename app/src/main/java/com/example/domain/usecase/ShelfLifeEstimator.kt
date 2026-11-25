package com.example.domain.usecase

import com.example.data.local.entity.ProductBatchEntity
import com.example.domain.model.ExpiryType
import kotlin.math.roundToInt

data class ShelfLifeEstimate(
    val averageDays: Int,
    val sampleCount: Int,
    val estimatedExpiryMillis: Long,
    val dominantExpiryType: ExpiryType
)

object ShelfLifeEstimator {
    private const val MIN_VALID_DAYS = 1L
    private const val MAX_VALID_DAYS = 3650L

    fun estimateFromBatches(
        batches: List<ProductBatchEntity>,
        todayMillis: Long = PantryDateUtils.todayStartMillis()
    ): ShelfLifeEstimate? {
        val validSamples = batches.mapNotNull { batch ->
            val expiry = batch.expiryDate ?: return@mapNotNull null
            val days = PantryDateUtils.daysBetween(batch.purchaseDate, expiry)
            if (days in MIN_VALID_DAYS..MAX_VALID_DAYS) {
                days.toInt() to batch.expiryType
            } else {
                null
            }
        }
        if (validSamples.isEmpty()) return null

        val sortedDurations = validSamples.map { it.first }.sorted()
        val representativeDays = when {
            sortedDurations.size >= 3 -> {
                val mid = sortedDurations.size / 2
                if (sortedDurations.size % 2 == 1) {
                    sortedDurations[mid]
                } else {
                    ((sortedDurations[mid - 1] + sortedDurations[mid]) / 2.0).roundToInt()
                }
            }
            else -> sortedDurations.average().roundToInt()
        }.coerceAtLeast(1)

        val typeCounts = validSamples.groupingBy { it.second }.eachCount()
        val dominantType = validSamples
            .maxByOrNull { (_, type) -> typeCounts[type] ?: 0 }
            ?.second
            ?: ExpiryType.USE_BY

        return ShelfLifeEstimate(
            averageDays = representativeDays,
            sampleCount = validSamples.size,
            estimatedExpiryMillis = PantryDateUtils.addDays(todayMillis, representativeDays),
            dominantExpiryType = dominantType
        )
    }
}
