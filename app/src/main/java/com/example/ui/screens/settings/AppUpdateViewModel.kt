package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AppUpdateInfo
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.AppUpdateStatus
import com.example.data.repository.BackupOperationResult
import com.example.data.repository.BackupRepository
import com.example.data.repository.InAppUpdateDownloadState
import com.example.data.repository.InAppUpdateManager
import com.example.data.repository.SettingsRepository
import com.example.data.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class StartupUpdateDialogState(
    val updateInfo: AppUpdateInfo,
    val downloadState: InAppUpdateDownloadState
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val inAppUpdateManager: InAppUpdateManager,
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository,
    private val workScheduler: WorkScheduler
) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    private val _isPopupVisible = MutableStateFlow(false)
    private val _backupActionMessage = MutableStateFlow<String?>(null)
    private val _isExporting = MutableStateFlow(false)
    private var minimizedWhileDownloading = false

    val installPromptIntents: SharedFlow<Intent> = inAppUpdateManager.installPromptIntents
    val backupActionMessage: StateFlow<String?> = _backupActionMessage.asStateFlow()
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    val startupDialogState: StateFlow<StartupUpdateDialogState?> = combine(
        _availableUpdate,
        _isPopupVisible,
        inAppUpdateManager.state
    ) { info, visible, downloadState ->
        if (!visible || info == null) {
            null
        } else {
            StartupUpdateDialogState(
                updateInfo = info,
                downloadState = downloadState
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.observeSettings()
        .map { it.notificationsEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private var hasCheckedStartup = false

    init {
        viewModelScope.launch {
            inAppUpdateManager.state.collect { dlState ->
                when (dlState) {
                    is InAppUpdateDownloadState.ReadyToInstall -> {
                        if (minimizedWhileDownloading) {
                            minimizedWhileDownloading = false
                            _availableUpdate.value = dlState.updateInfo
                            _isPopupVisible.value = true
                        }
                    }
                    is InAppUpdateDownloadState.SignatureMismatch -> {
                        if (minimizedWhileDownloading) {
                            minimizedWhileDownloading = false
                            _availableUpdate.value = dlState.updateInfo
                            _isPopupVisible.value = true
                        }
                    }
                    is InAppUpdateDownloadState.Error -> {
                        if (minimizedWhileDownloading) {
                            minimizedWhileDownloading = false
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun checkOnStartup() {
        if (hasCheckedStartup) return
        hasCheckedStartup = true
        viewModelScope.launch {
            val status = appUpdateRepository.checkForUpdates(force = false, bypassInterval = true)
            if (status is AppUpdateStatus.UpdateAvailable) {
                val info = status.info
                _availableUpdate.value = info
                _isPopupVisible.value = true
                if (!info.apkDownloadUrl.isNullOrBlank()) {
                    inAppUpdateManager.startBackgroundDownload(info)
                }
            }
        }
    }

    fun onNotificationPermissionGranted() {
        workScheduler.scheduleDailyExpiryCheck()
        workScheduler.triggerImmediateExpiryCheck(isManual = false)
    }

    fun canRequestPackageInstalls(): Boolean = inAppUpdateManager.canRequestPackageInstalls()

    fun installReadyUpdate() {
        inAppUpdateManager.installDownloadedUpdate()
    }

    fun exportBackupZip(uri: Uri) {
        viewModelScope.launch {
            _isExporting.value = true
            val res = backupRepository.exportFullBackupZipToUri(uri)
            _isExporting.value = false
            _backupActionMessage.value = when (res) {
                is BackupOperationResult.Success -> res.message
                is BackupOperationResult.Error -> res.message
            }
        }
    }

    fun exportPendingApk(uri: Uri) {
        val apkFile = when (val dlState = inAppUpdateManager.state.value) {
            is InAppUpdateDownloadState.SignatureMismatch -> dlState.apkFile
            is InAppUpdateDownloadState.ReadyToInstall -> dlState.apkFile
            else -> null
        }
        if (apkFile == null || !apkFile.exists()) {
            _backupActionMessage.value = "Pacchetto APK non più presente in cache."
            return
        }
        viewModelScope.launch {
            _isExporting.value = true
            val ok = inAppUpdateManager.exportApkToUri(apkFile, uri)
            _isExporting.value = false
            _backupActionMessage.value = if (ok) {
                "APK salvato! Disinstalla questa app, apri l'APK salvato e ripristina il backup da Impostazioni."
            } else {
                "Impossibile salvare il pacchetto APK nella posizione scelta."
            }
        }
    }

    fun clearBackupActionMessage() {
        _backupActionMessage.value = null
    }

    fun retryDownload() {
        val info = _availableUpdate.value ?: return
        if (!info.apkDownloadUrl.isNullOrBlank()) {
            inAppUpdateManager.startBackgroundDownload(info, forceRestart = true)
        } else {
            viewModelScope.launch {
                val refreshed = appUpdateRepository.checkForUpdates(force = true, bypassInterval = true)
                if (refreshed is AppUpdateStatus.UpdateAvailable) {
                    _availableUpdate.value = refreshed.info
                    if (!refreshed.info.apkDownloadUrl.isNullOrBlank()) {
                        inAppUpdateManager.startBackgroundDownload(refreshed.info, forceRestart = true)
                    }
                }
            }
        }
    }

    fun continueInBackground() {
        minimizedWhileDownloading = inAppUpdateManager.state.value is InAppUpdateDownloadState.Downloading
        _isPopupVisible.value = false
    }

    fun remindLater() {
        minimizedWhileDownloading = false
        _backupActionMessage.value = null
        _isPopupVisible.value = false
    }

    fun ignoreVersion(tagName: String) {
        minimizedWhileDownloading = false
        _backupActionMessage.value = null
        _isPopupVisible.value = false
        _availableUpdate.value = null
        inAppUpdateManager.cancelAndClear(tagName)
        viewModelScope.launch {
            appUpdateRepository.dismissUpdateTag(tagName)
        }
    }
}
