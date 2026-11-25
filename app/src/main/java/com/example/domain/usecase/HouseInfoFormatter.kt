package com.example.domain.usecase

import com.example.data.local.entity.HouseInfoEntryEntity
import com.example.domain.model.HouseInfoCategory

object HouseInfoFormatter {

    fun formatDimensions(entry: HouseInfoEntryEntity): String {
        val parts = buildList {
            entry.widthValue.trim().takeIf { it.isNotEmpty() }?.let { add("L $it") }
            entry.heightValue.trim().takeIf { it.isNotEmpty() }?.let { add("H $it") }
            entry.depthValue.trim().takeIf { it.isNotEmpty() }?.let { add("P $it") }
        }
        if (parts.isEmpty()) return ""
        return "${parts.joinToString(" × ")} ${entry.measurementUnit.symbol}"
    }

    fun maskSecret(secret: String): String {
        if (secret.isBlank()) return ""
        return "•".repeat(secret.length.coerceIn(6, 12))
    }

    fun buildShareText(entry: HouseInfoEntryEntity, includeSecret: Boolean = false): String {
        return buildString {
            append(entry.title.trim())
            if (entry.subtitle.isNotBlank()) {
                append(" (${entry.subtitle.trim()})")
            }
            appendLine()
            append("Categoria: ${entry.category.labelIt}")

            when (entry.category) {
                HouseInfoCategory.PHONE_NUMBER -> {
                    if (entry.primaryValue.isNotBlank()) {
                        appendLine()
                        append("Telefono: ${entry.primaryValue.trim()}")
                    }
                    if (entry.secondaryValue.isNotBlank()) {
                        appendLine()
                        append("Altro contatto: ${entry.secondaryValue.trim()}")
                    }
                }

                HouseInfoCategory.MEASUREMENT -> {
                    val dims = formatDimensions(entry)
                    if (dims.isNotBlank()) {
                        appendLine()
                        append("Misure: $dims")
                    }
                    if (entry.primaryValue.isNotBlank()) {
                        appendLine()
                        append("Riferimento: ${entry.primaryValue.trim()}")
                    }
                }

                HouseInfoCategory.PHOTO_DETAIL -> {
                    if (entry.primaryValue.isNotBlank()) {
                        appendLine()
                        append("Dettaglio: ${entry.primaryValue.trim()}")
                    }
                }

                HouseInfoCategory.PASSWORD_NOTE -> {
                    if (entry.primaryValue.isNotBlank()) {
                        appendLine()
                        append("Rete / Utente: ${entry.primaryValue.trim()}")
                    }
                    if (includeSecret && entry.secretValue.isNotBlank()) {
                        appendLine()
                        append("Codice / Password: ${entry.secretValue.trim()}")
                    }
                }
            }

            if (entry.notes.isNotBlank()) {
                appendLine()
                append("Note: ${entry.notes.trim()}")
            }
        }.trim()
    }
}
