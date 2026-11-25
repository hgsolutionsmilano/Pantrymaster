package com.example.ui.screens.scanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.db.DatabaseSeeder
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.StorageLocationEntity
import com.example.data.repository.BarcodeLookupResult
import com.example.domain.model.ExpiryType
import com.example.domain.usecase.PantryDateUtils
import com.example.domain.usecase.ShelfLifeEstimate
import com.example.ui.components.LocationSelectorRow
import com.example.ui.components.PantryDatePickerModal
import com.example.ui.components.PantryFlowRow
import com.example.ui.components.ProductThumbnail

@Composable
internal fun ExpiryCaptureProductBanner(
    barcode: String,
    lookupState: BarcodeLookupAsyncState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (lookupState) {
                BarcodeLookupAsyncState.Loading -> {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Barcode $barcode",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Ricerca prodotto in corso...",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                is BarcodeLookupAsyncState.Ready -> {
                    val (title, subtitle) = when (val res = lookupState.result) {
                        is BarcodeLookupResult.LocalProduct ->
                            res.product.name to "Già in dispensa • ${res.product.brand.ifBlank { barcode }}"
                        is BarcodeLookupResult.RemoteProduct ->
                            res.name to "Open Food Facts • ${res.brand.ifBlank { barcode }}"
                        is BarcodeLookupResult.NotFound ->
                            "Nuovo prodotto ($barcode)" to "Non trovato in archivio, potrai salvarlo dopo"
                        is BarcodeLookupResult.NetworkError ->
                            "Prodotto ($barcode)" to "Servizio offline, inserimento manuale pronto"
                    }
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExpiryCaptureBottomCard(
    state: ScannerUiState.CapturingExpiry,
    onConfirmDetected: () -> Unit,
    onSelectExpiryType: (ExpiryType) -> Unit,
    onRetryOcr: () -> Unit,
    onOpenManualPicker: () -> Unit,
    onUseHistoricalEstimate: () -> Unit,
    onSkip: () -> Unit,
    onChangeBarcode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shelfLifeEstimate = (state.lookupState as? BarcodeLookupAsyncState.Ready)?.shelfLifeEstimate

    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(16.dp)
        ) {
            when (val ocr = state.ocrStatus) {
                OcrCaptureStatus.Searching -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.pendingConfirmAfterLookup) {
                                    "Completamento ricerca prodotto..."
                                } else {
                                    "Lettura automatica scadenza attiva"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Inquadra la data stampata (es. SCAD 18/11/2026 o TMC 08/26)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (shelfLifeEstimate != null) {
                        HistoricalShelfLifeBanner(
                            estimate = shelfLifeEstimate,
                            onApplyEstimate = onUseHistoricalEstimate
                        )
                    }

                    PantryFlowRow(horizontalSpacing = 8.dp, verticalSpacing = 6.dp) {
                        OutlinedButton(
                            onClick = onOpenManualPicker,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("ocr_manual_date_button")
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scegli data")
                        }
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("ocr_skip_button")
                        ) {
                            Text("Salta senza data")
                        }
                        TextButton(
                            onClick = onChangeBarcode,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Altro barcode")
                        }
                    }
                }

                is OcrCaptureStatus.Detected -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Data di scadenza rilevata",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = PantryDateUtils.formatDate(ocr.expiryDateMillis),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Testo riconosciuto: \"${ocr.matchedText}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            PantryFlowRow(horizontalSpacing = 8.dp, verticalSpacing = 4.dp) {
                                ExpiryType.entries.forEach { type ->
                                    FilterChip(
                                        selected = ocr.expiryType == type,
                                        onClick = { onSelectExpiryType(type) },
                                        label = {
                                            Text(if (type == ExpiryType.USE_BY) "Data di scadenza" else "TMC")
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onConfirmDetected,
                        enabled = !state.pendingConfirmAfterLookup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                            .testTag("confirm_ocr_expiry_button")
                    ) {
                        if (state.pendingConfirmAfterLookup) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Caricamento prodotto...")
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conferma scadenza (${PantryDateUtils.formatDate(ocr.expiryDateMillis)})")
                        }
                    }

                    AnimatedVisibility(
                        visible = shelfLifeEstimate != null && !ocr.fromHistoricalEstimate,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        if (shelfLifeEstimate != null) {
                            HistoricalShelfLifeBanner(
                                estimate = shelfLifeEstimate,
                                onApplyEstimate = onUseHistoricalEstimate
                            )
                        }
                    }

                    PantryFlowRow(horizontalSpacing = 8.dp, verticalSpacing = 4.dp) {
                        OutlinedButton(
                            onClick = onRetryOcr,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("ocr_retry_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rileva di nuovo")
                        }
                        OutlinedButton(
                            onClick = onOpenManualPicker,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Modifica")
                        }
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Salta")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoricalShelfLifeBanner(
    estimate: ShelfLifeEstimate,
    onApplyEstimate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Stima da storico (${estimate.averageDays} giorni medi)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Basata su ${estimate.sampleCount} ${if (estimate.sampleCount == 1) "lotto precedente" else "lotti precedenti"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            OutlinedButton(
                onClick = onApplyEstimate,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("use_historical_estimate_button")
            ) {
                Text("Usa ${PantryDateUtils.formatDate(estimate.estimatedExpiryMillis)}")
            }
        }
    }
}

@Composable
internal fun ScannerResultContent(
    state: ScannerUiState.Result,
    locations: List<StorageLocationEntity>,
    onAddBatchToLocal: (
        ProductEntity,
        Double,
        Long?,
        ExpiryType,
        String,
        Double?
    ) -> Unit,
    onEditRemoteAndSave: (BarcodeLookupResult.RemoteProduct, Long?, ExpiryType?) -> Unit,
    onManualCreate: (String, Long?, ExpiryType?) -> Unit,
    onRescan: () -> Unit
) {
    val lookup = state.lookup
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
        ) {
            when (lookup) {
                is BarcodeLookupResult.LocalProduct -> {
                    val product = lookup.product
                    var qtyText by remember { mutableStateOf("1") }
                    var expiryDate by remember(state.capturedExpiryMillis) {
                        mutableStateOf(state.capturedExpiryMillis)
                    }
                    var expiryType by remember(state.capturedExpiryType) {
                        mutableStateOf(state.capturedExpiryType)
                    }
                    var locationId by remember { mutableStateOf(product.defaultLocationId) }
                    var showPicker by remember { mutableStateOf(false) }

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Prodotto già presente in dispensa",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (product.brand.isNotBlank()) {
                                    Text(product.brand, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Text("Aggiungi nuovo lotto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Quantità (${product.unit.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.expirySourceLabel != null && expiryDate == state.capturedExpiryMillis && expiryDate != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = state.expirySourceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else if (expiryDate == null && state.shelfLifeEstimate != null) {
                        HistoricalShelfLifeBanner(
                            estimate = state.shelfLifeEstimate,
                            onApplyEstimate = {
                                expiryDate = state.shelfLifeEstimate.estimatedExpiryMillis
                                expiryType = state.shelfLifeEstimate.dominantExpiryType
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { showPicker = true },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(expiryDate?.let { "Scadenza: ${PantryDateUtils.formatDate(it)}" } ?: "Imposta data scadenza")
                        }
                        if (expiryDate != null) {
                            IconButton(onClick = { expiryDate = null }) {
                                Icon(Icons.Default.Clear, contentDescription = "Rimuovi data")
                            }
                        }
                    }

                    PantryFlowRow(horizontalSpacing = 8.dp, verticalSpacing = 6.dp) {
                        ExpiryType.entries.forEach { t ->
                            FilterChip(
                                selected = expiryType == t,
                                onClick = { expiryType = t },
                                label = { Text(if (t == ExpiryType.USE_BY) "Scadenza" else "TMC") }
                            )
                        }
                    }
                    LocationSelectorRow(
                        locations = locations,
                        selectedLocationId = locationId.ifBlank { DatabaseSeeder.DEFAULT_LOCATION_PANTRY_ID },
                        onLocationSelected = { locationId = it }
                    )
                    Button(
                        onClick = {
                            val qty = qtyText.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (qty > 0.0) {
                                onAddBatchToLocal(
                                    product,
                                    qty,
                                    expiryDate,
                                    expiryType,
                                    locationId,
                                    null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("scanner_save_local_batch_button")
                    ) {
                        Text("Salva nuovo lotto")
                    }
                    if (showPicker) {
                        PantryDatePickerModal(
                            initialDateMillis = expiryDate,
                            onDateSelected = { expiryDate = it },
                            onDismiss = { showPicker = false }
                        )
                    }
                }

                is BarcodeLookupResult.RemoteProduct -> {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            ProductThumbnail(
                                imagePath = lookup.localImagePath,
                                categoryIcon = "inventory",
                                categoryColorHex = null,
                                productName = lookup.name,
                                size = 64.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Trovato su Open Food Facts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = lookup.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (lookup.brand.isNotBlank()) {
                                    Text(lookup.brand, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = "Barcode: ${lookup.barcode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    CapturedExpirySummaryPill(
                        expiryMillis = state.capturedExpiryMillis,
                        expiryType = state.capturedExpiryType,
                        sourceLabel = state.expirySourceLabel
                    )
                    Button(
                        onClick = {
                            onEditRemoteAndSave(
                                lookup,
                                state.capturedExpiryMillis,
                                state.capturedExpiryType.takeIf { state.capturedExpiryMillis != null }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("scanner_open_remote_editor_button")
                    ) {
                        Text("Verifica dati, aggiungi lotto e salva")
                    }
                }

                is BarcodeLookupResult.NotFound -> {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Prodotto non trovato (${lookup.barcode})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Il codice non è presente in archivio né su Open Food Facts. Puoi inserirlo manualmente mantenendo il barcode.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    CapturedExpirySummaryPill(
                        expiryMillis = state.capturedExpiryMillis,
                        expiryType = state.capturedExpiryType,
                        sourceLabel = state.expirySourceLabel
                    )
                    Button(
                        onClick = {
                            onManualCreate(
                                lookup.barcode,
                                state.capturedExpiryMillis,
                                state.capturedExpiryType.takeIf { state.capturedExpiryMillis != null }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Inserisci prodotto manualmente")
                    }
                }

                is BarcodeLookupResult.NetworkError -> {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Servizio offline o non raggiungibile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(lookup.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    CapturedExpirySummaryPill(
                        expiryMillis = state.capturedExpiryMillis,
                        expiryType = state.capturedExpiryType,
                        sourceLabel = state.expirySourceLabel
                    )
                    Button(
                        onClick = {
                            onManualCreate(
                                lookup.barcode,
                                state.capturedExpiryMillis,
                                state.capturedExpiryType.takeIf { state.capturedExpiryMillis != null }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Inserisci manualmente (${lookup.barcode})")
                    }
                }
            }

            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Scansiona un altro codice")
            }
        }
    }
}

@Composable
private fun CapturedExpirySummaryPill(
    expiryMillis: Long?,
    expiryType: ExpiryType,
    sourceLabel: String?
) {
    if (expiryMillis == null) return
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Scadenza pronta: ${PantryDateUtils.formatDate(expiryMillis)} (${if (expiryType == ExpiryType.USE_BY) "Scadenza" else "TMC"})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (!sourceLabel.isNullOrBlank()) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
