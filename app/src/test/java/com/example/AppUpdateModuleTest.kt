package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.UserPreferencesDataStore
import com.example.data.remote.GitHubAssetDto
import com.example.data.remote.GitHubReleaseApi
import com.example.data.remote.GitHubReleaseDto
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.AppUpdateStatus
import com.example.domain.usecase.AppVersionComparator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateModuleTest {

    @Test
    fun semanticVersionComparison_handlesProgressiveAndPrereleaseVersions() {
        assertTrue(AppVersionComparator.isRemoteNewer("1.0.0", "v1.0.1"))
        assertTrue(AppVersionComparator.isRemoteNewer("1.0.9", "1.0.10"))
        assertTrue(AppVersionComparator.isRemoteNewer("1.0.5-ci.12", "1.0.5"))
        assertFalse(AppVersionComparator.isRemoteNewer("1.2.0", "v1.2.0"))
        assertFalse(AppVersionComparator.isRemoteNewer("2.0.0", "v1.9.9"))
    }

    @Test
    fun repoSlugParser_acceptsSlugAndFullGithubUrl() {
        assertEquals("acme" to "PantryMaster", AppVersionComparator.parseRepoSlug("acme/PantryMaster"))
        assertEquals(
            "acme" to "PantryMaster",
            AppVersionComparator.parseRepoSlug("https://github.com/acme/PantryMaster.git")
        )
        assertNull(AppVersionComparator.parseRepoSlug("invalid_repo"))
        assertNull(AppVersionComparator.parseRepoSlug("a/b/c"))
    }

    @Test
    fun updateRepository_detectsNewerReleaseAndRespectsIgnoreOnAutoCheck() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = UserPreferencesDataStore(context)
        val fakeApi = object : GitHubReleaseApi {
            override suspend fun getLatestRelease(owner: String, repo: String): Response<GitHubReleaseDto> {
                return Response.success(
                    GitHubReleaseDto(
                        tagName = "v99.0.0",
                        name = "PantryMaster v99.0.0",
                        body = "Nuova versione con miglioramenti",
                        draft = false,
                        prerelease = false,
                        htmlUrl = "https://github.com/$owner/$repo/releases/tag/v99.0.0",
                        publishedAt = "2026-11-24T10:00:00Z",
                        assets = listOf(
                            GitHubAssetDto(
                                name = "PantryMaster-v99.0.0.apk",
                                size = 14_500_000L,
                                browserDownloadUrl = "https://github.com/$owner/$repo/releases/download/v99.0.0/PantryMaster-v99.0.0.apk"
                            )
                        )
                    )
                )
            }
        }

        val repo = AppUpdateRepository(fakeApi, prefs)
        repo.setAutoUpdateEnabled(true)
        repo.setCustomGithubRepo("acme/PantryMaster")

        val status = repo.checkForUpdates(force = true)
        assertTrue(status is AppUpdateStatus.UpdateAvailable)
        val available = status as AppUpdateStatus.UpdateAvailable
        assertEquals("99.0.0", available.info.latestVersion)
        assertEquals("PantryMaster-v99.0.0.apk", available.info.apkFileName)

        // Ignora questa versione e verifica che auto-check non la riproponga
        repo.dismissUpdateTag("v99.0.0")
        prefs.setLastUpdateCheckAt(0L)
        val autoAfterIgnore = repo.checkForUpdates(force = false)
        assertTrue(autoAfterIgnore is AppUpdateStatus.UpToDate)
    }

    @Test
    fun updateRepository_handles404Gracefully() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = UserPreferencesDataStore(context)
        val fakeApi = object : GitHubReleaseApi {
            override suspend fun getLatestRelease(owner: String, repo: String): Response<GitHubReleaseDto> {
                return Response.error(
                    404,
                    "{}".toResponseBody("application/json".toMediaType())
                )
            }
        }
        val repo = AppUpdateRepository(fakeApi, prefs)
        repo.setCustomGithubRepo("acme/PantryMaster")

        val status = repo.checkForUpdates(force = true)
        assertTrue(status is AppUpdateStatus.Error)
    }

    @Test
    fun homeTitle_defaultsUpdatesAndResetsToPantryMaster() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = UserPreferencesDataStore(context)

        assertEquals(UserPreferencesDataStore.DEFAULT_HOME_TITLE, prefs.homeTitle.first())

        prefs.setHomeTitle("  Casa Rossi  ")
        assertEquals("Casa Rossi", prefs.homeTitle.first())

        prefs.setHomeTitle("   ")
        assertEquals(UserPreferencesDataStore.DEFAULT_HOME_TITLE, prefs.homeTitle.first())
    }

    @Test
    fun buildCodeExtractionAndEligibility_blocksDowngradeAndAllowsHigherBuild() {
        assertEquals(42, AppVersionComparator.extractBuildCode("PantryMaster v1.0.5 (build 42)"))
        assertNull(AppVersionComparator.extractBuildCode("PantryMaster v1.0.5"))

        // Higher semver & higher build -> eligible
        assertTrue(
            AppVersionComparator.isUpdateEligible(
                currentVersion = "1.0.0",
                currentBuildCode = 5,
                remoteTag = "v1.0.1",
                remoteReleaseName = "PantryMaster v1.0.1 (build 6)"
            )
        )
        // Same semver but higher build -> eligible
        assertTrue(
            AppVersionComparator.isUpdateEligible(
                currentVersion = "1.0.5",
                currentBuildCode = 5,
                remoteTag = "v1.0.5",
                remoteReleaseName = "PantryMaster v1.0.5 (build 8)"
            )
        )
        // Higher semver tag but lower buildCode -> NOT eligible (would fail Android install)
        assertFalse(
            AppVersionComparator.isUpdateEligible(
                currentVersion = "1.0.0",
                currentBuildCode = 10,
                remoteTag = "v1.0.2",
                remoteReleaseName = "PantryMaster v1.0.2 (build 9)"
            )
        )
    }

    @Test
    fun startupCheck_bypassesCooldownIntervalWhileRespectingAutoUpdateToggle() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = UserPreferencesDataStore(context)
        val fakeApi = object : GitHubReleaseApi {
            override suspend fun getLatestRelease(owner: String, repo: String): Response<GitHubReleaseDto> {
                return Response.success(
                    GitHubReleaseDto(
                        tagName = "v99.1.0",
                        name = "PantryMaster v99.1.0 (build 999)",
                        body = "Fix e miglioramenti",
                        draft = false,
                        prerelease = false,
                        htmlUrl = "https://github.com/$owner/$repo/releases/tag/v99.1.0",
                        assets = listOf(
                            GitHubAssetDto(
                                name = "PantryMaster-v99.1.0.apk",
                                size = 12_000_000L,
                                browserDownloadUrl = "https://github.com/$owner/$repo/releases/download/v99.1.0/PantryMaster-v99.1.0.apk"
                            )
                        )
                    )
                )
            }
        }

        val repo = AppUpdateRepository(fakeApi, prefs)
        repo.setAutoUpdateEnabled(true)
        repo.setCustomGithubRepo("acme/PantryMaster")
        prefs.setLastUpdateCheckAt(System.currentTimeMillis())

        // Without bypassInterval, recent check returns Idle
        val throttled = repo.checkForUpdates(force = false, bypassInterval = false)
        assertTrue(throttled is AppUpdateStatus.Idle)

        // Startup check (bypassInterval = true) detects the update immediately
        val startup = repo.checkForUpdates(force = false, bypassInterval = true)
        assertTrue(startup is AppUpdateStatus.UpdateAvailable)
        assertEquals(999, (startup as AppUpdateStatus.UpdateAvailable).info.latestBuildCode)

        // When autoUpdateEnabled is false, startup check remains Idle
        repo.setAutoUpdateEnabled(false)
        val disabled = repo.checkForUpdates(force = false, bypassInterval = true)
        assertTrue(disabled is AppUpdateStatus.Idle)
        repo.setAutoUpdateEnabled(true)
    }
}
