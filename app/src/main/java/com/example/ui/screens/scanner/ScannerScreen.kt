package com.example.ui.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.BarcodeLookupResult
import com.example.domain.model.ExpiryType
import com.example.domain.usecase.OcrLineCandidate
import com.example.ui.components.PantryDatePickerModal
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private enum class ActiveCameraAnalyzerMode {
    BARCODE,
    OCR_EXPIRY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onManualCreate: (barcode: String?, expiryMillis: Long?, expiryType: ExpiryType?) -> Unit,
    onOpenRemotePrefill: (BarcodeLookupResult.RemoteProduct, Long?, ExpiryType?) -> Unit,
    onOpenProductDetail: (String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
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
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var manualBarcode by remember { mutableStateOf("") }
    var showCaptureDatePicker by remember { mutableStateOf(false) }

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

    LaunchedEffect(uiState is ScannerUiState.ScanningBarcode, uiState is ScannerUiState.CapturingExpiry) {
        when (uiState) {
            is ScannerUiState.ScanningBarcode -> zoomRatio = 1f
            is ScannerUiState.CapturingExpiry -> zoomRatio = 1.5f
            is ScannerUiState.Result -> Unit
        }
    }

    // Subtle haptic feedback when barcode locks or OCR date locks
    val isOcrDetected = (uiState as? ScannerUiState.CapturingExpiry)?.ocrStatus is OcrCaptureStatus.Detected
    LaunchedEffect(isOcrDetected) {
        if (isOcrDetected) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BackHandler(enabled = uiState is ScannerUiState.CapturingExpiry || uiState is ScannerUiState.Result) {
        viewModel.resumeScanning()
    }

    val isCameraPhase = uiState is ScannerUiState.ScanningBarcode || uiState is ScannerUiState.CapturingExpiry

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (uiState) {
                            is ScannerUiState.ScanningBarcode -> "Scanner Barcode"
                            is ScannerUiState.CapturingExpiry -> "Scansiona Scadenza"
                            is ScannerUiState.Result -> "Risultato Scansione"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState is ScannerUiState.CapturingExpiry) {
                                viewModel.resumeScanning()
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("scanner_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (hasCameraPermission && isCameraPhase) {
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
                .imePadding()
        ) {
            if (isCameraPhase) {
                if (hasCameraPermission) {
                    val captureState = uiState as? ScannerUiState.CapturingExpiry
                    val analyzerMode = if (captureState != null) {
                        ActiveCameraAnalyzerMode.OCR_EXPIRY
                    } else {
                        ActiveCameraAnalyzerMode.BARCODE
                    }
                    val isPaused = captureState?.isAutoCapturePaused == true
                    val reticleBorderColor by animateColorAsState(
                        targetValue = when {
                            captureState?.ocrStatus is OcrCaptureStatus.Detected -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        label = "reticle_border_color"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black)
                    ) {
                        DualModeCameraPreview(
                            mode = analyzerMode,
                            isPaused = isPaused,
                            torchEnabled = torchEnabled,
                            zoomRatio = zoomRatio,
                            onBarcodeScanned = { code ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.onBarcodeDetected(code, cameraAvailable = true)
                            },
                            onOcrLinesDetected = viewModel::onOcrLinesDetected,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (captureState != null) {
                            ExpiryCaptureProductBanner(
                                barcode = captureState.barcode,
                                lookupState = captureState.lookupState,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }

                        // Central guided reticle (wide barcode frame vs focused OCR date strip)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Surface(
                                color = if (captureState?.ocrStatus is OcrCaptureStatus.Detected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    Color.Transparent
                                },
                                border = BorderStroke(2.5.dp, reticleBorderColor),
                                shape = RoundedCornerShape(18.dp),
                                modifier = if (analyzerMode == ActiveCameraAnalyzerMode.OCR_EXPIRY) {
                                    Modifier.size(width = 296.dp, height = 88.dp)
                                } else {
                                    Modifier.size(width = 260.dp, height = 150.dp)
                                }
                            ) {
                                if (captureState?.ocrStatus is OcrCaptureStatus.Detected) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Data rilevata",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Hint & Zoom controls overlay at bottom of camera viewport
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.68f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = when {
                                        captureState == null -> "Inquadra il codice a barre del prodotto"
                                        captureState.ocrStatus is OcrCaptureStatus.Detected -> "Data agganciata! Conferma qui sotto"
                                        else -> "Inquadra la data stampata nel riquadro"
                                    },
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }

                            if (analyzerMode == ActiveCameraAnalyzerMode.OCR_EXPIRY) {
                                FilterChip(
                                    selected = zoomRatio > 1.2f,
                                    onClick = {
                                        zoomRatio = when {
                                            zoomRatio < 1.25f -> 1.5f
                                            zoomRatio < 1.8f -> 2f
                                            else -> 1f
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ZoomIn,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            when {
                                                zoomRatio >= 1.8f -> "2x"
                                                zoomRatio >= 1.3f -> "1.5x"
                                                else -> "1x"
                                            }
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color.Black.copy(alpha = 0.72f),
                                        labelColor = Color.White,
                                        iconColor = Color.White,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                            }
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
                                text = "Puoi concedere il permesso per scansionare codici e date di scadenza o inserire il codice manualmente qui sotto.",
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

                when (val state = uiState) {
                    is ScannerUiState.ScanningBarcode -> {
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
                                        onClick = {
                                            viewModel.onBarcodeDetected(
                                                barcode = manualBarcode,
                                                cameraAvailable = hasCameraPermission
                                            )
                                        },
                                        enabled = manualBarcode.trim().isNotEmpty(),
                                        modifier = Modifier.testTag("manual_barcode_search_button")
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "Cerca codice")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        onManualCreate(
                                            manualBarcode.trim().ifEmpty { null },
                                            null,
                                            null
                                        )
                                    },
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

                    is ScannerUiState.CapturingExpiry -> {
                        ExpiryCaptureBottomCard(
                            state = state,
                            onConfirmDetected = viewModel::confirmCapturedExpiry,
                            onSelectExpiryType = viewModel::updateCapturedExpiryType,
                            onRetryOcr = viewModel::retryOcrCapture,
                            onOpenManualPicker = { showCaptureDatePicker = true },
                            onUseHistoricalEstimate = viewModel::applyHistoricalEstimateAndConfirm,
                            onSkip = viewModel::skipExpiryCapture,
                            onChangeBarcode = viewModel::resumeScanning
                        )
                    }

                    is ScannerUiState.Result -> Unit
                }
            } else if (uiState is ScannerUiState.Result) {
                val resultState = uiState as ScannerUiState.Result
                ScannerResultContent(
                    state = resultState,
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
                    onManualCreate = { code, expMillis, expType ->
                        onManualCreate(code, expMillis, expType)
                    },
                    onRescan = viewModel::resumeScanning
                )
            }
        }
    }

    if (showCaptureDatePicker) {
        val currentDetectedMillis = ((uiState as? ScannerUiState.CapturingExpiry)?.ocrStatus as? OcrCaptureStatus.Detected)
            ?.expiryDateMillis
        PantryDatePickerModal(
            initialDateMillis = currentDetectedMillis,
            onDateSelected = { pickedMillis ->
                if (pickedMillis != null) {
                    viewModel.setManualExpiryInCapture(pickedMillis)
                }
            },
            onDismiss = { showCaptureDatePicker = false }
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun DualModeCameraPreview(
    mode: ActiveCameraAnalyzerMode,
    isPaused: Boolean,
    torchEnabled: Boolean,
    zoomRatio: Float,
    onBarcodeScanned: (String) -> Unit,
    onOcrLinesDetected: (List<OcrLineCandidate>) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val currentMode by rememberUpdatedState(mode)
    val currentPaused by rememberUpdatedState(isPaused)
    val currentOnBarcode by rememberUpdatedState(onBarcodeScanned)
    val currentOnOcr by rememberUpdatedState(onOcrLinesDetected)

    val lastOcrFrameMs = remember { AtomicLong(0L) }

    val barcodeScanner = remember {
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

    val textRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var tapFocusPoint by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(torchEnabled, boundCamera) {
        boundCamera?.cameraControl?.enableTorch(torchEnabled)
    }

    LaunchedEffect(zoomRatio, boundCamera) {
        val cam = boundCamera ?: return@LaunchedEffect
        val zoomState = cam.cameraInfo.zoomState.value
        val clamped = if (zoomState != null) {
            zoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        } else {
            zoomRatio
        }
        cam.cameraControl.setZoomRatio(clamped)
    }

    // Trigger center AF/AE when switching into close-up OCR mode and pulse gently while searching
    LaunchedEffect(mode, isPaused, boundCamera, previewViewRef) {
        val cam = boundCamera ?: return@LaunchedEffect
        val pv = previewViewRef ?: return@LaunchedEffect
        if (mode != ActiveCameraAnalyzerMode.OCR_EXPIRY || isPaused) return@LaunchedEffect

        fun focusCenter() {
            if (pv.width > 0 && pv.height > 0) {
                val point = pv.meteringPointFactory.createPoint(pv.width * 0.5f, pv.height * 0.5f)
                val action = FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                )
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                cam.cameraControl.startFocusAndMetering(action)
            }
        }

        delay(120L)
        focusCenter()
        while (true) {
            delay(2600L)
            focusCenter()
        }
    }

    LaunchedEffect(tapFocusPoint) {
        if (tapFocusPoint != null) {
            delay(850L)
            tapFocusPoint = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            barcodeScanner.close()
            textRecognizer.close()
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier.pointerInput(boundCamera, previewViewRef) {
            detectTapGestures { offset ->
                val cam = boundCamera ?: return@detectTapGestures
                val pv = previewViewRef ?: return@detectTapGestures
                tapFocusPoint = offset
                val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                val action = FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                )
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                cam.cameraControl.startFocusAndMetering(action)
            }
        }
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).also { previewViewRef = it }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1920, 1080),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()

                        val preview = Preview.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .build()
                            .also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (currentPaused) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val rotation = imageProxy.imageInfo.rotationDegrees

                            when (currentMode) {
                                ActiveCameraAnalyzerMode.BARCODE -> {
                                    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                                    barcodeScanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            val code = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                            if (code != null) {
                                                currentOnBarcode(code)
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                }

                                ActiveCameraAnalyzerMode.OCR_EXPIRY -> {
                                    val now = System.currentTimeMillis()
                                    val prev = lastOcrFrameMs.get()
                                    if (now - prev < 50L || !lastOcrFrameMs.compareAndSet(prev, now)) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }

                                    val croppedBitmap = runCatching {
                                        extractOcrRoiBitmap(imageProxy, rotation)
                                    }.getOrNull()

                                    val inputImage = if (croppedBitmap != null) {
                                        InputImage.fromBitmap(croppedBitmap, 0)
                                    } else {
                                        InputImage.fromMediaImage(mediaImage, rotation)
                                    }

                                    val imgW = if (croppedBitmap != null) {
                                        croppedBitmap.width.toFloat()
                                    } else if (rotation == 90 || rotation == 270) {
                                        imageProxy.height.toFloat()
                                    } else {
                                        imageProxy.width.toFloat()
                                    }
                                    val imgH = if (croppedBitmap != null) {
                                        croppedBitmap.height.toFloat()
                                    } else if (rotation == 90 || rotation == 270) {
                                        imageProxy.width.toFloat()
                                    } else {
                                        imageProxy.height.toFloat()
                                    }

                                    textRecognizer.process(inputImage)
                                        .addOnSuccessListener { visionText ->
                                            val roiLines = extractSortedOcrCandidates(
                                                visionText = visionText,
                                                imageWidth = imgW,
                                                imageHeight = imgH,
                                                alreadyCroppedToRoi = croppedBitmap != null
                                            )
                                            if (roiLines.isNotEmpty()) {
                                                currentOnOcr(roiLines)
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                            croppedBitmap?.recycle()
                                        }
                                }
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
            modifier = Modifier.fillMaxSize()
        )

        val density = LocalDensity.current
        AnimatedVisibility(
            visible = tapFocusPoint != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            tapFocusPoint?.let { pt ->
                val ringPx = with(density) { 56.dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (pt.x - ringPx / 2f).roundToInt(),
                                y = (pt.y - ringPx / 2f).roundToInt()
                            )
                        }
                        .size(56.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

private fun extractOcrRoiBitmap(imageProxy: ImageProxy, rotationDegrees: Int): Bitmap? {
    val fullBitmap = imageProxy.toBitmap()
    val rawW = fullBitmap.width
    val rawH = fullBitmap.height
    if (rawW <= 0 || rawH <= 0) {
        fullBitmap.recycle()
        return null
    }

    val isRotated90or270 = rotationDegrees == 90 || rotationDegrees == 270
    // Upright central strip matching the guided reticle (88% width x 38% height)
    val rawFracW = if (isRotated90or270) 0.38f else 0.88f
    val rawFracH = if (isRotated90or270) 0.88f else 0.38f

    val cropW = (rawW * rawFracW).roundToInt().coerceIn(64, rawW)
    val cropH = (rawH * rawFracH).roundToInt().coerceIn(64, rawH)
    val cropLeft = ((rawW - cropW) / 2).coerceAtLeast(0)
    val cropTop = ((rawH - cropH) / 2).coerceAtLeast(0)

    val cropped = if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropW, cropH, matrix, true)
    } else {
        Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropW, cropH)
    }

    if (cropped !== fullBitmap) {
        fullBitmap.recycle()
    }
    return cropped
}

private fun extractSortedOcrCandidates(
    visionText: Text,
    imageWidth: Float,
    imageHeight: Float,
    alreadyCroppedToRoi: Boolean
): List<OcrLineCandidate> {
    val allLines = visionText.textBlocks
        .flatMap { it.lines }
        .filter { it.text.isNotBlank() }
        .sortedWith(
            compareBy<Text.Line>(
                { it.boundingBox?.top ?: Int.MAX_VALUE },
                { it.boundingBox?.left ?: Int.MAX_VALUE }
            )
        )

    if (allLines.isEmpty()) return emptyList()

    val result = mutableListOf<OcrLineCandidate>()
    for (idx in allLines.indices) {
        val line = allLines[idx]
        val box = line.boundingBox
        val inCore = if (box != null && imageWidth > 0f && imageHeight > 0f) {
            val normX = box.centerX() / imageWidth
            val normY = box.centerY() / imageHeight
            if (alreadyCroppedToRoi) {
                normX in 0.04f..0.96f && normY in 0.15f..0.85f
            } else {
                normX in 0.08f..0.92f && normY in 0.28f..0.72f
            }
        } else {
            true
        }

        val prevText = allLines.getOrNull(idx - 1)?.text.orEmpty()
        result.add(
            OcrLineCandidate(
                text = line.text,
                inCoreRoi = inCore,
                contextText = prevText
            )
        )

        // Stitch horizontally adjacent fragments split across TextBlocks on the same visual row
        val next = allLines.getOrNull(idx + 1)
        val nextBox = next?.boundingBox
        if (box != null && nextBox != null) {
            val maxHeight = max(box.height(), nextBox.height()).coerceAtLeast(12)
            val verticalDelta = abs(box.centerY() - nextBox.centerY())
            if (verticalDelta <= maxHeight * 0.65f) {
                val ordered = if (box.left <= nextBox.left) {
                    "${line.text.trim()} ${next.text.trim()}"
                } else {
                    "${next.text.trim()} ${line.text.trim()}"
                }
                result.add(
                    OcrLineCandidate(
                        text = ordered,
                        inCoreRoi = inCore,
                        contextText = prevText
                    )
                )
            }
        }
    }
    return result
}
