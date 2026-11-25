package com.example.ui.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.db.DatabaseSeeder
import com.example.data.repository.BarcodeLookupResult
import com.example.domain.model.ExpiryType
import com.example.domain.usecase.PantryDateUtils
import com.example.ui.components.LocationSelectorRow
import com.example.ui.components.PantryDatePickerModal
import com.example.ui.components.ProductThumbnail
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onManualCreate: (barcode: String?) -> Unit,
    onOpenRemotePrefill: (BarcodeLookupResult.RemoteProduct) -> Unit,
    onOpenProductDetail: (String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var manualBarcode by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Scanner Barcode") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("scanner_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (hasCameraPermission && uiState is ScannerUiState.Scanning) {
                        IconButton(onClick = { torchEnabled = !torchEnabled }) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                                contentDescription = "Torcia"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is ScannerUiState.Scanning -> {
                    if (hasCameraPermission) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.Black)
                        ) {
                            BarcodeCameraPreview(
                                torchEnabled = torchEnabled,
                                onBarcodeScanned = viewModel::onBarcodeDetected,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Scan reticle overlay
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Surface(
                                    color = Color.Transparent,
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.size(width = 260.dp, height = 150.dp)
                                ) {}
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Inquadra il codice a barre del prodotto",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Accesso alla fotocamera non concesso",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Puoi concedere il permesso per scansionare i codici o inserire il codice manualmente qui sotto.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                    Text("Consenti fotocamera")
                                }
                            }
                        }
                    }

                    // Always-available manual barcode lookup & manual entry
                    Card(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Ricerca o inserimento manuale",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = manualBarcode,
                                    onValueChange = { manualBarcode = it },
                                    label = { Text("Codice EAN / UPC") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("manual_barcode_input")
                                )
                                Button(
                                    onClick = { viewModel.onBarcodeDetected(manualBarcode) },
                                    enabled = manualBarcode.trim().isNotEmpty(),
                                    modifier = Modifier.testTag("manual_barcode_search_button")
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Cerca codice")
                                }
                            }
                            OutlinedButton(
                                onClick = { onManualCreate(manualBarcode.trim().ifEmpty { null }) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scanner_manual_entry_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Inserimento manuale completo")
                            }
                        }
                    }
                }

                is ScannerUiState.Loading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Ricerca barcode ${state.barcode}...",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Verifica archivio locale e Open Food Facts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is ScannerUiState.Result -> {
                    ScannerResultContent(
                        lookup = state.lookup,
                        locations = locations,
                        onAddBatchToLocal = { product, qty, exp, expType, locId, price ->
                            viewModel.addBatchToLocalProduct(
                                productId = product.id,
                                unit = product.unit,
                                quantity = qty,
                                expiryDate = exp,
                                expiryType = expType,
                                locationId = locId,
                                price = price,
                                onSuccess = { onOpenProductDetail(product.id) }
                            )
                        },
                        onEditRemoteAndSave = onOpenRemotePrefill,
                        onManualCreate = onManualCreate,
                        onRescan = viewModel::resumeScanning
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun BarcodeCameraPreview(
    torchEnabled: Boolean,
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    var boundCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    LaunchedEffect(torchEnabled, boundCamera) {
        boundCamera?.cameraControl?.enableTorch(torchEnabled)
    }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    val code = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                    if (code != null) {
                                        onBarcodeScanned(code)
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}

@Composable
private fun ScannerResultContent(
    lookup: BarcodeLookupResult,
    locations: List<com.example.data.local.entity.StorageLocationEntity>,
    onAddBatchToLocal: (
        com.example.data.local.entity.ProductEntity,
        Double,
        Long?,
        ExpiryType,
        String,
        Double?
    ) -> Unit,
    onEditRemoteAndSave: (BarcodeLookupResult.RemoteProduct) -> Unit,
    onManualCreate: (String) -> Unit,
    onRescan: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        when (lookup) {
            is BarcodeLookupResult.LocalProduct -> {
                val product = lookup.product
                var qtyText by remember { mutableStateOf("1") }
                var expiryDate by remember { mutableStateOf<Long?>(null) }
                var expiryType by remember { mutableStateOf(ExpiryType.USE_BY) }
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
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(expiryDate?.let { "Scadenza: ${PantryDateUtils.formatDate(it)}" } ?: "Imposta data scadenza")
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
                Button(
                    onClick = { onEditRemoteAndSave(lookup) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
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
                Button(
                    onClick = { onManualCreate(lookup.barcode) },
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
                Button(
                    onClick = { onManualCreate(lookup.barcode) },
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scansiona un altro codice")
        }
    }
}
