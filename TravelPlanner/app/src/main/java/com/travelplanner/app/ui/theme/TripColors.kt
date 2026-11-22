package com.travelplanner.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** Приглушённые цвета типов пунктов: только для иконок и тонких акцентов. */
object TripColors {
    val Flight: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF9DB2FF) else Color(0xFF3550D8)
    val Train: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF8BE0C2) else Color(0xFF0E8F68)
    val Hotel: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFFC9B6FF) else Color(0xFF6D4AE0)
    val Road: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFFF5C77E) else Color(0xFFB7791F)
    val Layover: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF8FD3F5) else Color(0xFF1C7FB0)
    val Checkout: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF9898A6) else Color(0xFF6A6A76)

    /** Мягкое «сияние» шапки. */
    val GlowA = Color(0xFF5B6CFF)
    val GlowB = Color(0xFF8B5CF6)
    val GlowC = Color(0xFF22D3EE)
}
