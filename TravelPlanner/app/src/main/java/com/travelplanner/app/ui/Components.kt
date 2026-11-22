package com.travelplanner.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Лёгкое пружинное сжатие при нажатии. */
@Composable
fun Modifier.pressable(onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(interactionSource = source, indication = null, onClick = onClick)
}

/**
 * Парящая карточка: без рамок, мягкая глубокая тень и едва заметный блик сверху,
 * будто плашка приподнята над фоном.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    elevation: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.2f
    val highlight = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.7f)
    Column(
        modifier.fillMaxWidth().pressable(onClick)
            .shadow(
                elevation, shape,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.9f else 0.18f),
                spotColor = Color.Black.copy(alpha = if (dark) 1f else 0.28f),
            )
            .background(color, shape)
            .background(Brush.verticalGradient(listOf(highlight, Color.Transparent), endY = 220f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Кнопка-«таблетка» без обводки. */
@Composable
fun Pill(icon: ImageVector?, text: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface, filled: Boolean = false) {
    val bg = if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val fg = if (filled) MaterialTheme.colorScheme.surface else tint
    Row(
        Modifier.pressable(onClick).clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(17.dp), tint = fg)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** Подпись раздела: мелкие прописные с разрядкой. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier,
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Крупный заголовок экрана. */
@Composable
fun ScreenTitle(text: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp, bottom = 12.dp)) {
        Text(text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Появление элемента: плавно проявляется и чуть всплывает, по очереди с соседями. */
@Composable
fun Appear(index: Int, key: Any = index, content: @Composable () -> Unit) {
    var shown by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (!shown) {
            delay((index.coerceAtMost(10) * 45).toLong())
            shown = true
        }
    }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(420), label = "appear")
    Box(Modifier.graphicsLayer { alpha = progress; translationY = (1 - progress) * 36f }) { content() }
}
