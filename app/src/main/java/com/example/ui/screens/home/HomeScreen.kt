package com.example.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.relation.BatchWithProduct
import com.example.data.local.relation.HouseInfoWithPhotos
import com.example.domain.model.HouseInfoCategory
import com.example.domain.usecase.HouseInfoFormatter
import com.example.domain.usecase.UnitConverter
import com.example.ui.components.ExpiryBadge
import com.example.ui.components.PantryEmptyState
import com.example.ui.components.ProductThumbnail
import com.example.ui.screens.info.HouseInfoCategoryBadge
import com.example.ui.screens.info.launchPhoneDialer
import com.example.ui.theme.LocalPantryColors
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isExpanded: Boolean,
    onOpenScanner: () -> Unit,
    onAddProduct: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenPantry: () -> Unit,
    onOpenShopping: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenInfoDetail: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val semantics = LocalPantryColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = state.homeTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("home_title_text")
                    )
                    Text(
                        text = "La tua dispensa intelligente",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.testTag("home_history_button")
                ) {
                    Icon(Icons.Default.History, contentDescription = "Storico movimenti")
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("home_settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxSize()
            ) {
            // Editorial Hero & Quick Actions
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_pantry_hero),
                            contentDescription = "Panoramica dispensa naturale",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.15f),
                                            Color.Black.copy(alpha = 0.72f)
                                        )
                                    )
                                )
                        )
                        Column(
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "${state.totalProductsInStock} prodotti disponibili",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onOpenScanner,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("home_scan_button")
                                ) {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Scansiona",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                FilledTonalButton(
                                    onClick = onAddProduct,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("home_add_product_button")
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Aggiungi",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // KPI Metrics Grid (2x2 on phone, 4-in-row on tablet)
            item {
                val metrics = listOf(
                    KpiItem(
                        title = "Disponibili",
                        value = state.totalProductsInStock.toString(),
                        subtitle = "${state.totalRegisteredProducts} anagrafiche",
                        containerColor = semantics.freshContainer,
                        accentColor = semantics.fresh,
                        onClick = onOpenPantry
                    ),
                    KpiItem(
                        title = "In scadenza",
                        value = state.expiringSoonBatches.size.toString(),
                        subtitle = "Entro 7 giorni",
                        containerColor = semantics.warningContainer,
                        accentColor = semantics.warning,
                        onClick = onOpenPantry
                    ),
                    KpiItem(
                        title = "Scaduti",
                        value = state.expiredBatches.size.toString(),
                        subtitle = "Da verificare",
                        containerColor = semantics.expiredContainer,
                        accentColor = semantics.expired,
                        onClick = onOpenPantry
                    ),
                    KpiItem(
                        title = "Sotto scorta",
                        value = state.lowStockSuggestions.size.toString(),
                        subtitle = "Da rifornire",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        accentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = onOpenShopping
                    )
                )

                if (isExpanded) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        metrics.forEach { kpi ->
                            KpiMetricCard(kpi = kpi, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            KpiMetricCard(kpi = metrics[0], modifier = Modifier.weight(1f))
                            KpiMetricCard(kpi = metrics[1], modifier = Modifier.weight(1f))
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            KpiMetricCard(kpi = metrics[2], modifier = Modifier.weight(1f))
                            KpiMetricCard(kpi = metrics[3], modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // "Da consumare prima" (FEFO priority)
            item {
                SectionHeader(
                    title = "Da consumare prima",
                    actionLabel = "Vedi dispensa",
                    onAction = onOpenPantry
                )
            }

            if (state.consumeFirstBatches.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PantryEmptyState(
                            icon = Icons.Default.Inventory2,
                            title = "Nessun lotto con scadenza imminente",
                            subtitle = "Aggiungi i tuoi prodotti manualmente o tramite scanner barcode per monitorare le scadenze.",
                            actionLabel = "Aggiungi primo prodotto",
                            onAction = onAddProduct
                        )
                    }
                }
            } else {
                items(state.consumeFirstBatches, key = { it.batch.id }) { item ->
                    ConsumeFirstBatchCard(
                        item = item,
                        onOpenProduct = { item.product?.id?.let(onOpenProduct) },
                        onConsumeOne = { viewModel.quickConsumeBatchOneUnit(item) }
                    )
                }
            }

            // Second Home quick access ("In evidenza dalla casa")
            item {
                SectionHeader(
                    title = "In evidenza dalla casa",
                    actionLabel = "Apri Info",
                    onAction = onOpenInfo
                )
            }

            if (state.pinnedHouseInfo.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.pinnedHouseInfo, key = { it.entry.id }) { infoItem ->
                            PinnedHouseInfoCard(
                                item = infoItem,
                                onOpenDetail = { onOpenInfoDetail(infoItem.entry.id) }
                            )
                        }
                    }
                }
            } else {
                item {
                    Card(
                        onClick = onOpenInfo,
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_info_empty_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.HomeWork,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.totalHouseInfoCount == 0) {
                                        "Schede della seconda casa"
                                    } else {
                                        "${state.totalHouseInfoCount} schede salvate in Info"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (state.totalHouseInfoCount == 0) {
                                        "Salva numeri utili, misure, foto impianti, password Wi-Fi e codici."
                                    } else {
                                        "Fissa le schede più importanti per averle subito qui in Home."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Shopping list summary & Recent products (Side-by-side on Tablet, stacked on Phone)
            item {
                if (isExpanded) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ShoppingSummaryCard(
                            state = state,
                            onOpenShopping = onOpenShopping,
                            modifier = Modifier.weight(1f)
                        )
                        RecentProductsCard(
                            state = state,
                            onOpenProduct = onOpenProduct,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ShoppingSummaryCard(state = state, onOpenShopping = onOpenShopping)
                        RecentProductsCard(state = state, onOpenProduct = onOpenProduct)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        }
    }
}

private data class KpiItem(
    val title: String,
    val value: String,
    val subtitle: String,
    val containerColor: Color,
    val accentColor: Color,
    val onClick: () -> Unit
)

@Composable
private fun KpiMetricCard(kpi: KpiItem, modifier: Modifier = Modifier) {
    Card(
        onClick = kpi.onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = kpi.containerColor.copy(alpha = 0.72f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = kpi.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = kpi.accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = kpi.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = kpi.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun ConsumeFirstBatchCard(
    item: BatchWithProduct,
    onOpenProduct: () -> Unit,
    onConsumeOne: () -> Unit
) {
    val product = item.product ?: return
    Card(
        onClick = onOpenProduct,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            ProductThumbnail(
                imagePath = null,
                categoryIcon = "inventory",
                categoryColorHex = null,
                productName = product.name,
                size = 46.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = UnitConverter.formatQuantity(item.batch.remainingQuantity, item.batch.unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "• ${item.location?.name ?: "Dispensa"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                ExpiryBadge(
                    expiryDateMillis = item.batch.expiryDate,
                    expiryType = item.batch.expiryType
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onConsumeOne,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("consume_quick_${item.batch.id}")
            ) {
                Icon(
                    Icons.Default.CheckCircleOutline,
                    contentDescription = "Consuma",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Usa",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ShoppingSummaryCard(
    state: HomeDashboardUiState,
    onOpenShopping: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpenShopping,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lista della spesa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${state.pendingShoppingCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (state.pendingShoppingItems.isEmpty()) {
                Text(
                    text = if (state.lowStockSuggestions.isEmpty()) {
                        "Nessun articolo da acquistare al momento."
                    } else {
                        "${state.lowStockSuggestions.size} prodotti sotto scorta suggeriti."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.pendingShoppingItems.forEach { item ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Text(
                            text = "• ${item.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = UnitConverter.formatQuantity(item.quantity, item.unit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            if (state.lowStockSuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = LocalPantryColors.current.warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${state.lowStockSuggestions.size} articoli sotto scorta minima",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalPantryColors.current.warning
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentProductsCard(
    state: HomeDashboardUiState,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Ultimi prodotti aggiunti",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (state.recentProducts.isEmpty()) {
                Text(
                    text = "Nessun prodotto registrato ancora.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.recentProducts.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenProduct(p.product.id) }
                            .padding(vertical = 6.dp)
                    ) {
                        ProductThumbnail(
                            imagePath = p.primaryImagePath,
                            categoryIcon = p.category?.iconName ?: "category",
                            categoryColorHex = p.category?.colorHex,
                            productName = p.product.name,
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p.product.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = p.category?.name ?: "Altro",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = UnitConverter.formatQuantity(p.totalAvailableQuantity, p.product.unit),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedHouseInfoCard(
    item: HouseInfoWithPhotos,
    onOpenDetail: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val entry = item.entry
    val photos = item.sortedPhotos
    var revealSecret by remember(entry.id) { mutableStateOf(false) }
    var copiedNotice by remember(entry.id) { mutableStateOf(false) }

    LaunchedEffect(copiedNotice) {
        if (copiedNotice) {
            delay(1500)
            copiedNotice = false
        }
    }

    Card(
        onClick = onOpenDetail,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(268.dp)
            .testTag("home_pinned_info_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            HouseInfoCategoryBadge(category = entry.category)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (entry.category) {
                HouseInfoCategory.PHONE_NUMBER -> {
                    Text(
                        text = entry.primaryValue.ifBlank { "Numero non indicato" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.primaryValue.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { launchPhoneDialer(context, entry.primaryValue) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("home_info_call_${entry.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Chiama ${entry.title}",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chiama", maxLines = 1, softWrap = false)
                        }
                    }
                }

                HouseInfoCategory.MEASUREMENT -> {
                    val dims = HouseInfoFormatter.formatDimensions(entry)
                    Text(
                        text = dims.ifBlank { entry.primaryValue.ifBlank { entry.notes.ifBlank { "Apri misure" } } },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (dims.isNotBlank() && entry.primaryValue.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = entry.primaryValue,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HouseInfoCategory.PHOTO_DETAIL -> {
                    val cover = photos.firstOrNull()?.localFilePath?.takeIf { File(it).exists() }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cover != null) {
                            AsyncImage(
                                model = File(cover),
                                contentDescription = entry.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${photos.size} foto salvate",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (entry.primaryValue.isNotBlank()) {
                                Text(
                                    text = entry.primaryValue,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                HouseInfoCategory.PASSWORD_NOTE -> {
                    if (entry.primaryValue.isNotBlank()) {
                        Text(
                            text = entry.primaryValue,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (entry.secretValue.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (revealSecret) entry.secretValue else HouseInfoFormatter.maskSecret(entry.secretValue),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { revealSecret = !revealSecret },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = if (revealSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (revealSecret) "Nascondi codice" else "Mostra codice",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(entry.secretValue))
                                    copiedNotice = true
                                },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = if (copiedNotice) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copia codice",
                                    tint = if (copiedNotice) LocalPantryColors.current.fresh else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else if (entry.notes.isNotBlank()) {
                        Text(
                            text = entry.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
