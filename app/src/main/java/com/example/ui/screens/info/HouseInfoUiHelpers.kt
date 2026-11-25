package com.example.ui.screens.info

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.HouseInfoCategory
import com.example.ui.theme.LocalPantryColors

fun iconForHouseInfoCategory(category: HouseInfoCategory): ImageVector = when (category) {
    HouseInfoCategory.PHONE_NUMBER -> Icons.Default.Phone
    HouseInfoCategory.MEASUREMENT -> Icons.Default.Straighten
    HouseInfoCategory.PHOTO_DETAIL -> Icons.Default.PhotoLibrary
    HouseInfoCategory.PASSWORD_NOTE -> Icons.Default.Key
}

@Composable
fun houseInfoCategoryColors(category: HouseInfoCategory): Pair<Color, Color> {
    val semantics = LocalPantryColors.current
    return when (category) {
        HouseInfoCategory.PHONE_NUMBER ->
            semantics.freshContainer to semantics.fresh
        HouseInfoCategory.MEASUREMENT ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        HouseInfoCategory.PHOTO_DETAIL ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        HouseInfoCategory.PASSWORD_NOTE ->
            semantics.warningContainer to semantics.warning
    }
}

@Composable
fun HouseInfoCategoryBadge(
    category: HouseInfoCategory,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = houseInfoCategoryColors(category)
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = iconForHouseInfoCategory(category),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = category.labelIt,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun launchPhoneDialer(context: Context, phoneNumber: String): Boolean {
    val cleaned = phoneNumber.trim().replace(Regex("[^+0-9*#]"), "")
    if (cleaned.isBlank()) return false
    return runCatching {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned"))
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

fun shareHouseInfoText(context: Context, title: String, text: String): Boolean {
    if (text.isBlank()) return false
    return runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Condividi scheda casa"))
        true
    }.getOrDefault(false)
}
