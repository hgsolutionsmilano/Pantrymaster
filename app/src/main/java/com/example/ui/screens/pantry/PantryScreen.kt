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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.relation.ProductWithDetails
import com.example.domain.model.ExpiryType
import com.example.domain.model.PantrySortOption
import com.example.domain.usecase.UnitConverter
import com.example.ui.components.ExpiryBadge
import com.example.ui.components.PantryEmptyState
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.iconForLocationType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(
    isExpanded: Boolean,
    onOpenProduct: (String) -> Unit,
    onAddProduct: () -> Unit,
    onOpenScanner: () -> Unit,
    viewModel: PantryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Dispensa",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenScanner,
                        modifier = Modifier.testTag("pantry_scan_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scansiona barcode")
                    }
                    IconButton(
                        onClick = { viewModel.toggleGridView() },
                        modifier = Modifier.testTag("pantry_toggle_view_button")
                    ) {
                        Icon(
                            imageVector = if (state.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = if (state.isGridView) "Vista lista" else "Vista griglia"
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("pantry_sort_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordina prodotti")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            PantrySortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.labelIt,
                                            fontWeight = if (option == state.filter.sortOption) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.selectSort(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddProduct,
                modifier = Modifier.testTag("pantry_add_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuovo prodotto")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = state.filter.searchQuery,
                onValueChange = viewModel::updateSearch,
                placeholder = { Text("Cerca prodotto, marca o barcode...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filter.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearch("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Pulisci ricerca")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("pantry_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Location Filters
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.filter.selectedLocationId == null,
                        onClick = { viewModel.selectLocation(null) },
                        label = { Text("Tutte le posizioni") }
                    )
                }
                items(state.locations, key = { it.id }) { loc ->
                    FilterChip(
                        selected = state.filter.selectedLocationId == loc.id,
                        onClick = {
                            viewModel.selectLocation(
                                if (state.filter.selectedLocationId == loc.id) null else loc.id
                            )
                        },
                        label = { Text(loc.name) },
                        leadingIcon = {
                            Icon(
                                iconForLocationType(loc.locationType),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // Category Filters
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.filter.selectedCategoryId == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("Tutte le categorie") }
                    )
                }
                items(state.categories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = state.filter.selectedCategoryId == cat.id,
                        onClick = {
                            viewModel.selectCategory(
                                if (state.filter.selectedCategoryId == cat.id) null else cat.id
                            )
                        },
                        label = { Text(cat.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.products.isEmpty()) {
                PantryEmptyState(
                    icon = Icons.Default.Inventory2,
                    title = if (state.filter.searchQuery.isNotBlank() ||
                        state.filter.selectedCategoryId != null ||
                        state.filter.selectedLocationId != null
                    ) "Nessun prodotto corrisponde ai filtri" else "La dispensa è vuota",
                    subtitle = "Aggiungi i tuoi alimenti con lotti e date di scadenza per iniziare.",
                    actionLabel = "Aggiungi prodotto",
                    onAction = onAddProduct,
                    modifier = Modifier.weight(1f)
                )
            } else if (state.isGridView || isExpanded) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (isExpanded) 240.dp else 164.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.products, key = { it.product.id }) { item ->
                        ProductGridCard(
                            item = item,
                            onClick = { onOpenProduct(item.product.id) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.products, key = { it.product.id }) { item ->
                        ProductListCard(
                            item = item,
                            onClick = { onOpenProduct(item.product.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductListCard(
    item: ProductWithDetails,
    onClick: () -> Unit
) {
    val activeBatches = item.activeBatches
    val earliestBatch = activeBatches.firstOrNull()
    val totalQty = item.totalAvailableQuantity

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${item.product.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            ProductThumbnail(
                imagePath = item.primaryImagePath,
                categoryIcon = item.category?.iconName ?: "category",
                categoryColorHex = item.category?.colorHex,
                productName = item.product.name,
                size = 54.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = buildString {
                    if (item.product.brand.isNotBlank()) append("${item.product.brand} • ")
                    append(item.category?.name ?: "Altro")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (earliestBatch != null) {
                        ExpiryBadge(
                            expiryDateMillis = earliestBatch.expiryDate,
                            expiryType = earliestBatch.expiryType
                        )
                    } else {
                        ExpiryBadge(
                            expiryDateMillis = null,
                            expiryType = ExpiryType.USE_BY
                        )
                    }
                    Text(
                        text = "${activeBatches.size} lotti",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = UnitConverter.formatQuantity(totalQty, item.product.unit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (totalQty <= item.product.minStockQuantity && item.product.minStockQuantity > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = item.defaultLocation?.name ?: "Dispensa",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProductGridCard(
    item: ProductWithDetails,
    onClick: () -> Unit
) {
    val activeBatches = item.activeBatches
    val earliestBatch = activeBatches.firstOrNull()
    val totalQty = item.totalAvailableQuantity

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_grid_card_${item.product.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                ProductThumbnail(
                    imagePath = item.primaryImagePath,
                    categoryIcon = item.category?.iconName ?: "category",
                    categoryColorHex = item.category?.colorHex,
                    productName = item.product.name,
                    size = 46.dp
                )
                Text(
                    text = UnitConverter.formatQuantity(totalQty, item.product.unit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.product.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.product.brand.ifBlank { item.category?.name ?: "Altro" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExpiryBadge(
                expiryDateMillis = earliestBatch?.expiryDate,
                expiryType = earliestBatch?.expiryType ?: ExpiryType.USE_BY
            )
        }
    }
}
