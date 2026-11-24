package com.example.domain.usecase

import com.example.domain.model.UnitOfMeasure

object UnitConverter {
    fun toBaseAmount(quantity: Double, unit: UnitOfMeasure): Pair<Double, String> {
        return when (unit) {
            UnitOfMeasure.KILOGRAMS -> quantity * 1000.0 to "mass"
            UnitOfMeasure.GRAMS -> quantity to "mass"
            UnitOfMeasure.LITERS -> quantity * 1000.0 to "volume"
            UnitOfMeasure.MILLILITERS -> quantity to "volume"
            else -> quantity to unit.name
        }
    }

    fun isCompatible(u1: UnitOfMeasure, u2: UnitOfMeasure): Boolean {
        val (_, d1) = toBaseAmount(1.0, u1)
        val (_, d2) = toBaseAmount(1.0, u2)
        return d1 == d2
    }

    fun convert(quantity: Double, from: UnitOfMeasure, to: UnitOfMeasure): Double? {
        if (from == to) return quantity
        val (base, d1) = toBaseAmount(quantity, from)
        val (unitToBase, d2) = toBaseAmount(1.0, to)
        if (d1 != d2 || unitToBase == 0.0) return null
        return base / unitToBase
    }

    fun formatQuantity(quantity: Double, unit: UnitOfMeasure): String {
        val formatted = if (kotlin.math.abs(quantity - quantity.toLong()) < 1e-4) {
            quantity.toLong().toString()
        } else {
            String.format(java.util.Locale.ITALY, "%.2f", quantity).trimEnd('0').trimEnd(',')
        }
        return "$formatted ${unit.symbol}"
    }
}

data class AvailableIngredientStock(
    val productId: String?,
    val productName: String,
    val availableQuantity: Double,
    val unit: UnitOfMeasure,
    val earliestExpiryMillis: Long?
)

data class RecipeIngredientSpec(
    val ingredientId: String,
    val productId: String?,
    val ingredientName: String,
    val requiredQuantity: Double,
    val unit: UnitOfMeasure
)

data class IngredientMatchDetail(
    val ingredientId: String,
    val productId: String?,
    val ingredientName: String,
    val requiredQuantity: Double,
    val availableQuantity: Double,
    val missingQuantity: Double,
    val unit: UnitOfMeasure,
    val isSatisfied: Boolean,
    val usesExpiringSoon: Boolean
)

enum class RecipeFeasibility {
    MAKEABLE,
    PARTIAL,
    UNAVAILABLE
}

data class RecipeMatchEvaluation(
    val availabilityPercent: Int,
    val feasibility: RecipeFeasibility,
    val usesExpiringProductsCount: Int,
    val earliestExpiringDays: Long?,
    val details: List<IngredientMatchDetail>,
    val missingIngredients: List<IngredientMatchDetail>,
    val priorityScore: Double
)

object RecipeMatcher {
    fun evaluate(
        ingredients: List<RecipeIngredientSpec>,
        stock: List<AvailableIngredientStock>,
        todayMillis: Long = PantryDateUtils.todayStartMillis(),
        expiringSoonDays: Int = 5
    ): RecipeMatchEvaluation {
        if (ingredients.isEmpty()) {
            return RecipeMatchEvaluation(
                availabilityPercent = 0,
                feasibility = RecipeFeasibility.UNAVAILABLE,
                usesExpiringProductsCount = 0,
                earliestExpiringDays = null,
                details = emptyList(),
                missingIngredients = emptyList(),
                priorityScore = 0.0
            )
        }

        val details = ingredients.map { req ->
            val matchedStock = stock.filter { s ->
                (req.productId != null && s.productId == req.productId) ||
                    s.productName.trim().equals(req.ingredientName.trim(), ignoreCase = true)
            }
            var totalAvailableInReqUnit = 0.0
            var earliestExpiry: Long? = null
            for (item in matchedStock) {
                if (item.availableQuantity <= 0.0) continue
                val converted = UnitConverter.convert(item.availableQuantity, item.unit, req.unit)
                if (converted != null) {
                    totalAvailableInReqUnit += converted
                    val exp = item.earliestExpiryMillis
                    if (exp != null && (earliestExpiry == null || exp < earliestExpiry)) {
                        earliestExpiry = exp
                    }
                }
            }

            val missing = (req.requiredQuantity - totalAvailableInReqUnit).coerceAtLeast(0.0)
            val satisfied = missing <= 1e-4
            val daysToExpiry = earliestExpiry?.let { PantryDateUtils.daysBetween(todayMillis, it) }
            val usesExpiringSoon = totalAvailableInReqUnit > 0.0 && daysToExpiry != null && daysToExpiry in 0..expiringSoonDays

            IngredientMatchDetail(
                ingredientId = req.ingredientId,
                productId = req.productId ?: matchedStock.firstOrNull()?.productId,
                ingredientName = req.ingredientName,
                requiredQuantity = req.requiredQuantity,
                availableQuantity = totalAvailableInReqUnit,
                missingQuantity = if (satisfied) 0.0 else missing,
                unit = req.unit,
                isSatisfied = satisfied,
                usesExpiringSoon = usesExpiringSoon
            )
        }

        val satisfiedCount = details.count { it.isSatisfied }
        val partialCoverageSum = details.sumOf { d ->
            if (d.requiredQuantity <= 0.0) 1.0
            else (d.availableQuantity / d.requiredQuantity).coerceIn(0.0, 1.0)
        }
        val percent = ((partialCoverageSum / details.size.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        val feasibility = when {
            satisfiedCount == details.size -> RecipeFeasibility.MAKEABLE
            partialCoverageSum > 0.0 -> RecipeFeasibility.PARTIAL
            else -> RecipeFeasibility.UNAVAILABLE
        }

        val expiringCount = details.count { it.usesExpiringSoon }
        val earliestDays = stock.mapNotNull { s ->
            val used = details.any { d ->
                d.availableQuantity > 0.0 && (
                    (d.productId != null && d.productId == s.productId) ||
                        d.ingredientName.equals(s.productName, ignoreCase = true)
                    )
            }
            if (used) s.earliestExpiryMillis?.let { PantryDateUtils.daysBetween(todayMillis, it) } else null
        }.filter { it >= 0 }.minOrNull()

        // Priority score: favors full/high availability AND strongly boosts recipes that rescue expiring items
        val expiryBonus = expiringCount * 35.0 + (earliestDays?.let { (10 - it.coerceAtMost(10)) * 3.0 } ?: 0.0)
        val priorityScore = percent.toDouble() + expiryBonus + if (feasibility == RecipeFeasibility.MAKEABLE) 50.0 else 0.0

        return RecipeMatchEvaluation(
            availabilityPercent = percent,
            feasibility = feasibility,
            usesExpiringProductsCount = expiringCount,
            earliestExpiringDays = earliestDays,
            details = details,
            missingIngredients = details.filter { !it.isSatisfied },
            priorityScore = priorityScore
        )
    }
}
