package com.example

import com.example.data.local.entity.HouseInfoEntryEntity
import com.example.domain.model.ExpiryStatus
import com.example.domain.model.ExpiryType
import com.example.domain.model.HouseInfoCategory
import com.example.domain.model.MeasurementUnit
import com.example.domain.usecase.BatchAllocationInput
import com.example.domain.usecase.ExpiryCalculator
import com.example.domain.usecase.FefoConsumptionCalculator
import com.example.domain.usecase.HouseInfoFormatter
import com.example.domain.usecase.PantryDateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiryAndRecipeMatcherTest {

    @Test
    fun `expiry calculator evaluates all thresholds and types accurately`() {
        val today = PantryDateUtils.normalizeToUtcMidnight(2026, 10, 24) // 24 Nov 2026
        val yesterday = PantryDateUtils.addDays(today, -1)
        val in2Days = PantryDateUtils.addDays(today, 2)
        val in3Days = PantryDateUtils.addDays(today, 3)
        val in5Days = PantryDateUtils.addDays(today, 5)
        val in12Days = PantryDateUtils.addDays(today, 12)

        assertEquals(ExpiryStatus.EXPIRED, ExpiryCalculator.evaluate(yesterday, ExpiryType.USE_BY, today).status)
        assertEquals(ExpiryStatus.EXPIRING_TODAY, ExpiryCalculator.evaluate(today, ExpiryType.USE_BY, today).status)
        assertEquals(ExpiryStatus.EXPIRING_SOON, ExpiryCalculator.evaluate(in3Days, ExpiryType.BEST_BEFORE, today).status)
        assertEquals(ExpiryStatus.FRESH, ExpiryCalculator.evaluate(in12Days, ExpiryType.USE_BY, today).status)
        assertEquals(ExpiryStatus.NO_EXPIRY, ExpiryCalculator.evaluate(null, ExpiryType.USE_BY, today).status)

        assertEquals(3, ExpiryCalculator.matchingThreshold(in3Days, today, listOf(7, 3, 1, 0)))
        assertEquals(3, ExpiryCalculator.matchingThreshold(in2Days, today, listOf(7, 3, 1, 0)))
        assertEquals(7, ExpiryCalculator.matchingThreshold(in5Days, today, listOf(7, 3, 1, 0)))
        assertEquals(0, ExpiryCalculator.matchingThreshold(today, today, listOf(7, 3, 1, 0)))
        assertEquals(0, ExpiryCalculator.matchingThreshold(yesterday, today, listOf(7, 3, 1, 0)))
        assertEquals(1, ExpiryCalculator.matchingThreshold(yesterday, today, listOf(7, 3, 1)))
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
    fun `house info formatter formats dimensions, masks secrets, and omits secrets from default share text`() {
        val measurement = HouseInfoEntryEntity(
            id = "m1",
            category = HouseInfoCategory.MEASUREMENT,
            title = "Finestra soggiorno",
            widthValue = "120",
            heightValue = "140",
            depthValue = "",
            measurementUnit = MeasurementUnit.CM
        )
        assertEquals("L 120 × H 140 cm", HouseInfoFormatter.formatDimensions(measurement))

        val secretEntry = HouseInfoEntryEntity(
            id = "p1",
            category = HouseInfoCategory.PASSWORD_NOTE,
            title = "Wi-Fi Casa Mare",
            primaryValue = "CasaMare_5G",
            secretValue = "SuperSecret123!",
            notes = "Router in corridoio"
        )
        val masked = HouseInfoFormatter.maskSecret(secretEntry.secretValue)
        assertTrue(masked.all { it == '•' })
        assertTrue(masked.length in 6..12)

        val safeShare = HouseInfoFormatter.buildShareText(secretEntry, includeSecret = false)
        assertTrue(safeShare.contains("Wi-Fi Casa Mare"))
        assertTrue(safeShare.contains("CasaMare_5G"))
        assertFalse(safeShare.contains("SuperSecret123!"))

        val fullShare = HouseInfoFormatter.buildShareText(secretEntry, includeSecret = true)
        assertTrue(fullShare.contains("SuperSecret123!"))
    }
}
