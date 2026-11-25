package com.example

import com.example.data.remote.GitHubAssetDto
import com.example.data.repository.AppSigningFormatter
import com.example.data.repository.AppUpdateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSigningAndAssetSelectionTest {

    @Test
    fun signingFormatter_formatsFullColonAndShortFingerprints() {
        val raw = "ba2f1312c96112b08545a4265de4fd4364e2a3f2117b06a6c9a8b08ee20c22fd"
        val colon = AppSigningFormatter.formatColonHex(raw)
        assertTrue(colon.startsWith("BA:2F:13:12:"))
        assertTrue(colon.endsWith(":E2:0C:22:FD"))
        assertEquals("BA:2F:13:12…E2:0C:22:FD", AppSigningFormatter.shortFingerprint(raw))
        assertEquals("N/D", AppSigningFormatter.shortFingerprint(""))
    }

    @Test
    fun signingHashesCompatibility_validatesMatchingAndRotatedSignersAndRejectsMismatchOrUnsigned() {
        val releaseCert = "aaaa1111"
        val oldDebugCert = "bbbb2222"
        val rotatedNewCert = "cccc3333"

        assertTrue(
            AppSigningFormatter.areSigningHashesCompatible(
                installedActiveHashes = setOf(releaseCert),
                archiveAllHashes = setOf(releaseCert)
            )
        )
        assertTrue(
            AppSigningFormatter.areSigningHashesCompatible(
                installedActiveHashes = setOf(releaseCert),
                archiveAllHashes = setOf(rotatedNewCert, releaseCert)
            )
        )
        assertFalse(
            AppSigningFormatter.areSigningHashesCompatible(
                installedActiveHashes = setOf(oldDebugCert),
                archiveAllHashes = setOf(releaseCert)
            )
        )
        assertFalse(
            AppSigningFormatter.areSigningHashesCompatible(
                installedActiveHashes = setOf(releaseCert),
                archiveAllHashes = emptySet()
            )
        )
    }

    @Test
    fun packageIdAndVersionCodeUpgradeChecks_enforceCompatibilityAndNoDowngrade() {
        val appId = "com.aistudio.pantrymaster.vrkqzm"
        assertTrue(AppSigningFormatter.isPackageCompatible(appId, appId))
        assertFalse(AppSigningFormatter.isPackageCompatible(appId, "com.example.other"))
        assertFalse(AppSigningFormatter.isPackageCompatible(appId, null))

        assertTrue(AppSigningFormatter.isValidVersionUpgrade(10L, 11L))
        assertTrue(AppSigningFormatter.isValidVersionUpgrade(10L, 10L))
        assertFalse(AppSigningFormatter.isValidVersionUpgrade(10L, 9L))
    }

    @Test
    fun selectPreferredApkAsset_prefersReleaseApkOverDebugApkAndSkipsAab() {
        val assets = listOf(
            GitHubAssetDto("PantryMaster-v1.2.0.aab", 9_000_000L, "https://example.com/app.aab"),
            GitHubAssetDto("PantryMaster-v1.2.0-debug.apk", 12_000_000L, "https://example.com/debug.apk"),
            GitHubAssetDto("PantryMaster-v1.2.0.apk", 10_000_000L, "https://example.com/release.apk")
        )
        val picked = AppUpdateRepository.selectPreferredApkAsset(assets)
        assertEquals("PantryMaster-v1.2.0.apk", picked?.name)

        val debugOnly = listOf(
            GitHubAssetDto("PantryMaster-v1.2.0-debug.apk", 12_000_000L, "https://example.com/debug.apk")
        )
        assertEquals("PantryMaster-v1.2.0-debug.apk", AppUpdateRepository.selectPreferredApkAsset(debugOnly)?.name)

        assertNull(AppUpdateRepository.selectPreferredApkAsset(emptyList()))
    }
}
