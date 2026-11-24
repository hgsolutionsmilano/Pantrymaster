package com.example.data.repository

import com.example.BuildConfig
import com.example.data.preferences.UserPreferencesDataStore
import com.example.data.remote.GitHubReleaseApi
import com.example.domain.usecase.AppVersionComparator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long?,
    val publishedAt: String?
) {
    val preferredOpenUrl: String
        get() = apkDownloadUrl ?: releasePageUrl
}

sealed interface AppUpdateStatus {
    data object Idle : AppUpdateStatus
    data object Checking : AppUpdateStatus
    data class UpToDate(val currentVersion: String, val checkedAtMillis: Long) : AppUpdateStatus
    data class UpdateAvailable(val info: AppUpdateInfo) : AppUpdateStatus
    data object NotConfigured : AppUpdateStatus
    data class Error(val message: String) : AppUpdateStatus
}

@Singleton
class AppUpdateRepository @Inject constructor(
    private val gitHubReleaseApi: GitHubReleaseApi,
    private val preferencesDataStore: UserPreferencesDataStore
) {
    val autoUpdateEnabled: Flow<Boolean> = preferencesDataStore.autoUpdateEnabled
    val customGithubRepo: Flow<String> = preferencesDataStore.customGithubRepo
    val lastUpdateCheckAt: Flow<Long> = preferencesDataStore.lastUpdateCheckAt

    val effectiveRepositorySlug: Flow<String> = preferencesDataStore.customGithubRepo.map { custom ->
        custom.trim().ifBlank { BuildConfig.GITHUB_REPOSITORY.trim() }
    }

    suspend fun resolveRepositoryPair(): Pair<String, String>? {
        val custom = preferencesDataStore.customGithubRepo.first().trim()
        val raw = custom.ifBlank { BuildConfig.GITHUB_REPOSITORY.trim() }
        return AppVersionComparator.parseRepoSlug(raw)
    }

    suspend fun checkForUpdates(force: Boolean): AppUpdateStatus {
        val repoPair = resolveRepositoryPair() ?: return AppUpdateStatus.NotConfigured

        if (!force) {
            val autoEnabled = preferencesDataStore.autoUpdateEnabled.first()
            if (!autoEnabled) return AppUpdateStatus.Idle
            val lastCheck = preferencesDataStore.lastUpdateCheckAt.first()
            val now = System.currentTimeMillis()
            if (lastCheck > 0L && (now - lastCheck) < AUTO_CHECK_INTERVAL_MILLIS) {
                return AppUpdateStatus.Idle
            }
        }

        return try {
            val response = gitHubReleaseApi.getLatestRelease(
                owner = repoPair.first,
                repo = repoPair.second
            )
            val now = System.currentTimeMillis()
            preferencesDataStore.setLastUpdateCheckAt(now)

            if (!response.isSuccessful) {
                val message = when (response.code()) {
                    404 -> "Nessuna release pubblicata trovata su ${repoPair.first}/${repoPair.second}."
                    403, 429 -> "Limite richieste GitHub raggiunto. Riprova tra qualche minuto."
                    else -> "Errore GitHub Releases (HTTP ${response.code()})."
                }
                return AppUpdateStatus.Error(message)
            }

            val release = response.body()
                ?: return AppUpdateStatus.Error("Risposta vuota da GitHub Releases.")

            if (release.draft || release.prerelease || release.tagName.isBlank()) {
                return AppUpdateStatus.UpToDate(BuildConfig.VERSION_NAME, now)
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val isNewer = AppVersionComparator.isRemoteNewer(
                currentVersion = currentVersion,
                remoteVersion = release.tagName
            )

            if (!isNewer) {
                return AppUpdateStatus.UpToDate(currentVersion, now)
            }

            val ignoredTag = preferencesDataStore.ignoredUpdateTag.first()
            if (!force && ignoredTag == release.tagName) {
                return AppUpdateStatus.UpToDate(currentVersion, now)
            }

            val apkAsset = release.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true) &&
                    asset.browserDownloadUrl.isNotBlank()
            }

            val info = AppUpdateInfo(
                currentVersion = currentVersion,
                latestVersion = AppVersionComparator.normalizeTag(release.tagName),
                tagName = release.tagName,
                releaseTitle = release.name?.takeIf { it.isNotBlank() }
                    ?: "PantryMaster ${release.tagName}",
                releaseNotes = release.body?.trim().orEmpty(),
                releasePageUrl = release.htmlUrl,
                apkDownloadUrl = apkAsset?.browserDownloadUrl,
                apkFileName = apkAsset?.name,
                apkSizeBytes = apkAsset?.size?.takeIf { it > 0L },
                publishedAt = release.publishedAt
            )
            AppUpdateStatus.UpdateAvailable(info)
        } catch (_: UnknownHostException) {
            AppUpdateStatus.Error("Connessione Internet non disponibile.")
        } catch (_: SocketTimeoutException) {
            AppUpdateStatus.Error("Timeout durante il controllo aggiornamenti.")
        } catch (_: IOException) {
            AppUpdateStatus.Error("Errore di rete durante il controllo aggiornamenti.")
        } catch (e: Exception) {
            AppUpdateStatus.Error(e.localizedMessage ?: "Errore imprevisto durante il controllo aggiornamenti.")
        }
    }

    suspend fun dismissUpdateTag(tagName: String) {
        preferencesDataStore.setIgnoredUpdateTag(tagName)
    }

    suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        preferencesDataStore.setAutoUpdateEnabled(enabled)
    }

    suspend fun setCustomGithubRepo(repoSlug: String) {
        preferencesDataStore.setCustomGithubRepo(repoSlug)
        preferencesDataStore.setLastUpdateCheckAt(0L)
        preferencesDataStore.setIgnoredUpdateTag(null)
    }

    suspend fun shouldNotifyForTag(tagName: String): Boolean {
        val lastNotified = preferencesDataStore.lastNotifiedUpdateTag.first()
        return lastNotified != tagName
    }

    suspend fun markNotifiedForTag(tagName: String) {
        preferencesDataStore.setLastNotifiedUpdateTag(tagName)
    }

    companion object {
        private const val AUTO_CHECK_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L
    }
}
