package com.example.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AppUpdateInfo
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.AppUpdateStatus
import com.example.data.repository.SettingsRepository
import com.example.data.worker.WorkScheduler
import com.example.domain.usecase.AppVersionComparator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val settingsRepository: SettingsRepository,
    private val workScheduler: WorkScheduler
) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.observeSettings()
        .map { it.notificationsEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private var hasCheckedStartup = false

    fun checkOnStartup() {
        if (hasCheckedStartup) return
        hasCheckedStartup = true
        viewModelScope.launch {
            val status = appUpdateRepository.checkForUpdates(force = false)
            if (status is AppUpdateStatus.UpdateAvailable) {
                _availableUpdate.value = status.info
            }
        }
    }

    fun onNotificationPermissionGranted() {
        workScheduler.scheduleDailyExpiryCheck()
        workScheduler.triggerImmediateExpiryCheck(isManual = false)
    }

    fun remindLater() {
        _availableUpdate.value = null
    }

    fun ignoreVersion(tagName: String) {
        _availableUpdate.value = null
        viewModelScope.launch {
            appUpdateRepository.dismissUpdateTag(tagName)
        }
    }
}

fun openExternalUpdateUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
fun AppUpdateAvailableDialog(
    updateInfo: AppUpdateInfo,
    onDownloadClick: () -> Unit,
    onRemindLater: () -> Unit,
    onIgnoreVersion: () -> Unit
) {
    val sizeText = updateInfo.apkSizeBytes?.let { AppVersionComparator.formatBytes(it) }.orEmpty()

    AlertDialog(
        onDismissRequest = onRemindLater,
        icon = {
            Icon(
                imageVector = Icons.Default.NewReleases,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Nuovo aggiornamento disponibile",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Versione ${updateInfo.currentVersion} → ${updateInfo.latestVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (!updateInfo.apkFileName.isNullOrBlank()) {
                            val assetDetails = if (sizeText.isNotBlank()) {
                                "${updateInfo.apkFileName} ($sizeText)"
                            } else {
                                updateInfo.apkFileName
                            }
                            Text(
                                text = assetDetails,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (updateInfo.releaseNotes.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Novità della versione:",
                            style = MaterialTheme.typography.labelMedium,
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
        },
        confirmButton = {
            Button(
                onClick = onDownloadClick,
                modifier = Modifier.testTag("update_dialog_download_button")
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scarica da GitHub")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onIgnoreVersion,
                    modifier = Modifier.testTag("update_dialog_ignore_button")
                ) {
                    Text("Ignora")
                }
                TextButton(
                    onClick = onRemindLater,
                    modifier = Modifier.testTag("update_dialog_later_button")
                ) {
                    Text("Più tardi")
                }
            }
        }
    )
}
