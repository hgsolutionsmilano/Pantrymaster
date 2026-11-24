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
}
