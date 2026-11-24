package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemePreference

data class PantrySemanticColors(
    val fresh: Color,
    val freshContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val expired: Color,
    val expiredContainer: Color
)

val LocalPantryColors = staticCompositionLocalOf {
    PantrySemanticColors(
        fresh = StatusFreshLight,
        freshContainer = StatusFreshContainerLight,
        warning = StatusWarningLight,
        warningContainer = StatusWarningContainerLight,
        expired = StatusExpiredLight,
        expiredContainer = StatusExpiredContainerLight
    )
}

private val PantryLightColorScheme = lightColorScheme(
    primary = ForestGreenPrimary,
    onPrimary = WarmWhiteOnPrimary,
    primaryContainer = SoftSageContainer,
    onPrimaryContainer = DeepForestOnContainer,
    secondary = OliveSageSecondary,
    onSecondary = WarmCreamOnSecondary,
    secondaryContainer = OatSageSecondaryContainer,
    onSecondaryContainer = DarkMossOnSecondaryContainer,
    tertiary = WarmTerracottaTertiary,
    onTertiary = OnTerracotta,
    tertiaryContainer = SoftApricotContainer,
    onTertiaryContainer = DeepTerracottaOnContainer,
    background = WarmCreamBackground,
    onBackground = CharcoalInk,
    surface = WarmAlabasterSurface,
    onSurface = CharcoalInk,
    surfaceVariant = CreamSurfaceContainer,
    onSurfaceVariant = MutedOliveInk,
    surfaceContainerLowest = CreamSurfaceContainerLowest,
    surfaceContainerLow = CreamSurfaceContainerLow,
    surfaceContainer = CreamSurfaceContainer,
    surfaceContainerHigh = CreamSurfaceContainerHigh,
    surfaceContainerHighest = CreamSurfaceContainerHighest,
    outline = SageOutline,
    outlineVariant = SoftSageOutlineVariant,
    error = StatusExpiredLight,
    onError = Color.White,
    errorContainer = StatusExpiredContainerLight,
    onErrorContainer = Color(0xFF4A0E0E)
)

private val PantryDarkColorScheme = darkColorScheme(
    primary = SageDarkPrimary,
    onPrimary = ForestDarkOnPrimary,
    primaryContainer = MossDarkContainer,
    onPrimaryContainer = MintLightOnContainer,
    secondary = SageMistSecondary,
    onSecondary = DarkPineOnSecondary,
    secondaryContainer = PineDarkContainer,
    onSecondaryContainer = PaleSageOnContainer,
    tertiary = WarmAmberTertiary,
    onTertiary = DeepBrownOnTertiary,
    tertiaryContainer = BurntAmberContainer,
    onTertiaryContainer = PalePeachOnContainer,
    background = BotanicalNightBackground,
    onBackground = WarmIvoryInk,
    surface = BotanicalNightSurface,
    onSurface = WarmIvoryInk,
    surfaceVariant = NightSurfaceContainer,
    onSurfaceVariant = MutedSageInk,
    surfaceContainerLowest = BotanicalNightBackground,
    surfaceContainerLow = NightSurfaceLow,
    surfaceContainer = NightSurfaceContainer,
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = NightSurfaceHighest,
    outline = NightOutline,
    outlineVariant = NightOutlineVariant,
    error = StatusExpiredDark,
    onError = Color(0xFF490B0B),
    errorContainer = StatusExpiredContainerDark,
    onErrorContainer = Color(0xFFFCDAD7)
)

val PantryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PantryMasterTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = if (isDark) PantryDarkColorScheme else PantryLightColorScheme
    val semanticColors = if (isDark) {
        PantrySemanticColors(
            fresh = StatusFreshDark,
            freshContainer = StatusFreshContainerDark,
            warning = StatusWarningDark,
            warningContainer = StatusWarningContainerDark,
            expired = StatusExpiredDark,
            expiredContainer = StatusExpiredContainerDark
        )
    } else {
        PantrySemanticColors(
            fresh = StatusFreshLight,
            freshContainer = StatusFreshContainerLight,
            warning = StatusWarningLight,
            warningContainer = StatusWarningContainerLight,
            expired = StatusExpiredLight,
            expiredContainer = StatusExpiredContainerLight
        )
    }

    CompositionLocalProvider(LocalPantryColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PantryTypography,
            shapes = PantryShapes,
            content = content
        )
    }
}
