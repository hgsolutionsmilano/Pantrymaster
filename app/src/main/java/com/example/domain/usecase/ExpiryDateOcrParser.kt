package com.example.domain.usecase

import com.example.domain.model.ExpiryType
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class OcrLineCandidate(
    val text: String,
    val inCoreRoi: Boolean = true,
    val contextText: String = ""
)

data class OcrExpiryMatch(
    val expiryDateMillis: Long,
    val expiryType: ExpiryType,
    val confidence: Int,
    val matchedText: String,
    val hasKeywordCue: Boolean = false,
    val isFourDigitFullDate: Boolean = false
)

object ExpiryDateOcrParser {

    private val utcTz = TimeZone.getTimeZone("UTC")

    private val monthMap: Map<String, Int> = mapOf(
        "GEN" to 0, "GENNAIO" to 0, "JAN" to 0, "JANUARY" to 0,
        "FEB" to 1, "FEBBRAIO" to 1, "FEBRUARY" to 1,
        "MAR" to 2, "MARZO" to 2, "MARCH" to 2,
        "APR" to 3, "APRILE" to 3, "APRIL" to 3,
        "MAG" to 4, "MA6" to 4, "MAGGIO" to 4, "MAY" to 4,
        "GIU" to 5, "G1U" to 5, "GIUGNO" to 5, "JUN" to 5, "JUNE" to 5,
        "LUG" to 6, "LUGLIO" to 6, "JUL" to 6, "JULY" to 6,
        "AGO" to 7, "AG0" to 7, "AGOSTO" to 7, "AUG" to 7, "AUGUST" to 7,
        "SET" to 8, "SETT" to 8, "SETTEMBRE" to 8, "SEP" to 8, "SEPT" to 8, "SEPTEMBER" to 8,
        "OTT" to 9, "0TT" to 9, "OTTOBRE" to 9, "OCT" to 9, "0CT" to 9, "OCTOBER" to 9,
        "NOV" to 10, "N0V" to 10, "NOVEMBRE" to 10, "NOVEMBER" to 10,
        "DIC" to 11, "D1C" to 11, "DICEMBRE" to 11, "DEC" to 11, "DECEMBER" to 11
    )

    // Digit-lookalike character class used inside separated date tokens
    private const val OCR_DIGIT = "0-9OoIl|iSsBZDQ"

    // Explicit separators (/ . -), allowing glued cue prefix (SCAD18/11/26) and glued lot suffix (18/11/26L402)
    private val sepDmyRegex = Regex(
        """(?<![0-9])([$OCR_DIGIT]{1,2})\s*[/.\-]\s*([$OCR_DIGIT]{1,2})\s*[/.\-]\s*([$OCR_DIGIT]{2,4})(?![$OCR_DIGIT])"""
    )
    // Mixed separators or spaces ("18 11 26", "18.11 2027", "18/11 26", "18 11.26")
    private val mixedSpaceDmyRegex = Regex(
        """(?<![0-9A-Za-z])([$OCR_DIGIT]{1,2})\s*[/.\-\s]\s*([$OCR_DIGIT]{1,2})\s*[/.\-\s]\s*([$OCR_DIGIT]{2,4})(?![$OCR_DIGIT])"""
    )
    private val isoYmdRegex = Regex(
        """(?<![0-9])(20[$OCR_DIGIT]{2})\s*[/.\-\s]\s*([$OCR_DIGIT]{1,2})\s*[/.\-\s]\s*([$OCR_DIGIT]{1,2})(?![$OCR_DIGIT])"""
    )
    private const val MONTH_PATTERN =
        "GENNAIO|GEN|JANUARY|JAN|FEBBRAIO|FEB|FEBRUARY|MARZO|MAR|MARCH|APRILE|APR|APRIL|MAGGIO|MAG|MA6|MAY|GIUGNO|GIU|G1U|JUNE|JUN|LUGLIO|LUG|JULY|JUL|AGOSTO|AGO|AG0|AUGUST|AUG|SETTEMBRE|SETT|SET|SEPTEMBER|SEPT|SEP|OTTOBRE|OTT|0TT|OCTOBER|OCT|0CT|NOVEMBRE|NOV|N0V|NOVEMBER|DICEMBRE|DIC|D1C|DECEMBER|DEC"

