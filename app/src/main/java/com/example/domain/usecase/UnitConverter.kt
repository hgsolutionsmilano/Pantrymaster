package com.example.domain.usecase

import com.example.domain.model.UnitOfMeasure
import java.util.Locale
import kotlin.math.abs

object UnitConverter {

    fun formatQuantity(quantity: Double, unit: UnitOfMeasure): String {
        val clean = if (abs(quantity) < 1e-6) 0.0 else quantity
        val formattedNumber = if (abs(clean - clean.toLong()) < 1e-4) {
            clean.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", clean)
                .trimEnd('0')
                .trimEnd('.')
        }
        return "$formattedNumber ${unit.symbol}"
    }
}
