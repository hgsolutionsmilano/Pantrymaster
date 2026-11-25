package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.relation.CategorySpendAggregate
import com.example.data.local.relation.TopUsedProductAggregate
import com.example.data.repository.MonthlyMovementPoint
import com.example.ui.theme.LocalPantryColors
import java.util.Locale

@Composable
fun MonthlyTrendBarChart(
    points: List<MonthlyMovementPoint>,
    modifier: Modifier = Modifier
) {
    val semantics = LocalPantryColors.current
    val purchasedColor = MaterialTheme.colorScheme.primary
    val consumedColor = semantics.fresh
    val wastedColor = semantics.expired
    val axisColor = MaterialTheme.colorScheme.outlineVariant

    val maxVal = remember(points) {
        points.maxOfOrNull { maxOf(it.purchased, it.consumed, it.wasted) }?.coerceAtLeast(1.0) ?: 1.0
    }

    Column(modifier = modifier.fillMaxWidth()) {
        PantryFlowRow(
            horizontalSpacing = 16.dp,
            verticalSpacing = 6.dp,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            ChartLegendDot(color = purchasedColor, label = "Acquistati")
            ChartLegendDot(color = consumedColor, label = "Consumati")
            ChartLegendDot(color = wastedColor, label = "Sprecati")
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            val count = points.size.coerceAtLeast(1)
            val groupWidth = size.width / count
            val barWidth = (groupWidth * 0.2f).coerceIn(6.dp.toPx(), 18.dp.toPx())
            val gap = (barWidth * 0.35f)
            val chartHeight = size.height - 10.dp.toPx()

            drawLine(
                color = axisColor,
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight),
                strokeWidth = 1.dp.toPx()
            )

            points.forEachIndexed { idx, p ->
                val centerX = groupWidth * idx + groupWidth / 2f
                val totalBarsWidth = barWidth * 3f + gap * 2f
                val startX = centerX - totalBarsWidth / 2f

                val values = listOf(
                    p.purchased to purchasedColor,
                    p.consumed to consumedColor,
                    p.wasted to wastedColor
                )
                values.forEachIndexed { bIdx, (v, col) ->
                    val h = ((v / maxVal).toFloat() * (chartHeight - 8.dp.toPx())).coerceAtLeast(
                        if (v > 0) 4.dp.toPx() else 0f
                    )
                    if (h > 0f) {
                        val x = startX + bIdx * (barWidth + gap)
                        drawRoundRect(
                            color = col,
                            topLeft = Offset(x, chartHeight - h),
                            size = Size(barWidth, h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.fillMaxWidth()
        ) {
            points.forEach { p ->
                Text(
                    text = p.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CategoryDonutChart(
    categories: List<CategorySpendAggregate>,
    modifier: Modifier = Modifier
) {
    val active = remember(categories) {
        categories.filter { it.totalConsumed + it.totalPurchased > 0.0 }
    }
    val total = remember(active) {
        active.sumOf { it.totalConsumed + it.totalPurchased }.coerceAtLeast(1.0)
    }
    val fallback = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Canvas(modifier = Modifier.size(132.dp)) {
            val stroke = 22.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            var startAngle = -90f
            active.forEach { cat ->
                val value = cat.totalConsumed + cat.totalPurchased
                val sweep = ((value / total) * 360.0).toFloat()
                if (sweep > 0.5f) {
                    drawArc(
                        color = parseColorHex(cat.categoryColor, fallback),
                        startAngle = startAngle,
                        sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                }
                startAngle += sweep
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            active.take(6).forEach { cat ->
                val share = (((cat.totalConsumed + cat.totalPurchased) / total) * 100).toInt()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(parseColorHex(cat.categoryColor, fallback))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cat.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$share%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TopProductsBarList(
    items: List<TopUsedProductAggregate>,
    modifier: Modifier = Modifier
) {
    val max = remember(items) { items.maxOfOrNull { it.totalConsumed }?.coerceAtLeast(1.0) ?: 1.0 }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items.forEach { item ->
            val progress = (item.totalConsumed / max).toFloat().coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(Locale.ITALY, "%.1f (%d uscite)", item.totalConsumed, item.movementCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
