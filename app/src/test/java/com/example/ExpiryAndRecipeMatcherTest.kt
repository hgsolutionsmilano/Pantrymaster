package com.example

import com.example.domain.model.ExpiryStatus
import com.example.domain.model.ExpiryType
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.AvailableIngredientStock
import com.example.domain.usecase.BatchAllocationInput
import com.example.domain.usecase.ExpiryCalculator
import com.example.domain.usecase.FefoConsumptionCalculator
import com.example.domain.usecase.PantryDateUtils
import com.example.domain.usecase.RecipeFeasibility
import com.example.domain.usecase.RecipeIngredientSpec
import com.example.domain.usecase.RecipeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiryAndRecipeMatcherTest {

    @Test
    fun `expiry calculator evaluates all thresholds and types accurately`() {
        val today = PantryDateUtils.normalizeToUtcMidnight(2026, 10, 24) // 24 Nov 2026
        val yesterday = PantryDateUtils.addDays(today, -1)
        val in3Days = PantryDateUtils.addDays(today, 3)
        val in12Days = PantryDateUtils.addDays(today, 12)

        assertEquals(ExpiryStatus.EXPIRED, ExpiryCalculator.evaluate(yesterday, ExpiryType.USE_BY, today).status)
        assertEquals(ExpiryStatus.EXPIRING_TODAY, ExpiryCalculator.evaluate(today, ExpiryType.USE_BY, today).status)
        assertEquals(ExpiryStatus.EXPIRING_SOON, ExpiryCalculator.evaluate(in3Days, ExpiryType.BEST_BEFORE, today).status)
        assertEquals(ExpiryStatus.FRESH, ExpiryCalculator.evaluate(in12Days, ExpiryType.USE_BY, today).status)
        assertEquals(ExpiryStatus.NO_EXPIRY, ExpiryCalculator.evaluate(null, ExpiryType.USE_BY, today).status)

        assertEquals(3, ExpiryCalculator.matchingThreshold(in3Days, today, listOf(7, 3, 1, 0)))
        assertEquals(0, ExpiryCalculator.matchingThreshold(today, today, listOf(7, 3, 1, 0)))
        assertNull(ExpiryCalculator.matchingThreshold(in12Days, today, listOf(7, 3, 1, 0)))
    }

    @Test
    fun `fefo allocator consumes earliest expiring batches first and leaves no-expiry last`() {
        val today = PantryDateUtils.normalizeToUtcMidnight(2026, 10, 24)
        val batches = listOf(
            BatchAllocationInput("no_exp", 5.0, null, 100L),
            BatchAllocationInput("exp_late", 4.0, PantryDateUtils.addDays(today, 10), 200L),
            BatchAllocationInput("exp_early", 2.0, PantryDateUtils.addDays(today, 2), 300L)
        )

        val res = FefoConsumptionCalculator.allocate(batches, requestedQuantity = 5.0)
        assertEquals(5.0, res.totalConsumed, 0.001)
        assertEquals(0.0, res.unfulfilledQuantity, 0.001)
        assertEquals(2, res.allocations.size)
        assertEquals("exp_early", res.allocations[0].batchId)
        assertEquals(2.0, res.allocations[0].consumedQuantity, 0.001)
        assertTrue(res.allocations[0].isExhausted)
        assertEquals("exp_late", res.allocations[1].batchId)
        assertEquals(3.0, res.allocations[1].consumedQuantity, 0.001)
        assertEquals(1.0, res.allocations[1].newRemainingQuantity, 0.001)
    }

    @Test
    fun `recipe matcher converts units, detects missing ingredients, and prioritizes expiring stock`() {
        val today = PantryDateUtils.normalizeToUtcMidnight(2026, 10, 24)
        val stock = listOf(
            AvailableIngredientStock(
                productId = "p_pasta",
                productName = "Pasta",
                availableQuantity = 1.0,
                unit = UnitOfMeasure.KILOGRAMS,
                earliestExpiryMillis = PantryDateUtils.addDays(today, 2)
            ),
            AvailableIngredientStock(
                productId = "p_pomodoro",
                productName = "Passata di pomodoro",
                availableQuantity = 200.0,
                unit = UnitOfMeasure.MILLILITERS,
                earliestExpiryMillis = PantryDateUtils.addDays(today, 30)
            )
        )

        val ingredients = listOf(
            RecipeIngredientSpec("i1", "p_pasta", "Pasta", 400.0, UnitOfMeasure.GRAMS),
            RecipeIngredientSpec("i2", "p_pomodoro", "Passata di pomodoro", 500.0, UnitOfMeasure.MILLILITERS)
        )

        val eval = RecipeMatcher.evaluate(ingredients, stock, todayMillis = today)
        assertEquals(RecipeFeasibility.PARTIAL, eval.feasibility)
        assertEquals(70, eval.availabilityPercent) // (1.0 + 0.4) / 2 = 70%
        assertEquals(1, eval.missingIngredients.size)
        assertEquals(300.0, eval.missingIngredients[0].missingQuantity, 0.01)
        assertEquals(1, eval.usesExpiringProductsCount)
    }
}
