package com.example.ui.screens.pantry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.db.DatabaseSeeder
import com.example.domain.model.ExpiryType
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.PantryDateUtils
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
    var name by remember { mutableStateOf(prefillName.orEmpty()) }
    var brand by remember { mutableStateOf(prefillBrand.orEmpty()) }
    var barcode by remember { mutableStateOf(prefillBarcode.orEmpty()) }
    var categoryId by remember { mutableStateOf(prefillCategoryId ?: DatabaseSeeder.DEFAULT_CATEGORY_OTHER_ID) }
    var locationId by remember { mutableStateOf(DatabaseSeeder.DEFAULT_LOCATION_PANTRY_ID) }
    var description by remember { mutableStateOf(prefillDescription.orEmpty()) }
    var unit by remember {
        mutableStateOf(
            prefillUnitName?.let { runCatching { UnitOfMeasure.valueOf(it) }.getOrNull() }
                ?: UnitOfMeasure.PIECES
        )
    }
    var minStockText by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var localImagePath by remember { mutableStateOf(prefillLocalImagePath) }

    // Optional initial batch (only on creation)
    var addInitialBatch by remember { mutableStateOf(productId == null) }
    var initialQtyText by remember { mutableStateOf("1") }
    var initialPriceText by remember { mutableStateOf("") }
    var initialExpiryDate by remember { mutableStateOf<Long?>(null) }
    var initialExpiryType by remember { mutableStateOf(ExpiryType.USE_BY) }
    var showExpiryPicker by remember { mutableStateOf(false) }

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
                }
            }
        }
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
                        enabled = !isSaving,
                        onClick = {
                            val minStock = minStockText.replace(",", ".").toDoubleOrNull() ?: 0.0
                            val initQty = if (addInitialBatch) {
                                initialQtyText.replace(",", ".").toDoubleOrNull()
                            } else null
                            val initPrice = if (addInitialBatch) {
                                initialPriceText.replace(",", ".").toDoubleOrNull()
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
                                initialBatchQty = initQty,
                                initialBatchExpiry = initialExpiryDate,
                                initialBatchExpiryType = initialExpiryType,
                                initialBatchPrice = initPrice,
                                onSaved = onSaved
                            )
                        },
                        modifier = Modifier.testTag("save_product_top_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Salva prodotto")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Photo Picker Card (zero-permission PickVisualMedia)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("Immagine prodotto", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scegli foto")
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome prodotto *") },
                singleLine = true,
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
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.categories.isNotEmpty()) {
                CategoryDropdownField(
                    categories = state.categories,
                    selectedCategoryId = categoryId,
                    onCategorySelected = { categoryId = it }
                )
            }

            Text("Posizione predefinita", style = MaterialTheme.typography.labelLarge)
            LocationSelectorRow(
                locations = state.locations,
                selectedLocationId = locationId,
                onLocationSelected = { locationId = it }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                UnitDropdownField(
                    selectedUnit = unit,
                    onUnitSelected = { unit = it },
                    modifier = Modifier.weight(1.3f)
                )
                OutlinedTextField(
                    value = minStockText,
                    onValueChange = { minStockText = it },
                    label = { Text("Scorta min.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrizione / Formato") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Note di conservazione") },
                modifier = Modifier.fillMaxWidth()
            )

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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = initialQtyText,
                                    onValueChange = { initialQtyText = it },
                                    label = { Text("Quantità (${unit.symbol})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("initial_batch_qty_input")
                                )
                                OutlinedTextField(
                                    value = initialPriceText,
                                    onValueChange = { initialPriceText = it },
                                    label = { Text("Prezzo €") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedButton(
                                onClick = { showExpiryPicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    initialExpiryDate?.let { "Data: ${PantryDateUtils.formatDate(it)}" }
                                        ?: "Imposta data di scadenza / TMC"
                                )
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
                enabled = !isSaving,
                onClick = {
                    val minStock = minStockText.replace(",", ".").toDoubleOrNull() ?: 0.0
                    val initQty = if (addInitialBatch) {
                        initialQtyText.replace(",", ".").toDoubleOrNull()
                    } else null
                    val initPrice = if (addInitialBatch) {
                        initialPriceText.replace(",", ".").toDoubleOrNull()
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
                        initialBatchQty = initQty,
                        initialBatchExpiry = initialExpiryDate,
                        initialBatchExpiryType = initialExpiryType,
                        initialBatchPrice = initPrice,
                        onSaved = onSaved
                    )
                },
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

    if (showExpiryPicker) {
        PantryDatePickerModal(
            initialDateMillis = initialExpiryDate,
            onDateSelected = { initialExpiryDate = it },
            onDismiss = { showExpiryPicker = false }
        )
    }
}