    private val textualDmyRegex = Regex(
        """(?i)(?<![0-9])([0-9]{1,2}|[$OCR_DIGIT]{1,2}(?=[/.\-\s]))\s*[/.\-\s]?\s*($MONTH_PATTERN)\.?\s*[/.\-\s]?\s*([0-9]{2,4}|(?<=[/.\-\s])[$OCR_DIGIT]{2,4})(?![$OCR_DIGIT])"""
    )
    private val textualMyRegex = Regex(
        """(?i)(?<![0-9A-Za-z])($MONTH_PATTERN)\.?\s*[/.\-\s]\s*([$OCR_DIGIT]{2,4})(?![$OCR_DIGIT])"""
    )
    private val sepMyRegex = Regex(
        """(?<![0-9])([$OCR_DIGIT]{1,2})\s*[/.\-]\s*([$OCR_DIGIT]{2,4})(?![$OCR_DIGIT])"""
    )
    private val spaceMyRegex = Regex(
        """(?<![0-9A-Za-z])([$OCR_DIGIT]{1,2})\s+(20[$OCR_DIGIT]{2}|[$OCR_DIGIT]{2})(?![$OCR_DIGIT])"""
    )
    private val compactCueRegex = Regex(
        """(?i)\b(?:SCAD(?:ENZA)?|EXP(?:IRY)?|TMC|BBE|USE\s*BY)\b[:.\s\-]*([$OCR_DIGIT]{2})([$OCR_DIGIT]{2})([$OCR_DIGIT]{2,4})(?![$OCR_DIGIT])"""
    )
    private val bareCompactDmyRegex = Regex(
        """(?<![0-9])(\d{2})(\d{2})(20\d{2}|\d{2})(?![0-9])"""
    )

    private val bestBeforeCues = listOf(
        "PREFERIBILMENTE", "PREF.", "PREF ", "TMC", "BEST BEFORE", "BBE", "CONS. PREF", "ENTRO FINE"
    )
    private val useByCues = listOf(
        "SCADENZA", "SCAD", "DA CONSUMARE ENTRO", "ENTRO IL", "USE BY", "EXPIRY", "EXP"
    )
    private val productionNegativeCues = listOf(
        "PROD", "CONFEZ", "FABBRIC", "FAB.", "MFG", "PKD", "PACKED", "NATO"
    )
    private val lotPrefixRegex = Regex("""(?i)(?:\bL(?:OTTO|OT)?\.?\s*[:\-]?\s*)$""")

    fun parseBestCandidate(
        rawText: String,
        todayMillis: Long = PantryDateUtils.todayStartMillis(),
        ignoredBarcode: String? = null
    ): OcrExpiryMatch? {
        val rawLines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val candidates = rawLines.mapIndexed { idx, line ->
            val prev = rawLines.getOrNull(idx - 1).orEmpty()
            OcrLineCandidate(text = line, inCoreRoi = true, contextText = prev)
        }
        return parseBestCandidate(candidates, todayMillis, ignoredBarcode)
    }

