package com.example.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.preferences.UserPreferencesDataStore
import com.example.data.repository.AppSigningFormatter
import com.example.data.repository.AppUpdateStatus
import com.example.data.repository.InAppUpdateDownloadState
import com.example.data.worker.ExpiryNotificationWorker
import com.example.domain.model.ThemePreference
import com.example.domain.usecase.AppVersionComparator
import com.example.ui.components.PantryFlowRow
import com.example.ui.components.UnitDropdownField
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var repoDraft by remember(state.effectiveGithubRepo) { mutableStateOf(state.effectiveGithubRepo) }
    var homeTitleDraft by remember(state.homeTitle) { mutableStateOf(state.homeTitle) }
    var hasNotificationPermission by remember {
        mutableStateOf(ExpiryNotificationWorker.canPostNotifications(context))
    }
    var pendingManualExpiryCheck by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = ExpiryNotificationWorker.canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = ExpiryNotificationWorker.canPostNotifications(context)
        val shouldRunManualCheck = pendingManualExpiryCheck
        pendingManualExpiryCheck = false
        if (granted && hasNotificationPermission) {
            val s = state.settings
            viewModel.updateNotificationSettings(true, s.notify7Days, s.notify3Days, s.notify1Day, s.notify0Days)
            if (shouldRunManualCheck) {
                viewModel.triggerExpiryCheckNow()
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Permesso notifiche negato: attivalo dalle impostazioni di sistema.")
            }
        }
    }

    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(viewModel::exportFullBackupZip) }

    val openZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importFullBackupZip) }

    val createJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportJson) }

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importJson) }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (viewModel.canRequestPackageInstalls()) {
            viewModel.installDownloadedUpdate()
        }
    }

    val saveUpdateApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri -> uri?.let(viewModel::exportPendingUpdateApk) }

    val s = state.settings

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Theme & Home Title Section
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Aspetto e Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    PantryFlowRow(horizontalSpacing = 8.dp, verticalSpacing = 8.dp) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = s.themeMode == pref,
                                onClick = { viewModel.setTheme(pref) },
                                label = { Text(pref.labelIt) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = homeTitleDraft,
                            onValueChange = { updated ->
                                if (updated.length <= UserPreferencesDataStore.MAX_HOME_TITLE_LENGTH) {
                                    homeTitleDraft = updated
                                }
                            },
                            label = { Text(stringResource(R.string.settings_home_title_label)) },
                            placeholder = { Text(stringResource(R.string.settings_home_title_placeholder)) },
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                            trailingIcon = {
                                if (homeTitleDraft.isNotEmpty() && homeTitleDraft != UserPreferencesDataStore.DEFAULT_HOME_TITLE) {
                                    IconButton(
                                        onClick = { homeTitleDraft = UserPreferencesDataStore.DEFAULT_HOME_TITLE },
                                        modifier = Modifier.testTag("clear_home_title_button")
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Ripristina nel campo")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_title_input")
                        )
                        OutlinedButton(
                            onClick = { viewModel.saveHomeTitle(homeTitleDraft) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("save_home_title_button")
                        ) {
                            Text(
                                text = stringResource(R.string.settings_home_title_save),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_home_title_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.homeTitle != UserPreferencesDataStore.DEFAULT_HOME_TITLE) {
                        TextButton(
                            onClick = viewModel::resetHomeTitle,
                            modifier = Modifier.testTag("reset_home_title_button")
                        ) {
                            Text(stringResource(R.string.settings_home_title_reset))
                        }
                    }
                }
            }

            // Notifications & Thresholds
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notifiche scadenze", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Avvisi locali pianificati con WorkManager",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = s.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotificationSettings(
                                    enabled, s.notify7Days, s.notify3Days, s.notify1Day, s.notify0Days
                                )
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        pendingManualExpiryCheck = false
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }
                        )
                    }

                    if (s.notificationsEnabled && !hasNotificationPermission) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.NotificationsOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Le notifiche risultano bloccate dal sistema Android.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                PantryFlowRow(horizontalSpacing = 8.dp, verticalSpacing = 6.dp) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                pendingManualExpiryCheck = false
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            },
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("grant_notifications_button")
                                        ) {
                                            Text("Concedi permesso", maxLines = 1, softWrap = false)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { openAppNotificationSettings(context) },
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("open_notification_settings_button")
                                    ) {
                                        Text("Impostazioni Android", maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }

                    if (s.notificationsEnabled) {
                        Text("Soglie di avviso", style = MaterialTheme.typography.labelLarge)
                        PantryFlowRow(
                            horizontalSpacing = 8.dp,
                            verticalSpacing = 6.dp
                        ) {
                            FilterChip(
                                selected = s.notify7Days,
                                onClick = {
                                    viewModel.updateNotificationSettings(true, !s.notify7Days, s.notify3Days, s.notify1Day, s.notify0Days)
                                },
                                label = { Text("7 giorni prima") }
                            )
                            FilterChip(
                                selected = s.notify3Days,
                                onClick = {
                                    viewModel.updateNotificationSettings(true, s.notify7Days, !s.notify3Days, s.notify1Day, s.notify0Days)
                                },
                                label = { Text("3 giorni prima") }
                            )
                            FilterChip(
                                selected = s.notify1Day,
                                onClick = {
                                    viewModel.updateNotificationSettings(true, s.notify7Days, s.notify3Days, !s.notify1Day, s.notify0Days)
                                },
                                label = { Text("1 giorno prima") }
                            )
                            FilterChip(
                                selected = s.notify0Days,
                                onClick = {
                                    viewModel.updateNotificationSettings(true, s.notify7Days, s.notify3Days, s.notify1Day, !s.notify0Days)
                                },
                                label = { Text("Giorno stesso") }
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    pendingManualExpiryCheck = true
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else if (!ExpiryNotificationWorker.canPostNotifications(context)) {
                                    openAppNotificationSettings(context)
                                } else {
                                    viewModel.triggerExpiryCheckNow()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trigger_expiry_check_button")
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifica scadenze ora")
                        }
                    }
                }
            }

            // Defaults & Categories
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Preferenze inventario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    UnitDropdownField(
                        selectedUnit = s.defaultUnit,
                        onUnitSelected = viewModel::setDefaultUnit,
                        label = "Unità di misura predefinita"
                    )
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenCategories)
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gestione categorie", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${state.categories.size} categorie configurate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            // Backup & JSON Export/Import
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Backup e Dati Offline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Include prodotti, lotti, movimenti, liste della spesa, schede casa e immagini locali.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { createZipLauncher.launch("pantrymaster_backup.zip") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup completo (.zip)")
                    }
                    OutlinedButton(
                        onClick = { openZipLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ripristina da backup (.zip)")
                    }
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { createJsonLauncher.launch("pantrymaster_export.json") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Esporta JSON",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { openJsonLauncher.launch(arrayOf("application/json")) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Importa JSON",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Auto-Update & App Info
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Aggiornamenti e Versione",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "PantryMaster v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = state.autoUpdateEnabled,
                            onCheckedChange = viewModel::setAutoUpdateEnabled,
                            modifier = Modifier.testTag("auto_update_switch")
                        )
                    }

                    Text(
                        text = "Controllo automatico GitHub Releases all'avvio e in background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Build: ${state.signingDetails.buildTypeLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Firma SHA-256: ${state.signingDetails.shortFingerprint}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.signingDetails.sha256ColonHex))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Impronta SHA-256 copiata.")
                                    }
                                },
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("copy_signing_sha256_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copia impronta SHA-256"
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = repoDraft,
                            onValueChange = { repoDraft = it },
                            label = { Text("Repository GitHub (utente/repo)") },
                            placeholder = { Text("es. utente/PantryMaster") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("github_repo_input")
                        )
                        OutlinedButton(
                            onClick = { viewModel.saveGithubRepository(repoDraft) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("save_github_repo_button")
                        ) {
                            Text("Salva", maxLines = 1, softWrap = false)
                        }
                    }

                    OutlinedButton(
                        onClick = viewModel::checkForUpdatesNow,
                        enabled = state.updateStatus !is AppUpdateStatus.Checking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("check_updates_button")
                    ) {
                        if (state.updateStatus is AppUpdateStatus.Checking) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifica in corso...")
                        } else {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifica aggiornamenti ora")
                        }
                    }

                    when (val status = state.updateStatus) {
                        is AppUpdateStatus.UpdateAvailable -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "Nuova versione v${status.info.latestVersion} disponibile",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (status.info.releaseNotes.isNotBlank()) {
                                        Text(
                                            text = status.info.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 4
                                        )
                                    }

                                    when (val dlState = state.downloadState) {
                                        is InAppUpdateDownloadState.Downloading -> {
                                            if (dlState.progress != null) {
                                                LinearProgressIndicator(
                                                    progress = { dlState.progress },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else {
                                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            }
                                            val pctText = dlState.progress?.let { "${(it * 100).toInt()}% • " }.orEmpty()
                                            Text(
                                                text = "${pctText}${AppVersionComparator.formatBytes(dlState.downloadedBytes)} scaricati",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        is InAppUpdateDownloadState.ReadyToInstall -> {
                                            Button(
                                                onClick = {
                                                    if (viewModel.canRequestPackageInstalls()) {
                                                        viewModel.installDownloadedUpdate()
                                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                        val settingsIntent = Intent(
                                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                            Uri.parse("package:${context.packageName}")
                                                        )
                                                        unknownSourcesLauncher.launch(settingsIntent)
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    if (viewModel.canRequestPackageInstalls()) "Aggiorna ora"
                                                    else "Abilita e installa"
                                                )
                                            }
                                        }

                                        is InAppUpdateDownloadState.Installing -> {
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            Text(
                                                text = "Avvio installazione...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        is InAppUpdateDownloadState.SignatureMismatch -> {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(10.dp)
                                                ) {
                                                    Text(
                                                        text = "Firma digitale diversa: serve una reinstallazione pulita.",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                    Text(
                                                        text = "App: ${if (dlState.installedIsDebug) "Debug" else "Release"} (${AppSigningFormatter.shortFingerprint(dlState.installedFingerprint)})\nNuovo APK: ${if (dlState.archiveIsDebug) "Debug" else "Release"} (${AppSigningFormatter.shortFingerprint(dlState.archiveFingerprint)})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                            }
                                            Button(
                                                onClick = { createZipLauncher.launch("pantrymaster_backup.zip") },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .testTag("settings_mismatch_backup_button")
                                            ) {
                                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("1. Salva backup dati (.zip)")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val fileName = dlState.updateInfo.apkFileName
                                                        ?: "PantryMaster-${dlState.updateInfo.tagName}.apk"
                                                    saveUpdateApkLauncher.launch(fileName)
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .testTag("settings_mismatch_save_apk_button")
                                            ) {
                                                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("2. Salva nuovo APK in Download")
                                            }
                                        }

                                        is InAppUpdateDownloadState.Error -> {
                                            Text(
                                                text = dlState.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Button(
                                                onClick = { viewModel.startInAppUpdateDownload(forceRestart = true) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Riprova download")
                                            }
                                        }

                                        InAppUpdateDownloadState.Idle -> {
                                            Button(
                                                onClick = { viewModel.startInAppUpdateDownload() },
                                                enabled = !status.info.apkDownloadUrl.isNullOrBlank(),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    if (status.info.apkDownloadUrl.isNullOrBlank()) {
                                                        "Pacchetto APK non disponibile"
                                                    } else {
                                                        "Scarica aggiornamento in app"
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        is AppUpdateStatus.UpToDate -> {
                            Text(
                                text = "App aggiornata all'ultima versione disponibile (v${status.currentVersion}).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        is AppUpdateStatus.NotConfigured -> {
                            Text(
                                text = "Inserisci il repository GitHub (utente/repo) o compila tramite GitHub Actions per abilitare gli aggiornamenti.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is AppUpdateStatus.Error -> {
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        AppUpdateStatus.Idle, AppUpdateStatus.Checking -> Unit
                    }

                    if (state.lastUpdateCheckAt > 0L) {
                        val formatted = remember(state.lastUpdateCheckAt) {
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(state.lastUpdateCheckAt))
                        }
                        Text(
                            text = "Ultimo controllo: $formatted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Archivio locale Room offline-first. Le indicazioni tra Data di scadenza e TMC riflettono esclusivamente i dati inseriti dall'utente; verificare sempre l'etichetta fisica del prodotto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
