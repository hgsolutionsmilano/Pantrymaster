package com.example.data.repository

import java.io.File
import java.security.MessageDigest

data class AppSigningDetails(
    val isDebugBuild: Boolean = false,
    val sha256Hex: String = "",
    val sha256ColonHex: String = "",
    val shortFingerprint: String = "N/D"
) {
    val buildTypeLabel: String
        get() = if (isDebugBuild) "Debug" else "Release"
}

sealed interface InAppUpdateDownloadState {
    data object Idle : InAppUpdateDownloadState

    data class Downloading(
        val updateInfo: AppUpdateInfo,
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val progress: Float?
    ) : InAppUpdateDownloadState

    data class ReadyToInstall(
        val updateInfo: AppUpdateInfo,
        val apkFile: File
    ) : InAppUpdateDownloadState

    data class Installing(
        val updateInfo: AppUpdateInfo
    ) : InAppUpdateDownloadState

    data class SignatureMismatch(
        val updateInfo: AppUpdateInfo,
        val apkFile: File,
        val installedFingerprint: String,
        val archiveFingerprint: String,
        val installedIsDebug: Boolean,
        val archiveIsDebug: Boolean
    ) : InAppUpdateDownloadState

    data class Error(
        val updateInfo: AppUpdateInfo,
        val message: String,
        val cachedApkFile: File? = null
    ) : InAppUpdateDownloadState
}

sealed interface ApkValidationResult {
    data object Valid : ApkValidationResult

    data class SignatureMismatch(
        val installedFingerprint: String,
        val archiveFingerprint: String,
        val installedIsDebug: Boolean,
        val archiveIsDebug: Boolean
    ) : ApkValidationResult

    data class Invalid(val message: String) : ApkValidationResult
}

object AppSigningFormatter {

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun formatColonHex(rawHex: String): String {
        val clean = rawHex.replace(":", "").trim().uppercase()
        if (clean.isEmpty()) return "N/D"
        return clean.chunked(2).joinToString(":")
    }

    fun shortFingerprint(hexOrColonHex: String): String {
        val colon = formatColonHex(hexOrColonHex)
        if (colon == "N/D") return colon
        val parts = colon.split(":")
        return if (parts.size <= 8) {
            colon
        } else {
            "${parts.take(4).joinToString(":")}…${parts.takeLast(4).joinToString(":")}"
        }
    }

    fun areSigningHashesCompatible(
        installedActiveHashes: Set<String>,
        archiveAllHashes: Set<String>
    ): Boolean {
        if (installedActiveHashes.isEmpty() || archiveAllHashes.isEmpty()) {
            return true
        }
        return installedActiveHashes.any { it in archiveAllHashes }
    }
}
