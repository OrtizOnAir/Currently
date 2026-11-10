package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightBroadsheetScheme = lightColorScheme(
    primary = EditorialCrimson,
    onPrimary = Color.White,
    primaryContainer = EditorialCrimsonLight,
    onPrimaryContainer = EditorialCrimson,
    secondary = OxfordPressNavy,
    onSecondary = Color.White,
    secondaryContainer = NewsprintSurfaceElevated,
    onSecondaryContainer = OxfordPressNavy,
    tertiary = AntiqueBrass,
    onTertiary = PrinterInkBlack,
    background = NewsprintBackground,
    onBackground = PrinterInkBlack,
    surface = NewsprintSurface,
    onSurface = PrinterInkBlack,
    surfaceVariant = NewsprintSurfaceElevated,
    onSurfaceVariant = PrinterInkCharcoal,
    outline = HairlineRuleLight,
    outlineVariant = PrinterInkMuted
)

private val DarkBroadsheetScheme = darkColorScheme(
    primary = CrimsonAccentDark,
    onPrimary = DarkPressBackground,
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = IvoryInk,
    secondary = BrassAccentDark,
    onSecondary = DarkPressBackground,
    secondaryContainer = DarkPressSurfaceElevated,
    onSecondaryContainer = IvoryInk,
    tertiary = BrassAccentDark,
    onTertiary = DarkPressBackground,
    background = DarkPressBackground,
    onBackground = IvoryInk,
    surface = DarkPressSurface,
    onSurface = IvoryInk,
    surfaceVariant = DarkPressSurfaceElevated,
    onSurfaceVariant = IvoryInkSecondary,
    outline = HairlineRuleDark,
    outlineVariant = IvoryInkMuted
)

@Composable
fun FolioPressTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkBroadsheetScheme else LightBroadsheetScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
