package com.example.data.repository

import com.example.data.local.dao.StockMovementDao
import com.example.data.local.relation.CategorySpendAggregate
import com.example.data.local.relation.TopUsedProductAggregate
import com.example.domain.model.MovementType
import com.example.domain.usecase.PantryDateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class MonthlyMovementPoint(
    val monthKey: String,
    val label: String,
    val purchased: Double,
    val consumed: Double,
    val wasted: Double,
    val spend: Double
)

data class PantryStatisticsSnapshot(
    val purchasedQuantity: Double = 0.0,
    val consumedQuantity: Double = 0.0,
    val wastedQuantity: Double = 0.0,
    val totalSpend: Double = 0.0,
    val purchasedCount: Int = 0,
    val consumedCount: Int = 0,
    val wastedCount: Int = 0,
    val categoryAggregates: List<CategorySpendAggregate> = emptyList(),
    val monthlyTrend: List<MonthlyMovementPoint> = emptyList(),
    val topUsedProducts: List<TopUsedProductAggregate> = emptyList()
)

@Singleton
class StatisticsRepository @Inject constructor(
    private val movementDao: StockMovementDao
) {
    fun observeStatistics(fromMillis: Long, toMillis: Long): Flow<PantryStatisticsSnapshot> {
        return combine(
            movementDao.observeInRange(fromMillis, toMillis),
            movementDao.observeCategoryAggregates(fromMillis, toMillis),
            movementDao.observeTopUsedProducts(fromMillis, toMillis, limit = 8)
        ) { movements, categoryAggs, topUsed ->
            var purchasedQty = 0.0
            var consumedQty = 0.0
            var wastedQty = 0.0
            var totalSpend = 0.0
            var purchasedCount = 0
            var consumedCount = 0
            var wastedCount = 0

            val monthlyBuckets = linkedMapOf<String, MutableList<Double>>() // [purchased, consumed, wasted, spend]

            for (m in movements) {
                val monthKey = PantryDateUtils.monthYearKey(m.timestamp)
                val bucket = monthlyBuckets.getOrPut(monthKey) { mutableListOf(0.0, 0.0, 0.0, 0.0) }
                when (m.type) {
                    MovementType.PURCHASE, MovementType.MANUAL_ADD -> {
                        purchasedQty += m.quantity
                        purchasedCount++
                        val spend = m.priceAmount ?: 0.0
                        totalSpend += spend
                        bucket[0] += m.quantity
                        bucket[3] += spend
                    }
                    MovementType.CONSUME -> {
                        consumedQty += m.quantity
                        consumedCount++
                        bucket[1] += m.quantity
                    }
                    MovementType.WASTE -> {
                        wastedQty += m.quantity
                        wastedCount++
                        bucket[2] += m.quantity
                    }
                    MovementType.ADJUSTMENT, MovementType.TRANSFER, MovementType.BATCH_CANCEL -> Unit
                }
            }

            val monthlyTrend = monthlyBuckets.entries
                .sortedBy { it.key }
                .map { (key, vals) ->
                    MonthlyMovementPoint(
                        monthKey = key,
                        label = PantryDateUtils.formatMonthLabel(key),
                        purchased = vals[0],
                        consumed = vals[1],
                        wasted = vals[2],
                        spend = vals[3]
                    )
                }

            PantryStatisticsSnapshot(
                purchasedQuantity = purchasedQty,
                consumedQuantity = consumedQty,
                wastedQuantity = wastedQty,
                totalSpend = totalSpend,
                purchasedCount = purchasedCount,
                consumedCount = consumedCount,
                wastedCount = wastedCount,
                categoryAggregates = categoryAggs,
                monthlyTrend = monthlyTrend,
                topUsedProducts = topUsed
            )
        }
    }
}
