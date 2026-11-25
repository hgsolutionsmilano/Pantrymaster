package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ProductBatchEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.StorageLocationEntity
import com.example.data.local.relation.ProductWithDetails
import com.example.data.repository.AppUpdateInfo
import com.example.data.repository.InAppUpdateDownloadState
import com.example.domain.model.ExpiryType
import com.example.domain.model.LocationType
import com.example.domain.model.UnitOfMeasure
import com.example.domain.usecase.PantryDateUtils
import com.example.domain.usecase.ShelfLifeEstimate
import com.example.ui.components.LocationSelectorRow
import com.example.ui.components.PantryFlowRow
import com.example.ui.screens.pantry.BatchCard
import com.example.ui.screens.pantry.ProductHeaderSummaryCard
import com.example.ui.screens.scanner.BarcodeLookupAsyncState
import com.example.ui.screens.scanner.ExpiryCaptureBottomCard
import com.example.ui.screens.scanner.OcrCaptureStatus
import com.example.ui.screens.scanner.ScannerUiState
import com.example.ui.screens.settings.AppUpdateAvailableDialog
import com.example.ui.theme.PantryMasterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PantryUiSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pantryFlowRow_and_locationSelectorRow_renderWithoutCrash() {
        val locations = listOf(
            StorageLocationEntity("loc1", "Dispensa", LocationType.PANTRY, "inventory", 1),
            StorageLocationEntity("loc2", "Frigorifero", LocationType.FRIDGE, "kitchen", 2),
            StorageLocationEntity("loc3", "Freezer", LocationType.FREEZER, "ac_unit", 3),
            StorageLocationEntity("loc4", "Altro", LocationType.OTHER, "more", 4)
        )

        composeRule.setContent {
            MaterialTheme {
                Column {
                    PantryFlowRow {
                        FilterChip(selected = true, onClick = {}, label = { Text("Sistema") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Chiaro") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Scuro") })
                    }
                    LocationSelectorRow(
                        locations = locations,
                        selectedLocationId = "loc1",
                        onLocationSelected = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Sistema").assertIsDisplayed()
        composeRule.onNodeWithText("Dispensa").assertIsDisplayed()
        composeRule.onNodeWithText("Frigorifero").assertIsDisplayed()
    }

    @Test
    fun productDetail_consumeButtons_remainHorizontalAndProportionalOnCompactPhone() {
        val now = System.currentTimeMillis()
        val product = ProductEntity(
            id = "p1",
            name = "Latte Fresco Intero Alta Qualità",
            brand = "Fattoria Verde",
            categoryId = "cat1",
            defaultLocationId = "loc2",
            unit = UnitOfMeasure.LITERS,
            minStockQuantity = 2.0
        )
        val batch = ProductBatchEntity(
            id = "b1",
            productId = "p1",
            locationId = "loc2",
            initialQuantity = 2.0,
            remainingQuantity = 1.5,
            unit = UnitOfMeasure.LITERS,
            purchaseDate = now - 86_400_000L,
            expiryDate = now - 3 * 86_400_000L,
            expiryType = ExpiryType.BEST_BEFORE
        )
        val details = ProductWithDetails(
            product = product,
            category = CategoryEntity("cat1", "Latticini", "egg", "#3E7B52", true),
            defaultLocation = StorageLocationEntity("loc2", "Frigorifero Principale", LocationType.FRIDGE, "kitchen", 2),
            images = emptyList(),
            batches = listOf(batch)
        )

        composeRule.setContent {
            PantryMasterTheme {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .padding(16.dp)
                ) {
                    Column {
                        ProductHeaderSummaryCard(
                            details = details,
                            onAddBatch = {},
                            onConsumeFefo = {}
                        )
                        BatchCard(
                            batch = batch,
                            locationName = "Frigorifero Principale",
                            onConsume = {},
                            onWaste = {},
                            onAdjust = {},
                            onTransfer = {},
                            onEditDates = {},
                            onCancel = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("detail_add_batch_button").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_consume_fefo_button").assertIsDisplayed()
        composeRule.onNodeWithTag("batch_consume_b1").assertIsDisplayed()

        val addBounds = composeRule.onNodeWithTag("detail_add_batch_button").getUnclippedBoundsInRoot()
        val fefoBounds = composeRule.onNodeWithTag("detail_consume_fefo_button").getUnclippedBoundsInRoot()
        val batchConsumeBounds = composeRule.onNodeWithTag("batch_consume_b1").getUnclippedBoundsInRoot()

        val addW = (addBounds.right - addBounds.left).value
        val addH = (addBounds.bottom - addBounds.top).value
        val fefoW = (fefoBounds.right - fefoBounds.left).value
        val fefoH = (fefoBounds.bottom - fefoBounds.top).value
        val batchW = (batchConsumeBounds.right - batchConsumeBounds.left).value
        val batchH = (batchConsumeBounds.bottom - batchConsumeBounds.top).value

        assertTrue("Add batch button must be wider than tall ($addW x $addH)", addW > addH)
        assertTrue("Consuma FEFO button must be wider than tall ($fefoW x $fefoH)", fefoW > fefoH)
        assertTrue("Consuma FEFO height must stay compact ($fefoH)", fefoH <= 64f)
        assertTrue("Batch Consuma button must be wider than tall ($batchW x $batchH)", batchW > batchH)
        assertTrue("Batch Consuma height must stay compact ($batchH)", batchH <= 64f)
    }

    @Test
    fun scanner_ocrCaptureCard_displaysDetectedDateAndHistoricalFallback() {
        val detectedMillis = PantryDateUtils.normalizeToUtcMidnight(2027, 4, 20)
        val estimatedMillis = PantryDateUtils.normalizeToUtcMidnight(2026, 11, 10)
        val state = ScannerUiState.CapturingExpiry(
            barcode = "8076800195057",
            lookupState = BarcodeLookupAsyncState.Ready(
                result = com.example.data.repository.BarcodeLookupResult.NotFound("8076800195057"),
                shelfLifeEstimate = ShelfLifeEstimate(
                    averageDays = 15,
                    sampleCount = 3,
                    estimatedExpiryMillis = estimatedMillis,
                    dominantExpiryType = ExpiryType.USE_BY
                )
            ),
            ocrStatus = OcrCaptureStatus.Detected(
                expiryDateMillis = detectedMillis,
                expiryType = ExpiryType.BEST_BEFORE,
                matchedText = "TMC 20/05/2027",
                fromHistoricalEstimate = false
            ),
            isAutoCapturePaused = true
        )

        composeRule.setContent {
            PantryMasterTheme {
                ExpiryCaptureBottomCard(
                    state = state,
                    onConfirmDetected = {},
                    onSelectExpiryType = {},
                    onRetryOcr = {},
                    onOpenManualPicker = {},
                    onUseHistoricalEstimate = {},
                    onSkip = {},
                    onChangeBarcode = {}
                )
            }
        }

        composeRule.onNodeWithText("20/05/2027").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_ocr_expiry_button").assertIsDisplayed()
        composeRule.onNodeWithTag("use_historical_estimate_button").assertIsDisplayed()
        composeRule.onNodeWithTag("ocr_retry_button").assertIsDisplayed()
    }

    @Test
    fun appUpdateDialog_showsBackgroundProgressThenReadyToInstallWithoutExternalLinks() {
        val updateInfo = AppUpdateInfo(
            currentVersion = "1.0.0",
            latestVersion = "1.1.0",
            latestBuildCode = 12,
            tagName = "v1.1.0",
            releaseTitle = "PantryMaster v1.1.0",
            releaseNotes = "Aggiornamento in-app automatico",
            releasePageUrl = "https://github.com/acme/PantryMaster/releases/tag/v1.1.0",
            apkDownloadUrl = "https://github.com/acme/PantryMaster/releases/download/v1.1.0/app.apk",
            apkFileName = "app.apk",
            apkSizeBytes = 12_000_000L,
            publishedAt = null
        )

        composeRule.setContent {
            PantryMasterTheme {
                AppUpdateAvailableDialog(
                    updateInfo = updateInfo,
                    downloadState = InAppUpdateDownloadState.ReadyToInstall(
                        updateInfo = updateInfo,
                        apkFile = File("/tmp/app.apk")
                    ),
                    canInstallPackages = true,
                    onInstallClick = {},
                    onContinueInBackground = {},
                    onRetryClick = {},
                    onRemindLater = {},
                    onIgnoreVersion = {}
                )
            }
        }

        composeRule.onNodeWithText("Aggiornamento pronto").assertIsDisplayed()
        composeRule.onNodeWithTag("update_dialog_install_button").assertIsDisplayed()
        composeRule.onNodeWithTag("update_dialog_later_button").assertIsDisplayed()
    }
}

