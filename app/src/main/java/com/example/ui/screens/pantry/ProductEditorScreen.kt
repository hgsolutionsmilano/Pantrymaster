package com.example.ui.screens.pantry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.db.DatabaseSeeder
import com.example.domain.model.ExpiryType
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.PantryDateUtils
import com.example.domain.usecase.UnitConverter
import com.example.ui.components.CategoryDropdownField
import com.example.ui.components.LocationSelectorRow
import com.example.ui.components.PantryDatePickerModal
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.UnitDropdownField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditorScreen(
    productId: String?,
    prefillBarcode: String? = null,
    prefillName: String? = null,
    prefillBrand: String? = null,
    prefillCategoryId: String? = null,
    prefillDescription: String? = null,
    prefillUnitName: String? = null,
    prefillLocalImagePath: String? = null,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: PantryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val existingProductFlow = remember(productId) {
        productId?.let { viewModel.observeProductDetail(it) }
    }
    val existingDetails = existingProductFlow?.collectAsStateWithLifecycle()?.value

    var initialized by remember(productId) { mutableStateOf(false) }
    var unitInitialized by remember(productId, prefillUnitName) {
        mutableStateOf(prefillUnitName != null)
    }

    var name by remember { mutableStateOf(prefillName.orEmpty()) }
    var brand by remember { mutableStateOf(prefillBrand.orEmpty()) }
    var barcode by remember { mutableStateOf(prefillBarcode.orEmpty()) }
    var categoryId by remember { mutableStateOf(prefillCategoryId ?: DatabaseSeeder.DEFAULT_CATEGORY_OTHER_ID) }
    var locationId by remember { mutableStateOf(DatabaseSeeder.DEFAULT_LOCATION_PANTRY_ID) }
    var description by remember { mutableStateOf(prefillDescription.orEmpty()) }
    var unit by remember {
        mutableStateOf(
            prefillUnitName?.let { runCatching { UnitOfMeasure.valueOf(it) }.getOrNull() }
                ?: state.defaultUnit
        )
    }
    var minStockText by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var localImagePath by remember { mutableStateOf(prefillLocalImagePath) }
    var removeImage by remember { mutableStateOf(false) }

    // Optional initial batch (only on creation)
    var addInitialBatch by remember { mutableStateOf(productId == null) }
    var initialQtyText by remember { mutableStateOf("1") }
    var initialExpiryDate by remember { mutableStateOf<Long?>(null) }
    var initialExpiryType by remember { mutableStateOf(ExpiryType.USE_BY) }
    var showExpiryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.isLoading, state.defaultUnit) {
        if (productId == null && !unitInitialized && !state.isLoading) {
            unit = state.defaultUnit
            unitInitialized = true
        }
    }

    LaunchedEffect(existingDetails) {
        if (productId != null && existingDetails != null && !initialized) {
            val p = existingDetails.product
            name = p.name
            brand = p.brand
            barcode = p.barcode.orEmpty()
            categoryId = p.categoryId
            locationId = p.defaultLocationId
            description = p.description
            unit = p.unit
            minStockText = if (p.minStockQuantity % 1.0 == 0.0) {
                p.minStockQuantity.toInt().toString()
            } else {
                p.minStockQuantity.toString()
            }
            notes = p.notes
            localImagePath = existingDetails.primaryImagePath
            removeImage = false
            addInitialBatch = false
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
                    localImagePath = savedPath
                    removeImage = false
                }
            }
        }
    }

    val canSubmit = !isSaving && (productId == null || initialized)

    fun submitProduct() {
        val minStock = minStockText.replace(",", ".").toDoubleOrNull() ?: 0.0
        val initQty = if (addInitialBatch) {
            initialQtyText.replace(",", ".").toDoubleOrNull()
        } else null
        viewModel.saveProduct(
            productId = productId,
            name = name,
            brand = brand,
            barcode = barcode,
            categoryId = categoryId,
            defaultLocationId = locationId,
            description = description,
            unit = unit,
            minStockQuantity = minStock,
            notes = notes,
            localImagePath = localImagePath,
            removeImage = removeImage,
            initialBatchQty = initQty,
            initialBatchExpiry = initialExpiryDate,
            initialBatchExpiryType = initialExpiryType,
            initialBatchPrice = null,
            onSaved = onSaved
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (productId == null) "Nuovo prodotto" else "Modifica prodotto")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(
                        enabled = canSubmit,
                        onClick = ::submitProduct,
                        modifier = Modifier.testTag("save_product_top_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Salva prodotto")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (productId != null && !initialized) {
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (productId != null && existingDetails != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Disponibilità attuale: ${
                                        UnitConverter.formatQuantity(
                                            existingDetails.totalAvailableQuantity,
                                            existingDetails.product.unit
                                        )
                                    } • ${existingDetails.activeBatches.size} lotti attivi",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Le quantità e le scadenze dei singoli lotti si gestiscono dal dettaglio prodotto.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                // Section 1: Identity & Photo
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Prodotto e immagine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ProductThumbnail(
                                imagePath = localImagePath,
                                categoryIcon = state.categories.firstOrNull { it.id == categoryId }?.iconName ?: "category",
                                categoryColorHex = state.categories.firstOrNull { it.id == categoryId }?.colorHex,
                                productName = name.ifBlank { "Prodotto" },
                                size = 76.dp
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (localImagePath != null) "Cambia foto" else "Scegli foto")
                                }
                                if (localImagePath != null) {
                                    TextButton(
                                        onClick = {
                                            localImagePath = null
                                            removeImage = true
                                        },
                                        modifier = Modifier.testTag("remove_product_photo_button")
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Rimuovi immagine")
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nome prodotto *") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_name_input")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("Marca") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text("Barcode") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Section 2: Classification, Location & Unit
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Classificazione e scorte",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (state.categories.isNotEmpty()) {
                            CategoryDropdownField(
                                categories = state.categories,
                                selectedCategoryId = categoryId,
                                onCategorySelected = { categoryId = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text("Posizione predefinita", style = MaterialTheme.typography.labelLarge)
                        LocationSelectorRow(
                            locations = state.locations,
                            selectedLocationId = locationId,
                            onLocationSelected = { locationId = it }
                        )

                        UnitDropdownField(
                            selectedUnit = unit,
                            onUnitSelected = { unit = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = minStockText,
                            onValueChange = { minStockText = it },
                            label = { Text("Scorta minima di avviso") },
                            suffix = { Text(unit.symbol) },
                            supportingText = {
                                Text("Se > 0, il prodotto compare tra i suggerimenti sotto scorta quando scende sotto questa soglia.")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Section 3: Description & Storage Notes
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Dettagli e conservazione",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Descrizione / Formato") },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Note di conservazione") },
                            minLines = 2,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Section 4: Optional Initial Batch (Creation only)
                if (productId == null) {
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
                                Text(
                                    text = "Aggiungi lotto iniziale",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Switch(
                                    checked = addInitialBatch,
                                    onCheckedChange = { addInitialBatch = it }
                                )
                            }

                            if (addInitialBatch) {
                                OutlinedTextField(
                                    value = initialQtyText,
                                    onValueChange = { initialQtyText = it },
                                    label = { Text("Quantità iniziale") },
                                    suffix = { Text(unit.symbol) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("initial_batch_qty_input")
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { showExpiryPicker = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            initialExpiryDate?.let { "Data: ${PantryDateUtils.formatDate(it)}" }
                                                ?: "Imposta data di scadenza / TMC"
                                        )
                                    }
                                    if (initialExpiryDate != null) {
                                        IconButton(
                                            onClick = { initialExpiryDate = null },
                                            modifier = Modifier.testTag("clear_initial_expiry_button")
                                        ) {
                                            Icon(Icons.Default.Clear, contentDescription = "Rimuovi data")
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ExpiryType.entries.forEach { t ->
                                        FilterChip(
                                            selected = initialExpiryType == t,
                                            onClick = { initialExpiryType = t },
                                            label = { Text(if (t == ExpiryType.USE_BY) "Data di scadenza" else "TMC") }
                                        )
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(initialExpiryType.infoIt, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    enabled = canSubmit,
                    onClick = ::submitProduct,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_product_submit_button")
                ) {
                    Text(
                        when {
                            isSaving -> "Salvataggio in corso..."
                            productId == null -> "Salva in dispensa"
                            else -> "Aggiorna prodotto"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showExpiryPicker) {
        PantryDatePickerModal(
            initialDateMillis = initialExpiryDate,
            onDateSelected = { initialExpiryDate = it },
            onDismiss = { showExpiryPicker = false }
        )
    }
}
