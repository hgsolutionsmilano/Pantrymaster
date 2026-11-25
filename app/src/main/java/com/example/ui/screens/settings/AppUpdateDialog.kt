package com.example.ui.screens.settings

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AppUpdateInfo
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.AppUpdateStatus
import com.example.data.repository.InAppUpdateDownloadState
import com.example.data.repository.InAppUpdateManager
import com.example.data.repository.SettingsRepository
import com.example.data.worker.WorkScheduler
import com.example.domain.usecase.AppVersionComparator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val settingsRepository: SettingsRepository,
    private val workScheduler: WorkScheduler
) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    private val _isPopupVisible = MutableStateFlow(false)
    private var minimizedWhileDownloading = false

    val installPromptIntents: SharedFlow<Intent> = inAppUpdateManager.installPromptIntents

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
                if (dlState is InAppUpdateDownloadState.ReadyToInstall && minimizedWhileDownloading) {
                    minimizedWhileDownloading = false
                    _availableUpdate.value = dlState.updateInfo
                    _isPopupVisible.value = true
                } else if (dlState is InAppUpdateDownloadState.Error && minimizedWhileDownloading) {
                    minimizedWhileDownloading = false
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
        _isPopupVisible.value = false
    }

    fun ignoreVersion(tagName: String) {
        minimizedWhileDownloading = false
        _isPopupVisible.value = false
        _availableUpdate.value = null
        inAppUpdateManager.cancelAndClear(tagName)
        viewModelScope.launch {
            appUpdateRepository.dismissUpdateTag(tagName)
        }
    }
}

@Composable
fun AppUpdateAvailableDialog(
    updateInfo: AppUpdateInfo,
    downloadState: InAppUpdateDownloadState,
    canInstallPackages: Boolean,
    onInstallClick: () -> Unit,
    onContinueInBackground: () -> Unit,
    onRetryClick: () -> Unit,
    onRemindLater: () -> Unit,
    onIgnoreVersion: () -> Unit
) {
    val isReady = downloadState is InAppUpdateDownloadState.ReadyToInstall
    val isDownloading = downloadState is InAppUpdateDownloadState.Downloading
    val isInstalling = downloadState is InAppUpdateDownloadState.Installing

    Dialog(
        onDismissRequest = {
            if (isDownloading) onContinueInBackground() else onRemindLater()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                // Premium Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = when {
                                isReady -> Icons.Default.CheckCircle
                                isDownloading || isInstalling -> Icons.Default.SystemUpdate
                                downloadState is InAppUpdateDownloadState.Error -> Icons.Default.ErrorOutline
                                else -> Icons.Default.NewReleases
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isReady -> "Aggiornamento pronto"
                                isInstalling -> "Installazione in corso"
                                isDownloading -> "Nuova versione disponibile"
                                else -> "Aggiornamento disponibile"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildString {
                                append("v${updateInfo.currentVersion} → v${updateInfo.latestVersion}")
                                updateInfo.latestBuildCode?.let { append(" • build $it") }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Release Notes Card
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = updateInfo.releaseTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Dynamic In-App Download / Install Status Area
                AnimatedContent(
                    targetState = downloadState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "update_download_stage"
                ) { state ->
                    when (state) {
                        is InAppUpdateDownloadState.Downloading -> {
                            val animatedProgress by animateFloatAsState(
                                targetValue = state.progress ?: 0f,
                                label = "apk_download_progress"
                            )
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Scaricamento in background...",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        state.progress?.let { pct ->
                                            Text(
                                                text = "${(pct * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    if (state.progress != null) {
                                        LinearProgressIndicator(
                                            progress = { animatedProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .testTag("update_dialog_progress_bar")
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .testTag("update_dialog_progress_bar")
                                        )
                                    }

                                    val bytesText = buildString {
                                        append(AppVersionComparator.formatBytes(state.downloadedBytes))
                                        state.totalBytes?.let { total ->
                                            append(" / ")
                                            append(AppVersionComparator.formatBytes(total))
                                        }
                                    }
                                    Text(
                                        text = "$bytesText • Ti chiederemo conferma al termine.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        is InAppUpdateDownloadState.ReadyToInstall -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Pacchetto verificato e pronto",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            val sizeLabel = AppVersionComparator.formatBytes(state.apkFile.length())
                                            Text(
                                                text = "I dati in dispensa resteranno intatti ($sizeLabel)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }

                                if (!canInstallPackages) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Al prossimo tocco consenti l'installazione aggiornamenti per PantryMaster.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is InAppUpdateDownloadState.Installing -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Text(
                                        text = "Preparazione installazione...",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }

                        is InAppUpdateDownloadState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        InAppUpdateDownloadState.Idle -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Pacchetto APK non ancora disponibile su questa release. Riprova tra poco.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (downloadState) {
                        is InAppUpdateDownloadState.Downloading -> {
                            Button(
                                onClick = onContinueInBackground,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("update_dialog_background_button")
                            ) {
                                Text("Continua in background")
                            }
                            TextButton(
                                onClick = onIgnoreVersion,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("update_dialog_ignore_button")
                            ) {
                                Text("Ignora questa versione")
                            }
                        }

                        is InAppUpdateDownloadState.ReadyToInstall -> {
                            Button(
                                onClick = onInstallClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 50.dp)
                                    .testTag("update_dialog_install_button")
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (canInstallPackages) "Aggiorna ora" else "Abilita e aggiorna")
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(
                                    onClick = onIgnoreVersion,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("update_dialog_ignore_button")
                                ) {
                                    Text("Ignora")
                                }
                                OutlinedButton(
                                    onClick = onRemindLater,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("update_dialog_later_button")
                                ) {
                                    Text("Più tardi")
                                }
                            }
                        }

                        is InAppUpdateDownloadState.Installing -> {
                            OutlinedButton(
                                onClick = onRemindLater,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Nascondi")
                            }
                        }

                        is InAppUpdateDownloadState.Error, InAppUpdateDownloadState.Idle -> {
                            Button(
                                onClick = onRetryClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("update_dialog_retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Riprova download")
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(
                                    onClick = onIgnoreVersion,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("update_dialog_ignore_button")
                                ) {
                                    Text("Ignora")
                                }
                                OutlinedButton(
                                    onClick = onRemindLater,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("update_dialog_later_button")
                                ) {
                                    Text("Più tardi")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
