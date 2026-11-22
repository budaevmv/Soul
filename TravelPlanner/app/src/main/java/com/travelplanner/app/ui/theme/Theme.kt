package com.travelplanner.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.travelplanner.app.R

/** Manrope — современный гротеск, встроен в приложение (работает и без сервисов Google). */
val Manrope = FontFamily(
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DB2FF),
    onPrimary = Color(0xFF0B0B10),
    primaryContainer = Color(0xFF1C2236),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Color(0xFF8BE0C2),
    tertiary = Color(0xFFF5C77E),
    background = Color(0xFF09090C),
    onBackground = Color(0xFFF3F3F6),
    surface = Color(0xFF09090C),
    onSurface = Color(0xFFF3F3F6),
    surfaceVariant = Color(0xFF17171D),
    onSurfaceVariant = Color(0xFF9898A6),
    surfaceContainerLowest = Color(0xFF0C0C10),
    surfaceContainerLow = Color(0xFF111116),
    surfaceContainer = Color(0xFF18181F),
    surfaceContainerHigh = Color(0xFF1B1B22),
    surfaceContainerHighest = Color(0xFF23232B),
    outline = Color(0xFF2C2C35),
    outlineVariant = Color(0xFF1F1F27),
    secondaryContainer = Color(0xFF16241F),
    onSecondaryContainer = Color(0xFFCDEFE2),
    tertiaryContainer = Color(0xFF2A2216),
    onTertiaryContainer = Color(0xFFFBE7C6),
    error = Color(0xFFFF7A7A),
    errorContainer = Color(0xFF2A1416),
    onErrorContainer = Color(0xFFFFDADA),
)

private val Light = lightColorScheme(
    primary = Color(0xFF3550D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EAFF),
    onPrimaryContainer = Color(0xFF101C5C),
    secondary = Color(0xFF0E8F68),
    tertiary = Color(0xFFB7791F),
    background = Color(0xFFF4F4F7),
    onBackground = Color(0xFF0E0E12),
    surface = Color(0xFFF4F4F7),
    onSurface = Color(0xFF0E0E12),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF6A6A76),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFBFD),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDEDF2),
    surfaceContainerHighest = Color(0xFFE6E6EC),
    outline = Color(0xFFDCDCE3),
    outlineVariant = Color(0xFFEAEAF0),
    secondaryContainer = Color(0xFFE3F6EE),
    onSecondaryContainer = Color(0xFF06392A),
    tertiaryContainer = Color(0xFFFFF1DC),
    onTertiaryContainer = Color(0xFF3D2706),
    error = Color(0xFFD23B3B),
    errorContainer = Color(0xFFFDE8E8),
    onErrorContainer = Color(0xFF5A0B0B),
)

private fun style(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) =
    TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

private val AppTypography = Typography(
    displayLarge = style(56, FontWeight.Light, 60, -1.5),
    displayMedium = style(44, FontWeight.Light, 50, -1.0),
    displaySmall = style(36, FontWeight.Normal, 42, -0.8),
    headlineLarge = style(32, FontWeight.Bold, 38, -0.8),
    headlineMedium = style(28, FontWeight.Bold, 34, -0.6),
    headlineSmall = style(24, FontWeight.Bold, 30, -0.4),
    titleLarge = style(20, FontWeight.Bold, 26, -0.3),
    titleMedium = style(16, FontWeight.SemiBold, 22, -0.1),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 21),
    bodySmall = style(12.5.toInt(), FontWeight.Normal, 18),
    labelLarge = style(14, FontWeight.SemiBold, 20),
    labelMedium = style(12, FontWeight.SemiBold, 16, 0.4),
    labelSmall = style(11, FontWeight.SemiBold, 14, 1.2),
)

@Composable
fun TravelTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