    fun parseBestCandidate(
        lines: List<OcrLineCandidate>,
        todayMillis: Long = PantryDateUtils.todayStartMillis(),
        ignoredBarcode: String? = null
    ): OcrExpiryMatch? {
        if (lines.isEmpty()) return null

        val minAllowedMillis = PantryDateUtils.addDays(todayMillis, -180)
        val maxAllowedMillis = PantryDateUtils.addDays(todayMillis, 365 * 15)
        val cleanedBarcode = ignoredBarcode?.trim()?.takeIf { it.length >= 6 }

        val allMatches = mutableListOf<OcrExpiryMatch>()

        for (line in lines) {
            val text = line.text.trim()
            if (text.length < 4) continue
            if (cleanedBarcode != null && text.replace(" ", "").contains(cleanedBarcode)) continue

            val upperCombined = "${line.contextText} ${line.text}".uppercase(Locale.ITALY)
            val upperLine = text.uppercase(Locale.ITALY)

            val hasBestBefore = bestBeforeCues.any { upperCombined.contains(it) }
            val hasUseBy = !hasBestBefore && useByCues.any { upperCombined.contains(it) }
            val hasGenericEntro = !hasBestBefore && !hasUseBy && upperCombined.contains("ENTRO")
            val hasKeywordCue = hasBestBefore || hasUseBy || hasGenericEntro

            val detectedType = when {
                hasBestBefore -> ExpiryType.BEST_BEFORE
                else -> ExpiryType.USE_BY
            }

            val hasProdNegative = productionNegativeCues.any { upperLine.contains(it) }
            val coveredRanges = mutableListOf<IntRange>()

            fun overlapsCovered(range: IntRange): Boolean =
                coveredRanges.any { existing ->
                    range.first <= existing.last && range.last >= existing.first
                }

            fun evaluateAndAdd(
                range: IntRange,
                year: Int?,
                monthZeroBased: Int?,
                day: Int?,
                baseScore: Int,
                isFourDigitFullDate: Boolean,
                useEndOfMonth: Boolean,
                matchedSubstring: String,
                forceKeywordCue: Boolean = false
            ) {
                if (year == null || monthZeroBased == null) return
                val resolvedDay = if (useEndOfMonth) {
                    lastDayOfMonth(year, monthZeroBased) ?: return
                } else {
                    day ?: return
                }
                val millis = toValidatedUtcMidnight(year, monthZeroBased, resolvedDay) ?: return
                if (millis < minAllowedMillis || millis > maxAllowedMillis) return

                val effectiveCue = hasKeywordCue || forceKeywordCue
                val prefixBefore = text.substring(0, range.first)
                val looksLikeLot = !effectiveCue && lotPrefixRegex.containsMatchIn(prefixBefore)

                var score = baseScore
                if (hasBestBefore || hasUseBy || forceKeywordCue) score += 25 else if (hasGenericEntro) score += 15
                if (line.inCoreRoi) score += 15
                if (millis >= todayMillis) score += 12 else score -= 8
                if (hasProdNegative) score -= 60
                if (looksLikeLot) score -= 50

                if (score >= 30) {
                    coveredRanges.add(range)
                    allMatches.add(
                        OcrExpiryMatch(
                            expiryDateMillis = millis,
                            expiryType = detectedType,
                            confidence = score,
                            matchedText = matchedSubstring.trim(),
                            hasKeywordCue = effectiveCue,
                            isFourDigitFullDate = isFourDigitFullDate
                        )
                    )
                }
            }

            // 1. ISO YYYY-MM-DD / YYYY MM DD
            for (m in isoYmdRegex.findAll(text)) {
                val y = parseYear(m.groupValues[1], allowTwoDigit = false)
                val mo = parseIntOcr(m.groupValues[2])?.minus(1)
                val d = parseIntOcr(m.groupValues[3])
                evaluateAndAdd(m.range, y, mo, d, baseScore = 50, isFourDigitFullDate = true, useEndOfMonth = false, m.value)
            }

            // 2. DD/MM/YYYY or DD.MM.YY with explicit separators (supports SCAD18/11/26 and 18/11/26L402)
            for (m in sepDmyRegex.findAll(text)) {
                if (overlapsCovered(m.range)) continue
                val d = parseIntOcr(m.groupValues[1])
                val mo = parseIntOcr(m.groupValues[2])?.minus(1)
                val yearRaw = m.groupValues[3]
                val y = parseYear(yearRaw, allowTwoDigit = true)
                val is4Digit = normalizeDigits(yearRaw)?.length == 4
                evaluateAndAdd(m.range, y, mo, d, baseScore = if (is4Digit) 54 else 48, isFourDigitFullDate = is4Digit, useEndOfMonth = false, m.value)
            }

            // 3. Textual day-month-year: "15 OTT 2026", "5 MAG 27", "15OTT2027"
            for (m in textualDmyRegex.findAll(text)) {
                if (overlapsCovered(m.range)) continue
                val d = parseIntOcr(m.groupValues[1])
                val mo = monthMap[m.groupValues[2].uppercase(Locale.ITALY)]
                val yearRaw = m.groupValues[3]
                val y = parseYear(yearRaw, allowTwoDigit = true)
                val is4Digit = normalizeDigits(yearRaw)?.length == 4
                evaluateAndAdd(m.range, y, mo, d, baseScore = if (is4Digit) 55 else 48, isFourDigitFullDate = is4Digit, useEndOfMonth = false, m.value)
            }

            // 4. Mixed separators or spaces ("18 11 2026", "18.11 26", "18 11.2027")
            for (m in mixedSpaceDmyRegex.findAll(text)) {
                if (overlapsCovered(m.range)) continue
                val d = parseIntOcr(m.groupValues[1])
                val mo = parseIntOcr(m.groupValues[2])?.minus(1)
                val yearRaw = m.groupValues[3]
                val y = parseYear(yearRaw, allowTwoDigit = true)
                val is4Digit = normalizeDigits(yearRaw)?.length == 4
                evaluateAndAdd(m.range, y, mo, d, baseScore = if (is4Digit) 50 else 44, isFourDigitFullDate = is4Digit, useEndOfMonth = false, m.value)
            }

            // 5. Compact with same-line cue: "SCAD 150826" or "EXP 15082026"
            for (m in compactCueRegex.findAll(text)) {
                if (overlapsCovered(m.range)) continue
                val d = parseIntOcr(m.groupValues[1])
                val mo = parseIntOcr(m.groupValues[2])?.minus(1)
                val yearRaw = m.groupValues[3]
                val y = parseYear(yearRaw, allowTwoDigit = true)
                val is4Digit = normalizeDigits(yearRaw)?.length == 4
                evaluateAndAdd(m.range, y, mo, d, baseScore = 48, isFourDigitFullDate = is4Digit, useEndOfMonth = false, m.value, forceKeywordCue = true)
            }

            // 6. Compact 6/8 digits on its own line when context cue is present (e.g. "Da consumarsi entro:\n18112027")
            if (hasKeywordCue) {
                for (m in bareCompactDmyRegex.findAll(text)) {
                    if (overlapsCovered(m.range)) continue
                    val d = m.groupValues[1].toIntOrNull()
                    val mo = m.groupValues[2].toIntOrNull()?.minus(1)
                    val yearRaw = m.groupValues[3]
                    val y = parseYearStrict(yearRaw)
                    val is4Digit = yearRaw.length == 4
                    evaluateAndAdd(m.range, y, mo, d, baseScore = 44, isFourDigitFullDate = is4Digit, useEndOfMonth = false, m.value)
                }
            }

            // 7. Textual month-year: "OTT 2026", "NOV 26" -> end of month
            for (m in textualMyRegex.findAll(text)) {
                if (overlapsCovered(m.range)) continue
                val mo = monthMap[m.groupValues[1].uppercase(Locale.ITALY)]
                val yearRaw = m.groupValues[2]
                val y = parseYear(yearRaw, allowTwoDigit = true)
                val is4Digit = normalizeDigits(yearRaw)?.length == 4
                evaluateAndAdd(m.range, y, mo, day = null, baseScore = if (is4Digit) 44 else 38, isFourDigitFullDate = false, useEndOfMonth = true, m.value)
            }

            // 8. Numeric MM/YYYY or MM/YY -> end of month
            for (m in sepMyRegex.findAll(text)) {
                if (overlapsCovered(m.range)) continue
                val mo = parseIntOcr(m.groupValues[1])?.minus(1)
                val yearRaw = m.groupValues[2]
                val y = parseYear(yearRaw, allowTwoDigit = true)
                val is4Digit = normalizeDigits(yearRaw)?.length == 4
                evaluateAndAdd(m.range, y, mo, day = null, baseScore = if (is4Digit) 40 else 34, isFourDigitFullDate = false, useEndOfMonth = true, m.value)
            }

            // 9. Space-separated MM YYYY ("TMC 08 2027") when explicit expiry cue is present
            if (hasKeywordCue) {
                for (m in spaceMyRegex.findAll(text)) {
                    if (overlapsCovered(m.range)) continue
                    val mo = parseIntOcr(m.groupValues[1])?.minus(1)
                    val yearRaw = m.groupValues[2]
                    val y = parseYear(yearRaw, allowTwoDigit = true)
                    val is4Digit = normalizeDigits(yearRaw)?.length == 4
                    evaluateAndAdd(m.range, y, mo, day = null, baseScore = if (is4Digit) 38 else 32, isFourDigitFullDate = false, useEndOfMonth = true, m.value)
                }
            }
        }

        return allMatches.maxWithOrNull(
            compareBy<OcrExpiryMatch>(
                { it.confidence },
                { if (it.expiryDateMillis >= todayMillis) 1 else 0 },
                { if (it.isFourDigitFullDate) 1 else 0 }
            )
        )
    }

