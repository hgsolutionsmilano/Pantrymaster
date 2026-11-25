package com.example.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SettingsEntity
import com.example.data.preferences.UserPreferencesDataStore
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.AppUpdateStatus
import com.example.data.repository.BackupOperationResult
import com.example.data.repository.BackupRepository
import com.example.data.repository.InAppUpdateDownloadState
import com.example.data.repository.InAppUpdateManager
import com.example.data.repository.SettingsRepository
import com.example.data.worker.ExpiryNotificationWorker
import com.example.data.worker.WorkScheduler
import com.example.domain.model.ThemePreference
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.AppVersionComparator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: SettingsEntity = SettingsEntity(id = 1),
    val categories: List<CategoryEntity> = emptyList(),
    val homeTitle: String = UserPreferencesDataStore.DEFAULT_HOME_TITLE,
    val isBusy: Boolean = false,
    val autoUpdateEnabled: Boolean = true,
    val effectiveGithubRepo: String = "",
    val lastUpdateCheckAt: Long = 0L,
    val updateStatus: AppUpdateStatus = AppUpdateStatus.Idle,
    val downloadState: InAppUpdateDownloadState = InAppUpdateDownloadState.Idle
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val inAppUpdateManager: InAppUpdateManager,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val workScheduler: WorkScheduler
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    private val _updateStatus = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.Idle)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        settingsRepository.observeCategories(),
        userPreferencesDataStore.homeTitle,
        _isBusy,
        combine(
            appUpdateRepository.autoUpdateEnabled,
            appUpdateRepository.effectiveRepositorySlug,
            appUpdateRepository.lastUpdateCheckAt,
            _updateStatus,
            inAppUpdateManager.state
        ) { autoUpdate, repo, lastCheck, status, dlState ->
            UpdateSettingsSlice(autoUpdate, repo, lastCheck, status, dlState)
        }
    ) { settings, categories, homeTitle, busy, updateSlice ->
        SettingsUiState(
            settings = settings,
            categories = categories,
            homeTitle = homeTitle,
            isBusy = busy,
            autoUpdateEnabled = updateSlice.autoUpdate,
            effectiveGithubRepo = updateSlice.repo,
            lastUpdateCheckAt = updateSlice.lastCheck,
            updateStatus = updateSlice.status,
            downloadState = updateSlice.downloadState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    private data class UpdateSettingsSlice(
        val autoUpdate: Boolean,
        val repo: String,
        val lastCheck: Long,
        val status: AppUpdateStatus,
        val downloadState: InAppUpdateDownloadState
    )

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { settingsRepository.updateTheme(theme) }
    }

    fun saveHomeTitle(rawInput: String) {
        val trimmed = rawInput.trim().take(UserPreferencesDataStore.MAX_HOME_TITLE_LENGTH)
        viewModelScope.launch {
            userPreferencesDataStore.setHomeTitle(trimmed)
            _message.value = if (trimmed.isBlank()) {
                "Nome Home ripristinato a ${UserPreferencesDataStore.DEFAULT_HOME_TITLE}."
            } else {
                "Nome Home aggiornato."
            }
        }
    }

    fun resetHomeTitle() {
        saveHomeTitle("")
    }

    fun updateNotificationSettings(
        enabled: Boolean,
        notify7: Boolean,
        notify3: Boolean,
        notify1: Boolean,
        notify0: Boolean
    ) {
        viewModelScope.launch {
            settingsRepository.updateNotifications(enabled, notify7, notify3, notify1, notify0)
            if (enabled) {
                workScheduler.scheduleDailyExpiryCheck()
            } else {
                workScheduler.cancelDailyExpiryCheck()
            }
        }
    }

    fun triggerExpiryCheckNow() {
        viewModelScope.launch {
            val workId = workScheduler.triggerImmediateExpiryCheck(isManual = true)
            val finalInfo = workScheduler.observeWorkInfo(workId)
                .first { info -> info != null && info.state.isFinished }
            if (finalInfo == null || finalInfo.state != WorkInfo.State.SUCCEEDED) {
                _message.value = "Impossibile completare il controllo scadenze."
                return@launch
            }
            val permitted = finalInfo.outputData.getBoolean(
                ExpiryNotificationWorker.KEY_PERMISSION_GRANTED,
                true
            )
            val notifiedCount = finalInfo.outputData.getInt(
                ExpiryNotificationWorker.KEY_NOTIFIED_COUNT,
                0
            )
            _message.value = when {
                !permitted -> "Permesso notifiche non attivo sul dispositivo. Abilitalo dalle impostazioni."
                notifiedCount == 1 -> "Notifica inviata per 1 prodotto in scadenza o scaduto."
                notifiedCount > 1 -> "Notifica inviata per $notifiedCount prodotti in scadenza o scaduti."
                else -> "Nessun prodotto in scadenza o oltre il termine per le soglie attive."
            }
        }
    }

    fun setDefaultUnit(unit: UnitOfMeasure) {
        viewModelScope.launch { settingsRepository.updateDefaultUnit(unit) }
    }

    fun saveCategory(existingId: String?, name: String, iconName: String, colorHex: String) {
        viewModelScope.launch {
            settingsRepository.saveCategory(
                CategoryEntity(
                    id = existingId.orEmpty(),
                    name = name,
                    iconName = iconName,
                    colorHex = colorHex,
                    isDefault = false,
                    sortOrder = 50
                )
            )
            _message.value = "Categoria salvata."
        }
    }

    fun deleteCustomCategory(categoryId: String) {
        viewModelScope.launch {
            settingsRepository.deleteCustomCategory(categoryId)
            _message.value = "Categoria eliminata."
        }
    }

    fun exportFullBackupZip(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = backupRepository.exportFullBackupZipToUri(uri)
            _isBusy.value = false
            _message.value = when (res) {
                is BackupOperationResult.Success -> res.message
                is BackupOperationResult.Error -> res.message
            }
        }
    }

    fun importFullBackupZip(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = backupRepository.importFullBackupZipFromUri(uri)
            _isBusy.value = false
            _message.value = when (res) {
                is BackupOperationResult.Success -> res.message
                is BackupOperationResult.Error -> res.message
            }
        }
    }

    fun exportJson(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = backupRepository.exportJsonToUri(uri)
            _isBusy.value = false
            _message.value = when (res) {
                is BackupOperationResult.Success -> res.message
                is BackupOperationResult.Error -> res.message
            }
        }
    }

    fun importJson(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = backupRepository.importJsonFromUri(uri)
            _isBusy.value = false
            _message.value = when (res) {
                is BackupOperationResult.Success -> res.message
                is BackupOperationResult.Error -> res.message
            }
        }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appUpdateRepository.setAutoUpdateEnabled(enabled)
            if (enabled) {
                workScheduler.schedulePeriodicUpdateCheck()
            } else {
                workScheduler.cancelPeriodicUpdateCheck()
            }
        }
    }

    fun saveGithubRepository(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isNotBlank() && AppVersionComparator.parseRepoSlug(trimmed) == null) {
            _message.value = "Formato non valido. Usa 'utente/repository'."
            return
        }
        viewModelScope.launch {
            appUpdateRepository.setCustomGithubRepo(trimmed)
            _updateStatus.value = AppUpdateStatus.Idle
            _message.value = if (trimmed.isBlank()) {
                "Repository personalizzato rimosso."
            } else {
                "Repository GitHub salvato."
            }
        }
    }

    fun checkForUpdatesNow() {
        viewModelScope.launch {
            _updateStatus.value = AppUpdateStatus.Checking
            val result = appUpdateRepository.checkForUpdates(force = true)
            _updateStatus.value = result
        }
    }

    fun startInAppUpdateDownload(forceRestart: Boolean = false) {
        val info = (_updateStatus.value as? AppUpdateStatus.UpdateAvailable)?.info ?: return
        inAppUpdateManager.startBackgroundDownload(info, forceRestart = forceRestart)
    }

    fun canRequestPackageInstalls(): Boolean = inAppUpdateManager.canRequestPackageInstalls()

    fun installDownloadedUpdate() {
        inAppUpdateManager.installDownloadedUpdate()
    }

    fun clearMessage() {
        _message.value = null
    }
}
