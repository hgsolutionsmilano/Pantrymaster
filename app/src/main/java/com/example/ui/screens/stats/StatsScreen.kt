package com.example.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.StatsPeriod
import com.example.domain.usecase.PantryDateUtils
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.MonthlyTrendBarChart
import com.example.ui.components.PantryDatePickerModal
import com.example.ui.components.PantryEmptyState
import com.example.ui.components.TopProductsBarList
import com.example.ui.theme.LocalPantryColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    isExpanded: Boolean,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val semantics = LocalPantryColors.current
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var tempStart by remember { mutableStateOf(state.fromMillis) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Statistiche",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StatsPeriod.entries) { p ->
                        FilterChip(
                            selected = state.period == p,
                            onClick = {
                                if (p == StatsPeriod.CUSTOM) {
                                    pickingStart = true
                                } else {
                                    viewModel.selectPeriod(p)
                                }
                            },
                            label = { Text(p.labelIt) }
                        )
                    }
                }
                if (state.period == StatsPeriod.CUSTOM) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(onClick = { pickingStart = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                        Text(
                            text = "  ${PantryDateUtils.formatDate(state.fromMillis)} - ${PantryDateUtils.formatDate(state.toMillis)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // KPI Summary
            item {
                val snap = state.snapshot
                val totalMovements = snap.purchasedCount + snap.consumedCount + snap.wastedCount
                val cards = listOf(
                    Triple("Acquistati", String.format(Locale.ITALY, "%.1f", snap.purchasedQuantity), MaterialTheme.colorScheme.primary),
                    Triple("Consumati", String.format(Locale.ITALY, "%.1f", snap.consumedQuantity), semantics.fresh),
                    Triple("Sprecati", String.format(Locale.ITALY, "%.1f", snap.wastedQuantity), semantics.expired),
                    Triple("Movimenti", totalMovements.toString(), MaterialTheme.colorScheme.tertiary)
                )
                if (isExpanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        cards.forEach { (title, valStr, col) ->
                            StatMetricCard(title = title, value = valStr, accent = col, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatMetricCard(cards[0].first, cards[0].second, cards[0].third, Modifier.weight(1f))
                            StatMetricCard(cards[1].first, cards[1].second, cards[1].third, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatMetricCard(cards[2].first, cards[2].second, cards[2].third, Modifier.weight(1f))
                            StatMetricCard(cards[3].first, cards[3].second, cards[3].third, Modifier.weight(1f))
                        }
                    }
                }
            }

            val snap = state.snapshot
            if (snap.monthlyTrend.isEmpty() && snap.categoryAggregates.isEmpty() && snap.topUsedProducts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PantryEmptyState(
                            icon = Icons.Default.BarChart,
                            title = "Nessun dato nel periodo selezionato",
                            subtitle = "I grafici si aggiorneranno automaticamente registrando carichi, consumi e acquisti."
                        )
                    }
                }
            } else {
                if (snap.monthlyTrend.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Andamento mensile",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                MonthlyTrendBarChart(points = snap.monthlyTrend)
                            }
                        }
                    }
                }

                if (snap.categoryAggregates.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Consumi e carichi per categoria",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                CategoryDonutChart(categories = snap.categoryAggregates)
                            }
                        }
                    }
                }

                if (snap.topUsedProducts.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Prodotti maggiormente utilizzati",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TopProductsBarList(items = snap.topUsedProducts)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (pickingStart) {
        PantryDatePickerModal(
            initialDateMillis = state.fromMillis,
            onDateSelected = { picked ->
                if (picked != null) {
                    tempStart = picked
                    pickingEnd = true
                }
            },
            onDismiss = { pickingStart = false }
        )
    }
    if (pickingEnd) {
        PantryDatePickerModal(
            initialDateMillis = state.toMillis,
            onDateSelected = { picked ->
                if (picked != null) {
                    viewModel.setCustomRange(tempStart, picked)
                }
            },
            onDismiss = { pickingEnd = false }
        )
    }
}

@Composable
private fun StatMetricCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}
