package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = IndigoSecondary,
    onSecondary = Color.White,
    secondaryContainer = IndigoContainer,
    onSecondaryContainer = OnIndigoContainer,
    tertiary = EmeraldSolution,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    error = RoseError,
    onError = Color.White,
    errorContainer = RoseContainer,
    onErrorContainer = OnRoseContainer,
    background = PaperBackground,
    onBackground = InkPrimary,
    surface = PaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = OutlineSubtle,
    outlineVariant = OutlineSubtle
)

private val DarkColorScheme = darkColorScheme(
    primary = SkyPrimaryDark,
    onPrimary = Color(0xFF0A192F),
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = OnCobaltContainerDark,
    secondary = IndigoSecondaryDark,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = IndigoContainerDark,
    onSecondaryContainer = OnIndigoContainerDark,
    tertiary = EmeraldDark,
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = OnEmeraldContainerDark,
    error = RoseDark,
    onError = Color(0xFF450A0A),
    errorContainer = RoseContainerDark,
    onErrorContainer = OnRoseContainerDark,
    background = DarkBackground,
    onBackground = DarkInkPrimary,
    surface = DarkSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline
)

val AlgeBoxShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun AlgeBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AlgeBoxTypography,
        shapes = AlgeBoxShapes,
        content = content
    )
}