    private fun normalizeDigits(raw: String): String? {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val mapped = when (ch) {
                in '0'..'9' -> ch
                'O', 'o', 'D', 'Q' -> '0'
                'I', 'l', '|', 'i' -> '1'
                'Z', 'z' -> '2'
                'S', 's' -> '5'
                'B' -> '8'
                else -> return null
            }
            sb.append(mapped)
        }
        return sb.toString()
    }

    private fun parseIntOcr(raw: String): Int? = normalizeDigits(raw)?.toIntOrNull()

    private fun parseYear(raw: String, allowTwoDigit: Boolean): Int? {
        val normalized = normalizeDigits(raw) ?: return null
        return expandYear(normalized, allowTwoDigit)
    }

    private fun parseYearStrict(raw: String): Int? {
        if (!raw.all { it.isDigit() }) return null
        return expandYear(raw, allowTwoDigit = true)
    }

    private fun expandYear(digits: String, allowTwoDigit: Boolean): Int? {
        val num = digits.toIntOrNull() ?: return null
        return when (digits.length) {
            4 -> if (num in 2000..2099) num else null
            2 -> if (allowTwoDigit) {
                if (num <= 59) 2000 + num else 1900 + num
            } else null
            else -> null
        }
    }

    private fun lastDayOfMonth(year: Int, monthZeroBased: Int): Int? {
        if (monthZeroBased !in 0..11) return null
        val cal = Calendar.getInstance(utcTz).apply {
            isLenient = false
            clear()
            set(year, monthZeroBased, 1, 0, 0, 0)
        }
        return runCatching { cal.getActualMaximum(Calendar.DAY_OF_MONTH) }.getOrNull()
    }

    private fun toValidatedUtcMidnight(year: Int, monthZeroBased: Int, day: Int): Long? {
        if (monthZeroBased !in 0..11 || day !in 1..31) return null
        val cal = Calendar.getInstance(utcTz).apply {
            isLenient = false
            clear()
            set(year, monthZeroBased, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return runCatching { cal.timeInMillis }.getOrNull()
    }
}

class OcrDateConsensusTracker(
    private val maxWindowSize: Int = 8,
    private val windowMillis: Long = 5000L
) {
    private data class Observation(
        val match: OcrExpiryMatch,
        val timestampMillis: Long
    )

    private val observations = mutableListOf<Observation>()

    fun reset() {
        observations.clear()
    }

    fun submit(match: OcrExpiryMatch?, nowMillis: Long = System.currentTimeMillis()): OcrExpiryMatch? {
        observations.removeAll { nowMillis - it.timestampMillis > windowMillis }
        if (match == null) return null

        observations.add(Observation(match, nowMillis))
        if (observations.size > maxWindowSize) {
            observations.removeAt(0)
        }

        val matchingDate = observations.filter { it.match.expiryDateMillis == match.expiryDateMillis }
        val requiredHits = if (matchingDate.any { it.match.hasKeywordCue || it.match.isFourDigitFullDate || it.match.confidence >= 55 }) {
            2
        } else {
            3
        }

        if (matchingDate.size < requiredHits) return null

        val typeScores = matchingDate.groupBy { it.match.expiryType }
            .mapValues { (_, list) -> list.sumOf { if (it.match.hasKeywordCue) 2 else 1 } }
        val bestType = typeScores.maxByOrNull { it.value }?.key ?: match.expiryType
        val bestMatch = matchingDate.maxByOrNull { it.match.confidence }?.match ?: match

        return bestMatch.copy(expiryType = bestType)
    }
}
