package com.example.ui.screens.info

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.repository.HouseInfoPhotoDraft
import com.example.domain.model.HouseInfoCategory
import com.example.domain.model.MeasurementUnit
import com.example.ui.components.PantryFlowRow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseInfoEditorScreen(
    entryId: String?,
    initialCategoryName: String? = null,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: HouseInfoViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val existingFlow = remember(entryId) { entryId?.let { viewModel.observeDetail(it) } }
    val existing = existingFlow?.collectAsStateWithLifecycle()?.value

    var initialized by remember(entryId) { mutableStateOf(entryId == null) }
    var category by remember(entryId, initialCategoryName) {
        mutableStateOf(
            initialCategoryName?.let { runCatching { HouseInfoCategory.valueOf(it) }.getOrNull() }
                ?: HouseInfoCategory.PHONE_NUMBER
        )
    }
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var primaryValue by remember { mutableStateOf("") }
    var secondaryValue by remember { mutableStateOf("") }
    var secretValue by remember { mutableStateOf("") }
    var showSecretInput by remember { mutableStateOf(false) }
    var widthValue by remember { mutableStateOf("") }
    var heightValue by remember { mutableStateOf("") }
    var depthValue by remember { mutableStateOf("") }
    var measurementUnit by remember { mutableStateOf(MeasurementUnit.CM) }
    var notes by remember { mutableStateOf("") }
    var isPinned by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<HouseInfoPhotoDraft>>(emptyList()) }

    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(existing) {
        if (entryId != null && existing != null && !initialized) {
            val e = existing.entry
            category = e.category
            title = e.title
            subtitle = e.subtitle
            primaryValue = e.primaryValue
            secondaryValue = e.secondaryValue
            secretValue = e.secretValue
            widthValue = e.widthValue
            heightValue = e.heightValue
            depthValue = e.depthValue
            measurementUnit = e.measurementUnit
            notes = e.notes
            isPinned = e.isPinned
            photos = existing.sortedPhotos.map { p ->
                HouseInfoPhotoDraft(
                    id = p.id,
                    localFilePath = p.localFilePath,
                    caption = p.caption
                )
            }
            initialized = true
        }
    }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = viewModel.copyPickedImage(uri)
                if (savedPath != null) {
                    photos = photos + HouseInfoPhotoDraft(localFilePath = savedPath)
                } else {
                    viewModel.showMessage("Impossibile importare la foto selezionata.")
                }
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        val tempFile = pendingCameraFile
        pendingCameraUri = null
        pendingCameraFile = null
        if (success && capturedUri != null) {
            scope.launch {
                val savedPath = viewModel.copyPickedImage(capturedUri)
                tempFile?.delete()
                if (savedPath != null) {
                    photos = photos + HouseInfoPhotoDraft(localFilePath = savedPath)
                } else {
                    viewModel.showMessage("Impossibile salvare la foto scattata.")
                }
            }
        } else {
            tempFile?.delete()
        }
    }

    fun launchCameraCapture() {
        runCatching {
            val (file, uri) = createCameraTempFileUri(context)
            pendingCameraFile = file
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }.onFailure {
            viewModel.showMessage("Fotocamera non disponibile su questo dispositivo.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            viewModel.showMessage("Permesso fotocamera necessario per scattare foto.")
        }
    }

    fun submit() {
        viewModel.saveEntry(
            entryId = entryId,
            category = category,
            title = title,
            subtitle = subtitle,
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            secretValue = secretValue,
            widthValue = widthValue,
            heightValue = heightValue,
            depthValue = depthValue,
            measurementUnit = measurementUnit,
            notes = notes,
            isPinned = isPinned,
            photos = photos,
            onSaved = onSaved
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (entryId == null) "Nuova scheda casa" else "Modifica scheda") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(
                        enabled = initialized && !isSaving,
                        onClick = ::submit,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("info_editor_save_top_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Salva scheda")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!initialized) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Categoria",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                PantryFlowRow(horizontalSpacing = 8.dp, verticalSpacing = 6.dp) {
                    HouseInfoCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            leadingIcon = {
                                Icon(
                                    imageVector = iconForHouseInfoCategory(cat),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(cat.labelIt) },
                            modifier = Modifier.testTag("info_editor_cat_${cat.name}")
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titolo *") },
                    placeholder = {
                        Text(
                            when (category) {
                                HouseInfoCategory.PHONE_NUMBER -> "Es. Idraulico Mario / Amministratore"
                                HouseInfoCategory.MEASUREMENT -> "Es. Nicchia frigorifero / Finestra sala"
                                HouseInfoCategory.PHOTO_DETAIL -> "Es. Quadro elettrico / Valvola acqua"
                                HouseInfoCategory.PASSWORD_NOTE -> "Es. Wi-Fi casa / Codice cancello"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("info_editor_title_input")
                )

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Sottotitolo / Zona") },
                    placeholder = { Text(category.subtitleHintIt) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )

                when (category) {
                    HouseInfoCategory.PHONE_NUMBER -> {
                        OutlinedTextField(
                            value = primaryValue,
                            onValueChange = { primaryValue = it },
                            label = { Text("Numero di telefono *") },
                            placeholder = { Text("Es. +39 333 1234567") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("info_editor_primary_input")
                        )
                        OutlinedTextField(
                            value = secondaryValue,
                            onValueChange = { secondaryValue = it },
                            label = { Text("Altro numero / Email / Referente") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HouseInfoCategory.MEASUREMENT -> {
                        Text(
                            text = "Dimensioni",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = widthValue,
                                onValueChange = { widthValue = it },
                                label = { Text("Largh.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("info_editor_width_input")
                            )
                            OutlinedTextField(
                                value = heightValue,
                                onValueChange = { heightValue = it },
                                label = { Text("Alt.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("info_editor_height_input")
                            )
                            OutlinedTextField(
                                value = depthValue,
                                onValueChange = { depthValue = it },
                                label = { Text("Prof.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("info_editor_depth_input")
                            )
                        }
                        PantryFlowRow(horizontalSpacing = 8.dp, verticalSpacing = 6.dp) {
                            MeasurementUnit.entries.forEach { u ->
                                FilterChip(
                                    selected = measurementUnit == u,
                                    onClick = { measurementUnit = u },
                                    label = { Text(u.labelIt) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = primaryValue,
                            onValueChange = { primaryValue = it },
                            label = { Text("Riferimento o modello") },
                            placeholder = { Text("Es. Infisso PVC bianco, lato terrazzo") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HouseInfoCategory.PHOTO_DETAIL -> {
                        OutlinedTextField(
                            value = primaryValue,
                            onValueChange = { primaryValue = it },
                            label = { Text("Posizione o impianto") },
                            placeholder = { Text("Es. Sottoscala ingresso, nicchia caldaia") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("info_editor_primary_input")
                        )
                    }

                    HouseInfoCategory.PASSWORD_NOTE -> {
                        OutlinedTextField(
                            value = primaryValue,
                            onValueChange = { primaryValue = it },
                            label = { Text("Rete / Utente / Dispositivo") },
                            placeholder = { Text("Es. SSID CasaMare_5G o Centralina") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("info_editor_primary_input")
                        )
                        OutlinedTextField(
                            value = secretValue,
                            onValueChange = { secretValue = it },
                            label = { Text("Codice / Password / PIN") },
                            singleLine = true,
                            visualTransformation = if (showSecretInput) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showSecretInput = !showSecretInput },
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showSecretInput) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showSecretInput) "Nascondi password" else "Mostra password"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("info_editor_secret_input")
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fissa nella Home",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Mostra questa scheda in accesso rapido nella Home.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isPinned,
                            onCheckedChange = { isPinned = it },
                            modifier = Modifier.testTag("info_editor_pinned_switch")
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "Foto dettagli (${photos.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        launchCameraCapture()
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("info_editor_camera_button")
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scatta foto")
                            }
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("info_editor_gallery_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Da galleria")
                            }
                        }

                        photos.forEachIndexed { idx, draft ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AsyncImage(
                                    model = File(draft.localFilePath),
                                    contentDescription = "Foto allegata ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                OutlinedTextField(
                                    value = draft.caption,
                                    onValueChange = { updated ->
                                        photos = photos.mapIndexed { i, item ->
                                            if (i == idx) item.copy(caption = updated) else item
                                        }
                                    },
                                    label = { Text("Didascalia foto ${idx + 1}") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        photos = photos.filterIndexed { i, _ -> i != idx }
                                    },
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Rimuovi foto")
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Note libere") },
                    placeholder = { Text("Dettagli utili per interventi, posizioni o istruzioni...") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("info_editor_notes_input")
                )

                Button(
                    enabled = !isSaving,
                    onClick = ::submit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("info_editor_save_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (entryId == null) "Salva scheda" else "Salva modifiche")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun createCameraTempFileUri(context: Context): Pair<File, Uri> {
    val dir = File(context.cacheDir, "camera_photos").apply { if (!exists()) mkdirs() }
    val file = File(dir, "house_${UUID.randomUUID()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return file to uri
}
