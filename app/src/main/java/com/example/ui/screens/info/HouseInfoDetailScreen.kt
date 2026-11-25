package com.example.ui.screens.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.HouseInfoPhotoEntity
import com.example.domain.model.HouseInfoCategory
import com.example.domain.usecase.HouseInfoFormatter
import com.example.ui.components.ConfirmDangerDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseInfoDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: HouseInfoViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val detailFlow = remember(entryId) { viewModel.observeDetail(entryId) }
    val item by detailFlow.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var revealSecret by remember(entryId) { mutableStateOf(false) }
    var zoomedPhoto by remember { mutableStateOf<HouseInfoPhotoEntity?>(null) }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Dettaglio scheda") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    item?.let { current ->
                        IconButton(
                            onClick = { viewModel.togglePinned(current.entry.id, current.entry.isPinned) },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("info_detail_pin_button")
                        ) {
                            Icon(
                                imageVector = if (current.entry.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (current.entry.isPinned) "Rimuovi dalla Home" else "Fissa nella Home",
                                tint = if (current.entry.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { onEdit(current.entry.id) },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("info_detail_edit_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifica scheda")
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("info_detail_delete_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Elimina scheda")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val current = item
        if (current == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Text(
                    text = "Scheda non trovata",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val entry = current.entry
        val photos = current.sortedPhotos

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxSize()
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            HouseInfoCategoryBadge(category = entry.category)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (entry.subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            when (entry.category) {
                                HouseInfoCategory.PHONE_NUMBER -> {
                                    DetailValueBlock(
                                        label = "Numero principale",
                                        value = entry.primaryValue
                                    )
                                    if (entry.secondaryValue.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        DetailValueBlock(
                                            label = "Contatto secondario",
                                            value = entry.secondaryValue
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { launchPhoneDialer(context, entry.primaryValue) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("info_detail_call_button")
                                        ) {
                                            Icon(Icons.Default.Phone, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Chiama ora")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(entry.primaryValue))
                                                viewModel.showMessage("Numero copiato negli appunti")
                                            },
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copia")
                                        }
                                    }
                                }

                                HouseInfoCategory.MEASUREMENT -> {
                                    val dims = HouseInfoFormatter.formatDimensions(entry)
                                    if (dims.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    text = "Dimensioni (${entry.measurementUnit.labelIt})",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = dims,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                    if (entry.primaryValue.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        DetailValueBlock(
                                            label = "Riferimento / Modello",
                                            value = entry.primaryValue
                                        )
                                    }
                                    if (dims.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString("${entry.title}: $dims"))
                                                viewModel.showMessage("Misure copiate negli appunti")
                                            },
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Copia misure")
                                        }
                                    }
                                }

                                HouseInfoCategory.PHOTO_DETAIL -> {
                                    if (entry.primaryValue.isNotBlank()) {
                                        DetailValueBlock(
                                            label = "Posizione / Dettaglio",
                                            value = entry.primaryValue
                                        )
                                    }
                                }

                                HouseInfoCategory.PASSWORD_NOTE -> {
                                    if (entry.primaryValue.isNotBlank()) {
                                        DetailValueBlock(
                                            label = "Rete / Utente / Dispositivo",
                                            value = entry.primaryValue
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                    if (entry.secretValue.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    text = "Codice / Password",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = if (revealSecret) {
                                                        entry.secretValue
                                                    } else {
                                                        HouseInfoFormatter.maskSecret(entry.secretValue)
                                                    },
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.testTag("info_detail_secret_text")
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    OutlinedButton(
                                                        onClick = { revealSecret = !revealSecret },
                                                        modifier = Modifier
                                                            .defaultMinSize(minHeight = 48.dp)
                                                            .testTag("info_detail_reveal_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = if (revealSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                            contentDescription = null
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(if (revealSecret) "Nascondi" else "Mostra")
                                                    }
                                                    Button(
                                                        onClick = {
                                                            clipboard.setText(AnnotatedString(entry.secretValue))
                                                            viewModel.showMessage("Codice copiato negli appunti")
                                                        },
                                                        modifier = Modifier
                                                            .defaultMinSize(minHeight = 48.dp)
                                                            .testTag("info_detail_copy_secret_button")
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Copia codice")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (entry.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                DetailValueBlock(
                                    label = "Note",
                                    value = entry.notes
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = {
                                    val shareText = HouseInfoFormatter.buildShareText(
                                        entry = entry,
                                        includeSecret = false
                                    )
                                    shareHouseInfoText(context, entry.title, shareText)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("info_detail_share_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Condividi scheda")
                            }
                        }
                    }
                }

                if (photos.isNotEmpty()) {
                    item {
                        Text(
                            text = "Foto allegate (${photos.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(photos, key = { it.id }) { photo ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    modifier = Modifier
                                        .width(200.dp)
                                        .clickable { zoomedPhoto = photo }
                                ) {
                                    Column {
                                        if (File(photo.localFilePath).exists()) {
                                            AsyncImage(
                                                model = File(photo.localFilePath),
                                                contentDescription = photo.caption.ifBlank { entry.title },
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(150.dp)
                                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                            )
                                        }
                                        if (photo.caption.isNotBlank()) {
                                            Text(
                                                text = photo.caption,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDangerDialog(
            title = "Elimina scheda",
            message = "Vuoi eliminare definitivamente questa scheda e le eventuali foto allegate?",
            confirmLabel = "Elimina",
            onConfirm = { viewModel.deleteEntry(entryId, onDeleted = onBack) },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    zoomedPhoto?.let { photo ->
        FullscreenPhotoViewerDialog(
            photo = photo,
            fallbackTitle = item?.entry?.title.orEmpty(),
            onDismiss = { zoomedPhoto = null }
        )
    }
}

@Composable
private fun DetailValueBlock(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun FullscreenPhotoViewerDialog(
    photo: HouseInfoPhotoEntity,
    fallbackTitle: String,
    onDismiss: () -> Unit
) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(photo.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(photo.id) { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = File(photo.localFilePath),
                contentDescription = photo.caption.ifBlank { fallbackTitle },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(photo.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Chiudi immagine",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (photo.caption.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(
                        text = photo.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
