package com.example.ui.screens.shopping

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.db.DatabaseSeeder
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ShoppingItemEntity
import com.example.data.local.entity.StorageLocationEntity
import com.example.domain.model.ExpiryType
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.PantryDateUtils
import com.example.domain.usecase.UnitConverter
import com.example.ui.components.CategoryDropdownField
import com.example.ui.components.ConfirmDangerDialog
import com.example.ui.components.LocationSelectorRow
import com.example.ui.components.PantryDatePickerModal
import com.example.ui.components.PantryEmptyState
import com.example.ui.components.PantryFlowRow
import com.example.ui.components.UnitDropdownField
import com.example.ui.theme.LocalPantryColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    var showCreateListDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var itemToMoveToPantry by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var listToDeleteId by remember { mutableStateOf<String?>(null) }

    val activeList = state.activeList

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lista della spesa",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showCreateListDialog = true },
                        modifier = Modifier.testTag("shopping_new_list_button")
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = "Nuova lista")
                    }
                    if (activeList != null) {
                        IconButton(onClick = { listToDeleteId = activeList.list.id }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Elimina lista")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (activeList != null) {
                FloatingActionButton(
                    onClick = { showAddItemDialog = true },
                    modifier = Modifier.testTag("shopping_add_item_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Aggiungi articolo")
                }
            }
        }
    ) { innerPadding ->
        if (state.isLoading) {
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
                .imePadding()
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            // Multiple lists selector
            if (state.lists.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.lists, key = { it.list.id }) { listWithItems ->
                        val pending = listWithItems.items.count { !it.isPurchased }
                        FilterChip(
                            selected = listWithItems.list.id == activeList?.list?.id,
                            onClick = { viewModel.selectList(listWithItems.list.id) },
                            label = { Text("${listWithItems.list.name} ($pending)") }
                        )
                    }
                }
            }

            if (activeList == null) {
                PantryEmptyState(
                    icon = Icons.Default.ShoppingCart,
                    title = "Nessuna lista della spesa",
                    subtitle = "Crea una lista per organizzare i tuoi acquisti o importare i prodotti sotto scorta.",
                    actionLabel = "Crea prima lista",
                    onAction = { showCreateListDialog = true },
                    modifier = Modifier.weight(1f)
                )
            } else {
                val pendingItems = activeList.items.filter { !it.isPurchased }
                val purchasedItems = activeList.items.filter { it.isPurchased }

                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Low stock auto suggestion banner
                    if (state.lowStockSuggestions.isNotEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = LocalPantryColors.current.warningContainer.copy(alpha = 0.72f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Icon(
                                        Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = LocalPantryColors.current.warning
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${state.lowStockSuggestions.size} prodotti sotto scorta",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Aggiungi automaticamente alla lista attiva.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.addLowStockSuggestions(activeList.list.id) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text("Aggiungi", maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }

                    // Shopping list summary bar
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text("Da acquistare", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = "${pendingItems.size} articoli",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Acquistati", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = "${purchasedItems.size} articoli",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (activeList.items.isEmpty()) {
                        item {
                            PantryEmptyState(
                                icon = Icons.Default.AddShoppingCart,
                                title = "Lista vuota",
                                subtitle = "Tocca + per aggiungere un articolo con quantità e categoria.",
                                actionLabel = "Aggiungi articolo",
                                onAction = { showAddItemDialog = true }
                            )
                        }
                    }

                    items(pendingItems, key = { it.id }) { item ->
                        ShoppingItemCard(
                            item = item,
                            categoryName = state.categories.firstOrNull { it.id == item.categoryId }?.name,
                            onTogglePurchased = { checked -> viewModel.togglePurchased(item, checked) },
                            onMoveToPantry = { itemToMoveToPantry = item },
                            onDelete = { viewModel.deleteItem(item.id) }
                        )
                    }

                    if (purchasedItems.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            ) {
                                Text(
                                    text = "Acquistati (${purchasedItems.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { viewModel.clearPurchased(activeList.list.id) }) {
                                    Text("Rimuovi completati")
                                }
                            }
                        }

                        items(purchasedItems, key = { it.id }) { item ->
                            ShoppingItemCard(
                                item = item,
                                categoryName = state.categories.firstOrNull { it.id == item.categoryId }?.name,
                                onTogglePurchased = { checked -> viewModel.togglePurchased(item, checked) },
                                onMoveToPantry = { itemToMoveToPantry = item },
                                onDelete = { viewModel.deleteItem(item.id) }
                            )
                        }
                    }
                }
            }
        }
        }
    }

    if (showCreateListDialog) {
        var listName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateListDialog = false },
            title = { Text("Nuova lista della spesa") },
            text = {
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    label = { Text("Nome lista (es. Supermercato)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createList(listName)
                    showCreateListDialog = false
                }) {
                    Text("Crea")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateListDialog = false }) { Text("Annulla") }
            }
        )
    }

    if (showAddItemDialog && activeList != null) {
        AddShoppingItemDialog(
            categories = state.categories,
            defaultUnit = state.defaultUnit,
            onConfirm = { name, qty, unit, catId, notes ->
                viewModel.saveItem(
                    existingId = null,
                    listId = activeList.list.id,
                    name = name,
                    quantity = qty,
                    unit = unit,
                    categoryId = catId,
                    estimatedPrice = null,
                    actualPrice = null,
                    notes = notes
                )
                showAddItemDialog = false
            },
            onDismiss = { showAddItemDialog = false }
        )
    }

    itemToMoveToPantry?.let { item ->
        ConfirmAddToPantryDialog(
            item = item,
            locations = state.locations,
            onConfirm = { locId, expiryDate, expiryType ->
                viewModel.confirmMoveToPantry(item.id, locId, expiryDate, expiryType, null)
                itemToMoveToPantry = null
            },
            onDismiss = { itemToMoveToPantry = null }
        )
    }

    listToDeleteId?.let { id ->
        ConfirmDangerDialog(
            title = "Elimina lista",
            message = "Vuoi eliminare questa lista della spesa e tutti i suoi articoli?",
            confirmLabel = "Elimina",
            onConfirm = { viewModel.deleteList(id) },
            onDismiss = { listToDeleteId = null }
        )
    }
}

