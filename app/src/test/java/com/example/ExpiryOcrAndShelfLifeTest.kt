package com.example

import com.example.data.local.entity.ProductBatchEntity
import com.example.domain.model.ExpiryType
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.ExpiryDateOcrParser
import com.example.domain.usecase.OcrDateConsensusTracker
import com.example.domain.usecase.OcrLineCandidate
import com.example.domain.usecase.PantryDateUtils
import com.example.domain.usecase.ShelfLifeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiryOcrAndShelfLifeTest {

    private val fixedToday = PantryDateUtils.normalizeToUtcMidnight(2026, 10, 25) // 25 Nov 2026

    @Test
    fun parseFullNumericAndSlashDotDashDates() {
        val match1 = ExpiryDateOcrParser.parseBestCandidate("SCAD. 18/12/2026", todayMillis = fixedToday)
        assertNotNull(match1)
        assertEquals(PantryDateUtils.normalizeToUtcMidnight(2026, 11, 18), match1!!.expiryDateMillis)
        assertEquals(ExpiryType.USE_BY, match1.expiryType)

        val match2 = ExpiryDateOcrParser.parseBestCandidate("TMC 05.03.27", todayMillis = fixedToday)
        assertNotNull(match2)
        assertEquals(PantryDateUtils.normalizeToUtcMidnight(2027, 2, 5), match2!!.expiryDateMillis)
        assertEquals(ExpiryType.BEST_BEFORE, match2.expiryType)

        val match3 = ExpiryDateOcrParser.parseBestCandidate("2027-07-14", todayMillis = fixedToday)
        assertNotNull(match3)
        assertEquals(PantryDateUtils.normalizeToUtcMidnight(2027, 6, 14), match3!!.expiryDateMillis)
    }

    @Test
    fun parseMonthYearNormalizesToEndOfMonthIncludingLeapYear() {
        val augMatch = ExpiryDateOcrParser.parseBestCandidate(
            "Da consumarsi preferibilmente entro:\n08/2027",
            todayMillis = fixedToday
        )
        assertNotNull(augMatch)
        assertEquals(PantryDateUtils.normalizeToUtcMidnight(2027, 7, 31), augMatch!!.expiryDateMillis)
        assertEquals(ExpiryType.BEST_BEFORE, augMatch.expiryType)

        val leapFeb = ExpiryDateOcrParser.parseBestCandidate("BEST BEFORE 02/2028", todayMillis = fixedToday)
        assertNotNull(leapFeb)
        assertEquals(PantryDateUtils.normalizeToUtcMidnight(2028, 1, 29), leapFeb!!.expiryDateMillis)
    }

    @Test
    fun parseTextualItalianAndEnglishMonthsAndOcrLookalikes() {
        val ita = ExpiryDateOcrParser.parseBestCandidate("SCADENZA 15 OTT 2027", todayMillis = fixedToday)
        assertNotNull(ita)
        assertEquals(PantryDateUtils.normalizeToUtcMidnight(2027, 9, 15), ita!!.expiryDateMillis)
        assertEquals(ExpiryType.USE_BY, ita.expiryType)

        val lookalike = ExpiryDateOcrParser.parseBestCandidate("EXP 15/O8/2O27", todayMillis = fixedToday)
        assertNotNull(lookalike)
        assertEquals(PantryDateUtils.normalizeToUtcMidnight(2027, 7, 15), lookalike!!.expiryDateMillis)

        val monthOnlyText = ExpiryDateOcrParser.parseBestCandidate("TMC DIC 2027", todayMillis = fixedToday)
        assertNotNull(monthOnlyText)
        assertEquals(PantryDateUtils.normalizeToUtcMidnight(2027, 11, 31), monthOnlyText!!.expiryDateMillis)
        assertEquals(ExpiryType.BEST_BEFORE, monthOnlyText.expiryType)

        val compactText = ExpiryDateOcrParser.parseBestCandidate("SCAD 15OTT2027", todayMillis = fixedToday)
        assertNotNull(compactText)
        assertEquals(PantryDateUtils.normalizeToUtcMidnight(2027, 9, 15), compactText!!.expiryDateMillis)

        val ocrTextMonth = ExpiryDateOcrParser.parseBestCandidate("TMC 1O/OTT/2O27", todayMillis = fixedToday)
        assertNotNull(ocrTextMonth)
        assertEquals(PantryDateUtils.normalizeToUtcMidnight(2027, 9, 10), ocrTextMonth!!.expiryDateMillis)
        assertEquals(ExpiryType.BEST_BEFORE, ocrTextMonth.expiryType)
    }

    @Test
    fun ignoresInvalidCalendarLotNumbersProductionDatesAndBarcode() {
        assertNull(ExpiryDateOcrParser.parseBestCandidate("SCAD 31/02/2027", todayMillis = fixedToday))
        assertNull(ExpiryDateOcrParser.parseBestCandidate("LOTTO L.241226", todayMillis = fixedToday))
        assertNull(ExpiryDateOcrParser.parseBestCandidate("PROD 10/11/2026", todayMillis = fixedToday))

        val mixed = ExpiryDateOcrParser.parseBestCandidate(
            lines = listOf(
                OcrLineCandidate("PROD 10/11/2026", inCoreRoi = false),
                OcrLineCandidate("8076800195057", inCoreRoi = true),
                OcrLineCandidate("SCAD 22/04/2027", inCoreRoi = true)
            ),
            todayMillis = fixedToday,
            ignoredBarcode = "8076800195057"
        )
        assertNotNull(mixed)
        assertEquals(PantryDateUtils.normalizeToUtcMidnight(2027, 3, 22), mixed!!.expiryDateMillis)
    }

    @Test
    fun consensusTrackerRequiresMultipleConsistentFramesBeforeLocking() {
        val tracker = OcrDateConsensusTracker()
        val match = ExpiryDateOcrParser.parseBestCandidate("SCAD 12/06/2027", todayMillis = fixedToday)
        assertNotNull(match)

        // Single frame must not lock immediately (prevents dot-matrix OCR flicker)
        assertNull(tracker.submit(match, nowMillis = 1000L))

        // Second matching frame with keyword cue locks on
        val locked = tracker.submit(match, nowMillis = 1200L)
        assertNotNull(locked)
        assertEquals(match!!.expiryDateMillis, locked!!.expiryDateMillis)
    }

    @Test
    fun shelfLifeEstimatorUsesMedianToResistOutliers() {
        val purchase = PantryDateUtils.normalizeToUtcMidnight(2026, 9, 1)
        val batches = listOf(
            ProductBatchEntity("b1", "p1", 1.0, 0.0, UnitOfMeasure.PIECES, purchase, PantryDateUtils.addDays(purchase, 14), ExpiryType.USE_BY, "loc1"),
            ProductBatchEntity("b2", "p1", 1.0, 0.0, UnitOfMeasure.PIECES, purchase, PantryDateUtils.addDays(purchase, 15), ExpiryType.USE_BY, "loc1"),
            ProductBatchEntity("b3", "p1", 1.0, 0.0, UnitOfMeasure.PIECES, purchase, PantryDateUtils.addDays(purchase, 16), ExpiryType.USE_BY, "loc1"),
            ProductBatchEntity("b4", "p1", 1.0, 0.0, UnitOfMeasure.PIECES, purchase, PantryDateUtils.addDays(purchase, 900), ExpiryType.BEST_BEFORE, "loc1"),
            ProductBatchEntity("b5", "p1", 1.0, 0.0, UnitOfMeasure.PIECES, purchase, null, ExpiryType.USE_BY, "loc1")
        )

        val estimate = ShelfLifeEstimator.estimateFromBatches(batches, todayMillis = fixedToday)
        assertNotNull(estimate)
        // Sorted durations: [14, 15, 16, 900] -> median avg of 15 and 16 = 16
        assertTrue(estimate!!.averageDays in 15..16)
        assertEquals(4, estimate.sampleCount)
        assertEquals(ExpiryType.USE_BY, estimate.dominantExpiryType)
        assertEquals(PantryDateUtils.addDays(fixedToday, estimate.averageDays), estimate.estimatedExpiryMillis)
    }
}
