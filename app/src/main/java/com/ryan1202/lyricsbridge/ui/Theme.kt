package com.ryan1202.lyricsbridge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private val LightColors = lightColorScheme(
    primary = Color(0xFFA3314E), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E1), onPrimaryContainer = Color(0xFF3F0016),
    secondary = Color(0xFF75565F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFED9E3), onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF6D5B7B), tertiaryContainer = Color(0xFFF5D9FF),
    onTertiary = Color.White, onTertiaryContainer = Color(0xFF271431),
    background = Color(0xFFFFF8F8), onBackground = Color(0xFF23191C),
    surface = Color(0xFFFFF8F8), onSurface = Color(0xFF23191C),
    surfaceVariant = Color(0xFFF2DDE3), onSurfaceVariant = Color(0xFF524249),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF0F3),
    surfaceContainer = Color(0xFFFBEAED), surfaceContainerHigh = Color(0xFFF6E4E8),
    surfaceContainerHighest = Color(0xFFF0DFE3),
    outline = Color(0xFF857279), outlineVariant = Color(0xFFD8C1C9)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C6), onPrimary = Color(0xFF64032A),
    primaryContainer = Color(0xFF83213B), onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFE4BDC8), onSecondary = Color(0xFF432932),
    secondaryContainer = Color(0xFF5C3F48), onSecondaryContainer = Color(0xFFFED9E3),
    tertiary = Color(0xFFDABDE9), tertiaryContainer = Color(0xFF544462),
    onTertiary = Color(0xFF3D2C49), onTertiaryContainer = Color(0xFFF5D9FF),
    background = Color(0xFF191113), onBackground = Color(0xFFF1DFE3),
    surface = Color(0xFF191113), onSurface = Color(0xFFF1DFE3),
    surfaceVariant = Color(0xFF524249), onSurfaceVariant = Color(0xFFD8C1C9),
    surfaceContainerLowest = Color(0xFF130C0E), surfaceContainerLow = Color(0xFF22191C),
    surfaceContainer = Color(0xFF271D20), surfaceContainerHigh = Color(0xFF32272A),
    surfaceContainerHighest = Color(0xFF3D3235),
    outline = Color(0xFF9F8B93), outlineVariant = Color(0xFF524249)
)
private val BaseTypography = Typography()
private val AppTypography = Typography(
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
)

@Composable
fun LyricsBridgeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = AppTypography, content = content)
}
