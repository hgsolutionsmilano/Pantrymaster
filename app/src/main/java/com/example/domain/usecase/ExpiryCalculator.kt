package com.example.domain.usecase

import com.example.domain.model.ExpiryStatus
import com.example.domain.model.ExpiryType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object PantryDateUtils {
    private val utcTz = TimeZone.getTimeZone("UTC")

    fun todayStartMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val local = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val utc = Calendar.getInstance(utcTz).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH),
                0, 0, 0
            )
            set(Calendar.MILLISECOND, 0)
        }
        return utc.timeInMillis
    }

    fun normalizeToUtcMidnight(year: Int, monthZeroBased: Int, dayOfMonth: Int): Long {
        return Calendar.getInstance(utcTz).apply {
            clear()
            set(year, monthZeroBased, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun addDays(dateMillis: Long, days: Int): Long {
        return Calendar.getInstance(utcTz).apply {
            timeInMillis = dateMillis
            add(Calendar.DAY_OF_MONTH, days)
        }.timeInMillis
    }

    fun daysBetween(fromStartMillis: Long, toStartMillis: Long): Long {
        val diff = toStartMillis - fromStartMillis
        return TimeUnit.MILLISECONDS.toDays(diff)
    }

    fun formatDate(millis: Long?): String {
        if (millis == null) return "—"
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).apply {
            timeZone = utcTz
        }
        return sdf.format(Date(millis))
    }

    fun formatShortDate(millis: Long?): String {
        if (millis == null) return "—"
        val sdf = SimpleDateFormat("dd MMM", Locale.ITALY).apply {
            timeZone = utcTz
        }
        return sdf.format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)
        return sdf.format(Date(millis))
    }

    fun monthYearKey(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        return sdf.format(Date(millis))
    }

    fun formatMonthLabel(monthKey: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM", Locale.US)
            val formatter = SimpleDateFormat("MMM yy", Locale.ITALY)
            val date = parser.parse(monthKey) ?: return monthKey
            formatter.format(date).replaceFirstChar { it.uppercase(Locale.ITALY) }
        } catch (_: Exception) {
            monthKey
        }
    }
}

data class ExpiryEvaluation(
    val daysRemaining: Long?,
    val status: ExpiryStatus,
    val badgeText: String
)

object ExpiryCalculator {
    fun evaluate(
        expiryDateMillis: Long?,
        expiryType: ExpiryType = ExpiryType.USE_BY,
        todayMillis: Long = PantryDateUtils.todayStartMillis(),
        soonThresholdDays: Int = 7
    ): ExpiryEvaluation {
        if (expiryDateMillis == null) {
            return ExpiryEvaluation(
                daysRemaining = null,
                status = ExpiryStatus.NO_EXPIRY,
                badgeText = "Senza scadenza"
            )
        }
        val days = PantryDateUtils.daysBetween(todayMillis, expiryDateMillis)
        val prefix = if (expiryType == ExpiryType.BEST_BEFORE) "TMC" else "Scad."
        return when {
            days < 0 -> ExpiryEvaluation(
                daysRemaining = days,
                status = ExpiryStatus.EXPIRED,
                badgeText = if (expiryType == ExpiryType.BEST_BEFORE) {
                    "TMC superato da ${-days}g"
                } else {
                    "Scaduto da ${-days}g"
                }
            )
            days == 0L -> ExpiryEvaluation(
                daysRemaining = 0L,
                status = ExpiryStatus.EXPIRING_TODAY,
                badgeText = if (expiryType == ExpiryType.BEST_BEFORE) "TMC oggi" else "Scade oggi"
            )
            days <= soonThresholdDays -> ExpiryEvaluation(
                daysRemaining = days,
                status = ExpiryStatus.EXPIRING_SOON,
                badgeText = "$prefix tra ${days}g"
            )
            else -> ExpiryEvaluation(
                daysRemaining = days,
                status = ExpiryStatus.FRESH,
                badgeText = "$prefix tra ${days}g"
            )
        }
    }

    fun matchingThreshold(
        expiryDateMillis: Long?,
        todayMillis: Long,
        enabledThresholds: List<Int>
    ): Int? {
        if (expiryDateMillis == null) return null
        val days = PantryDateUtils.daysBetween(todayMillis, expiryDateMillis).toInt()
        return enabledThresholds.sorted().firstOrNull { threshold -> days == threshold }
    }
}
