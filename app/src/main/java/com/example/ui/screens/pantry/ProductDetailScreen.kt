package com.example.ui.screens.pantry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.entity.StorageLocationEntity
import com.example.data.local.relation.MovementWithProduct
import com.example.domain.model.ExpiryType
import com.example.domain.usecase.PantryDateUtils
import com.example.domain.usecase.UnitConverter
import com.example.ui.components.ConfirmDangerDialog
import com.example.ui.components.ExpiryBadge
import com.example.ui.components.LocationSelectorRow
import com.example.ui.components.PantryDatePickerModal
import com.example.ui.components.ProductThumbnail
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    onEditProduct: (String) -> Unit,
    viewModel: PantryViewModel = hiltViewModel()
) {
    val productFlow = remember(productId) { viewModel.observeProductDetail(productId) }
    val movementsFlow = remember(productId) { viewModel.observeProductMovements(productId) }
    val productDetails by productFlow.collectAsStateWithLifecycle()
    val movements by movementsFlow.collectAsStateWithLifecycle()
    val pantryState by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    var showDeleteProductDialog by remember { mutableStateOf(false) }
    var showAddBatchDialog by remember { mutableStateOf(false) }
    var showFefoConsumeDialog by remember { mutableStateOf(false) }
    var batchToConsume by remember { mutableStateOf<Pair<ProductBatchEntity, Boolean>?>(null) } // Boolean = isWaste
    var batchToAdjust by remember { mutableStateOf<ProductBatchEntity?>(null) }
    var batchToTransfer by remember { mutableStateOf<ProductBatchEntity?>(null) }
    var batchToEditDates by remember { mutableStateOf<ProductBatchEntity?>(null) }
    var batchToCancel by remember { mutableStateOf<ProductBatchEntity?>(null) }
    var showExhaustedHistory by remember { mutableStateOf(false) }

    val details = productDetails
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = details?.product?.name ?: "Dettaglio prodotto",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("product_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (details != null) {
                        IconButton(
                            onClick = { onEditProduct(details.product.id) },
                            modifier = Modifier.testTag("product_edit_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifica prodotto")
                        }
                        IconButton(
                            onClick = { showDeleteProductDialog = true },
                            modifier = Modifier.testTag("product_delete_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina prodotto")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (details != null) {
                FloatingActionButton(
                    onClick = { showAddBatchDialog = true },
                    modifier = Modifier.testTag("add_batch_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Aggiungi lotto")
                }
            }
        }
    ) { innerPadding ->
        if (details == null) {
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

        val activeBatches = details.activeBatches
        val exhaustedBatches = details.batches.filter { it.isExhausted || it.remainingQuantity <= 0.0 }
        val locationsMap = remember(pantryState.locations) {
            pantryState.locations.associateBy { it.id }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Product Header Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProductThumbnail(
                                imagePath = details.primaryImagePath,
                                categoryIcon = details.category?.iconName ?: "category",
                                categoryColorHex = details.category?.colorHex,
                                productName = details.product.name,
                                size = 72.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = details.product.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (details.product.brand.isNotBlank()) {
                                    Text(
                                        text = details.product.brand,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${details.category?.name ?: "Altro"} • ${details.defaultLocation?.name ?: "Dispensa"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                details.product.barcode?.let { code ->
                                    Text(
                                        text = "Barcode: $code",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (details.product.description.isNotBlank() || details.product.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            if (details.product.description.isNotBlank()) {
                                Text(
                                    text = details.product.description,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (details.product.notes.isNotBlank()) {
                                Text(
                                    text = "Note: ${details.product.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "Disponibilità totale",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = UnitConverter.formatQuantity(
                                        details.totalAvailableQuantity,
                                        details.product.unit
                                    ),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (details.product.minStockQuantity > 0.0) {
                                    Text(
                                        text = "Scorta min: ${UnitConverter.formatQuantity(details.product.minStockQuantity, details.product.unit)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showAddBatchDialog = true },
                                    modifier = Modifier.testTag("detail_add_batch_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lotto")
                                }
                                Button(
                                    onClick = { showFefoConsumeDialog = true },
                                    enabled = details.totalAvailableQuantity > 0.0,
                                    modifier = Modifier.testTag("detail_consume_fefo_button")
                                ) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Consuma FEFO")
                                }
                            }
                        }
                    }
                }
            }

            // Active Batches Section
            item {
                Text(
                    text = "Lotti attivi (${activeBatches.size}) • ordine FEFO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (activeBatches.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Nessun lotto attivo disponibile. Tocca + per caricare un nuovo lotto.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(activeBatches, key = { it.id }) { batch ->
                    BatchCard(
                        batch = batch,
                        locationName = locationsMap[batch.locationId]?.name ?: "Dispensa",
                        onConsume = { batchToConsume = batch to false },
                        onWaste = { batchToConsume = batch to true },
                        onAdjust = { batchToAdjust = batch },
                        onTransfer = { batchToTransfer = batch },
                        onEditDates = { batchToEditDates = batch },
                        onCancel = { batchToCancel = batch }
                    )
                }
            }

            // Exhausted batches history (preserved, never deleted)
            if (exhaustedBatches.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Lotti esauriti / annullati (${exhaustedBatches.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showExhaustedHistory = !showExhaustedHistory }) {
                            Text(if (showExhaustedHistory) "Nascondi" else "Mostra storico")
                        }
                    }
                }
                if (showExhaustedHistory) {
                    items(exhaustedBatches, key = { it.id }) { batch ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Iniziale: ${UnitConverter.formatQuantity(batch.initialQuantity, batch.unit)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Esaurito",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Acquisto: ${PantryDateUtils.formatDate(batch.purchaseDate)} • Scad.: ${PantryDateUtils.formatDate(batch.expiryDate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Product Movements
            item {
                Text(
                    text = "Movimenti recenti (${movements.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (movements.isEmpty()) {
                item {
                    Text(
                        text = "Nessun movimento registrato per questo prodotto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(movements.take(15), key = { it.movement.id }) { mv ->
                    MovementRowCard(mv)
                }
            }
        }
    }

    if (showDeleteProductDialog && details != null) {
        ConfirmDangerDialog(
            title = "Elimina prodotto",
            message = "Vuoi eliminare \"${details.product.name}\" e tutti i suoi lotti associati?",
            confirmLabel = "Elimina",
            onConfirm = { viewModel.deleteProduct(details.product.id, onBack) },
            onDismiss = { showDeleteProductDialog = false }
        )
    }

    if (showAddBatchDialog && details != null) {
        AddBatchDialog(
            defaultUnit = details.product.unit,
            defaultLocationId = details.product.defaultLocationId,
            locations = pantryState.locations,
            onConfirm = { qty, expiry, expiryType, locId, price, notes, isPurchase ->
                viewModel.addBatch(
                    productId = details.product.id,
                    quantity = qty,
                    unit = details.product.unit,
                    expiryDate = expiry,
                    expiryType = expiryType,
                    locationId = locId,
                    purchasePrice = price,
                    notes = notes,
                    isPurchase = isPurchase
                )
                showAddBatchDialog = false
            },
            onDismiss = { showAddBatchDialog = false }
        )
    }

    if (showFefoConsumeDialog && details != null) {
        QuantityInputDialog(
            title = "Consuma con metodo FEFO",
            subtitle = "Disponibile: ${UnitConverter.formatQuantity(details.totalAvailableQuantity, details.product.unit)}. Verrà scalato prima il lotto con scadenza più vicina.",
            initialValue = minOf(1.0, details.totalAvailableQuantity),
            unitSymbol = details.product.unit.symbol,
            confirmLabel = "Consuma",
            onConfirm = { qty ->
                viewModel.consumeProductFefo(details.product.id, qty)
                showFefoConsumeDialog = false
            },
            onDismiss = { showFefoConsumeDialog = false }
        )
    }

    batchToConsume?.let { (batch, isWaste) ->
        QuantityInputDialog(
            title = if (isWaste) "Registra spreco" else "Consuma da lotto",
            subtitle = "Residuo lotto: ${UnitConverter.formatQuantity(batch.remainingQuantity, batch.unit)}",
            initialValue = minOf(1.0, batch.remainingQuantity),
            unitSymbol = batch.unit.symbol,
            confirmLabel = if (isWaste) "Registra spreco" else "Consuma",
            onConfirm = { qty ->
                viewModel.consumeOrWasteBatch(batch, qty, isWaste)
                batchToConsume = null
            },
            onDismiss = { batchToConsume = null }
        )
    }

    batchToAdjust?.let { batch ->
        QuantityInputDialog(
            title = "Rettifica quantità lotto",
            subtitle = "Quantità attuale: ${UnitConverter.formatQuantity(batch.remainingQuantity, batch.unit)}",
            initialValue = batch.remainingQuantity,
            unitSymbol = batch.unit.symbol,
            confirmLabel = "Salva rettifica",
            allowZero = true,
            onConfirm = { qty ->
                viewModel.adjustBatch(batch.id, qty)
                batchToAdjust = null
            },
            onDismiss = { batchToAdjust = null }
        )
    }

    batchToTransfer?.let { batch ->
        TransferBatchDialog(
            locations = pantryState.locations,
            currentLocationId = batch.locationId,
            onConfirm = { newLocId ->
                viewModel.transferBatch(batch.id, newLocId)
                batchToTransfer = null
            },
            onDismiss = { batchToTransfer = null }
        )
    }

    batchToEditDates?.let { batch ->
        EditBatchDatesDialog(
            batch = batch,
            onConfirm = { expiry, type, price, notes ->
                viewModel.updateBatchDates(batch.id, expiry, type, price, notes)
                batchToEditDates = null
            },
            onDismiss = { batchToEditDates = null }
        )
    }

    batchToCancel?.let { batch ->
        ConfirmDangerDialog(
            title = "Annulla lotto",
            message = "Il lotto verrà azzerato e spostato nello storico dei lotti esauriti senza perdere la tracciabilità.",
            confirmLabel = "Annulla lotto",
            onConfirm = { viewModel.cancelBatch(batch.id) },
            onDismiss = { batchToCancel = null }
        )
    }
}

@Composable
private fun BatchCard(
    batch: ProductBatchEntity,
    locationName: String,
    onConsume: () -> Unit,
    onWaste: () -> Unit,
    onAdjust: () -> Unit,
    onTransfer: () -> Unit,
    onEditDates: () -> Unit,
    onCancel: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("batch_card_${batch.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = UnitConverter.formatQuantity(batch.remainingQuantity, batch.unit),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "/ ${UnitConverter.formatQuantity(batch.initialQuantity, batch.unit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$locationName • Acquistato il ${PantryDateUtils.formatDate(batch.purchaseDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    batch.purchasePrice?.let { price ->
                        Text(
                            text = String.format(Locale.ITALY, "Prezzo: € %.2f", price),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Azioni lotto")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Consuma") },
                            onClick = { menuExpanded = false; onConsume() }
                        )
                        DropdownMenuItem(
                            text = { Text("Registra spreco") },
                            onClick = { menuExpanded = false; onWaste() }
                        )
                        DropdownMenuItem(
                            text = { Text("Rettifica quantità") },
                            onClick = { menuExpanded = false; onAdjust() }
                        )
                        DropdownMenuItem(
                            text = { Text("Trasferisci posizione") },
                            onClick = { menuExpanded = false; onTransfer() }
                        )
                        DropdownMenuItem(
                            text = { Text("Modifica scadenza / note") },
                            onClick = { menuExpanded = false; onEditDates() }
                        )
                        DropdownMenuItem(
                            text = { Text("Annulla lotto") },
                            onClick = { menuExpanded = false; onCancel() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpiryBadge(
                        expiryDateMillis = batch.expiryDate,
                        expiryType = batch.expiryType
                    )
                    if (batch.expiryDate != null) {
                        Text(
                            text = batch.expiryType.shortLabel + " " + PantryDateUtils.formatDate(batch.expiryDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onConsume) {
                    Text("Consuma")
                }
            }
            if (batch.notes.isNotBlank()) {
                Text(
                    text = batch.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MovementRowCard(item: MovementWithProduct) {
    val m = item.movement
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product?.name ?: m.type.labelIt,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${m.type.labelIt} • ${PantryDateUtils.formatDateTime(m.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (m.description.isNotBlank()) {
                    Text(
                        text = m.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            val sign = if (m.type.isInbound) "+" else "-"
            Text(
                text = "$sign${UnitConverter.formatQuantity(m.quantity, m.unit)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (m.type.isInbound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun AddBatchDialog(
    defaultUnit: com.example.domain.model.UnitOfMeasure,
    defaultLocationId: String,
    locations: List<StorageLocationEntity>,
    onConfirm: (Double, Long?, ExpiryType, String, Double?, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var qtyText by remember { mutableStateOf("1") }
    var priceText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var locationId by remember { mutableStateOf(defaultLocationId) }
    var expiryDate by remember { mutableStateOf<Long?>(null) }
    var expiryType by remember { mutableStateOf(ExpiryType.USE_BY) }
    var isPurchase by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo lotto (${defaultUnit.symbol})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Quantità (${defaultUnit.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Prezzo acquisto € (opzionale)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = expiryDate?.let { "Scadenza: ${PantryDateUtils.formatDate(it)}" }
                            ?: "Imposta data di scadenza"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpiryType.entries.forEach { t ->
                        FilterChip(
                            selected = expiryType == t,
                            onClick = { expiryType = t },
                            label = { Text(if (t == ExpiryType.USE_BY) "Scadenza" else "TMC") }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Registra come acquisto", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isPurchase, onCheckedChange = { isPurchase = it })
                }
                LocationSelectorRow(
                    locations = locations,
                    selectedLocationId = locationId,
                    onLocationSelected = { locationId = it }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Note lotto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyText.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (qty > 0.0) {
                        val price = priceText.replace(",", ".").toDoubleOrNull()
                        onConfirm(qty, expiryDate, expiryType, locationId, price, notes, isPurchase)
                    }
                }
            ) {
                Text("Aggiungi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )

    if (showDatePicker) {
        PantryDatePickerModal(
            initialDateMillis = expiryDate,
            onDateSelected = { expiryDate = it },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun QuantityInputDialog(
    title: String,
    subtitle: String,
    initialValue: Double,
    unitSymbol: String,
    confirmLabel: String,
    allowZero: Boolean = false,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember {
        mutableStateOf(
            if (initialValue % 1.0 == 0.0) initialValue.toInt().toString()
            else initialValue.toString()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Quantità ($unitSymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = text.replace(",", ".").toDoubleOrNull()
                if (parsed != null && (if (allowZero) parsed >= 0.0 else parsed > 0.0)) {
                    onConfirm(parsed)
                }
            }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
private fun TransferBatchDialog(
    locations: List<StorageLocationEntity>,
    currentLocationId: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember { mutableStateOf(currentLocationId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trasferisci posizione") },
        text = {
            LocationSelectorRow(
                locations = locations,
                selectedLocationId = selectedId,
                onLocationSelected = { selectedId = it }
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedId) }) { Text("Trasferisci") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
private fun EditBatchDatesDialog(
    batch: ProductBatchEntity,
    onConfirm: (Long?, ExpiryType, Double?, String) -> Unit,
    onDismiss: () -> Unit
) {
    var expiryDate by remember { mutableStateOf(batch.expiryDate) }
    var expiryType by remember { mutableStateOf(batch.expiryType) }
    var priceText by remember { mutableStateOf(batch.purchasePrice?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(batch.notes) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica lotto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(expiryDate?.let { PantryDateUtils.formatDate(it) } ?: "Nessuna scadenza")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpiryType.entries.forEach { t ->
                        FilterChip(
                            selected = expiryType == t,
                            onClick = { expiryType = t },
                            label = { Text(if (t == ExpiryType.USE_BY) "Scadenza" else "TMC") }
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(expiryType.infoIt, style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Prezzo €") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val price = priceText.replace(",", ".").toDoubleOrNull()
                onConfirm(expiryDate, expiryType, price, notes)
            }) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )

    if (showPicker) {
        PantryDatePickerModal(
            initialDateMillis = expiryDate,
            onDateSelected = { expiryDate = it },
            onDismiss = { showPicker = false }
        )
    }
}