@Composable
private fun ShoppingItemCard(
    item: ShoppingItemEntity,
    categoryName: String?,
    onTogglePurchased: (Boolean) -> Unit,
    onMoveToPantry: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPurchased) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shopping_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = item.isPurchased,
                    onCheckedChange = onTogglePurchased
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (item.isPurchased) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = buildString {
                        append(UnitConverter.formatQuantity(item.quantity, item.unit))
                        if (!categoryName.isNullOrBlank()) append(" • $categoryName")
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Elimina articolo")
                }
            }

            if (item.isPurchased) {
                Spacer(modifier = Modifier.height(6.dp))
                if (item.addedToPantryBatchId != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = LocalPantryColors.current.fresh,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Inserito in dispensa",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalPantryColors.current.fresh
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onMoveToPantry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(horizontal = 8.dp)
                            .testTag("move_to_pantry_${item.id}")
                    ) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Carica in dispensa",
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
private fun AddShoppingItemDialog(
    categories: List<CategoryEntity>,
    defaultUnit: UnitOfMeasure,
    onConfirm: (String, Double, UnitOfMeasure, String?, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var unit by remember(defaultUnit) { mutableStateOf(defaultUnit) }
    var categoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo articolo da acquistare") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Prodotto *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Q.tà") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.9f)
                    )
                    UnitDropdownField(
                        selectedUnit = unit,
                        onUnitSelected = { unit = it },
                        modifier = Modifier.weight(1.4f)
                    )
                }
                if (categories.isNotEmpty() && categoryId != null) {
                    CategoryDropdownField(
                        categories = categories,
                        selectedCategoryId = categoryId!!,
                        onCategorySelected = { categoryId = it }
                    )
                }
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
                val qty = qtyText.replace(",", ".").toDoubleOrNull() ?: 1.0
                if (name.isNotBlank() && qty > 0.0) {
                    onConfirm(
                        name,
                        qty,
                        unit,
                        categoryId,
                        notes
                    )
                }
            }) {
                Text("Aggiungi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
private fun ConfirmAddToPantryDialog(
    item: ShoppingItemEntity,
    locations: List<StorageLocationEntity>,
    onConfirm: (String, Long?, ExpiryType) -> Unit,
    onDismiss: () -> Unit
) {
    var locationId by remember { mutableStateOf(DatabaseSeeder.DEFAULT_LOCATION_PANTRY_ID) }
    var expiryDate by remember { mutableStateOf<Long?>(null) }
    var expiryType by remember { mutableStateOf(ExpiryType.USE_BY) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inserisci \"${item.name}\" in dispensa") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                Text(
                    text = "Verrà creato un nuovo lotto di ${UnitConverter.formatQuantity(item.quantity, item.unit)}.",
                    style = MaterialTheme.typography.bodySmall
                )
                LocationSelectorRow(
                    locations = locations,
                    selectedLocationId = locationId,
                    onLocationSelected = { locationId = it }
                )
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = expiryDate?.let { "Scadenza: ${PantryDateUtils.formatDate(it)}" } ?: "Imposta data scadenza",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    locationId,
                    expiryDate,
                    expiryType
                )
            }) {
                Text("Conferma carico")
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
