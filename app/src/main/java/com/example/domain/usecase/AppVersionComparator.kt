package com.example.domain.usecase

import java.util.Locale
import kotlin.math.max

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        if (preRelease == null && other.preRelease == null) return 0
        if (preRelease == null) return 1
        if (other.preRelease == null) return -1

        val aParts = preRelease.split('.')
        val bParts = other.preRelease.split('.')
        val count = max(aParts.size, bParts.size)
        for (i in 0 until count) {
            val a = aParts.getOrNull(i) ?: return -1
            val b = bParts.getOrNull(i) ?: return 1
            val aInt = a.toIntOrNull()
            val bInt = b.toIntOrNull()
            val cmp = when {
                aInt != null && bInt != null -> aInt.compareTo(bInt)
                aInt != null -> -1
                bInt != null -> 1
                else -> a.compareTo(b)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }
}

object AppVersionComparator {

    private val semVerRegex = Regex(
        pattern = """^[vV]?\s*(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.\-]+))?(?:\+[0-9A-Za-z.\-]+)?$"""
    )

    fun parse(rawVersion: String): SemanticVersion? {
        val cleaned = rawVersion.trim()
        val match = semVerRegex.matchEntire(cleaned) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: 0
        val patch = match.groupValues[3].toIntOrNull() ?: 0
        val pre = match.groupValues[4].takeIf { it.isNotBlank() }
        return SemanticVersion(major = major, minor = minor, patch = patch, preRelease = pre)
    }

    fun normalizeTag(rawVersion: String): String {
        return rawVersion.trim().removePrefix("v").removePrefix("V")
    }

    fun isRemoteNewer(currentVersion: String, remoteVersion: String): Boolean {
        val current = parse(currentVersion)
        val remote = parse(remoteVersion)
        if (current != null && remote != null) {
            return remote > current
        }
        return normalizeTag(remoteVersion) != normalizeTag(currentVersion)
    }

    fun parseRepoSlug(raw: String): Pair<String, String>? {
        val cleaned = raw.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
        val parts = cleaned.split('/')
        if (parts.size != 2) return null
        val owner = parts[0].trim()
        val repo = parts[1].removeSuffix(".git").trim()
        if (owner.isBlank() || repo.isBlank()) return null
        if (!owner.matches(Regex("[A-Za-z0-9_.-]+")) || !repo.matches(Regex("[A-Za-z0-9_.-]+"))) {
            return null
        }
        return owner to repo
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.ITALIAN, "%.1f MB", mb)
        } else {
            val kb = bytes / 1024.0
            String.format(Locale.ITALIAN, "%.0f KB", kb)
        }
    }
}
