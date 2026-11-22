package com.travelplanner.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelplanner.app.AppContainer
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Stay
import com.travelplanner.app.ui.theme.TripColors
import kotlin.math.cos
import kotlin.math.sin

/** Фон с мягким «сиянием», которое медленно перетекает. */
@Composable
fun Modifier.aurora(alpha: Float = 0.55f): Modifier {
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val base = Color(0xFF101016)
    return this.background(base).drawBehind {
        fun glow(color: Color, cx: Float, cy: Float, r: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = Offset(cx, cy), radius = r),
            radius = r, center = Offset(cx, cy),
        )
        val w = size.width
        val h = size.height
        glow(TripColors.GlowA, w * (0.15f + 0.1f * cos(t)), h * (0.2f + 0.15f * sin(t)), w * 0.7f)
        glow(TripColors.GlowB, w * (0.85f + 0.08f * sin(t)), h * (0.35f + 0.2f * cos(t)), w * 0.6f)
        glow(TripColors.GlowC, w * (0.6f + 0.15f * cos(t + 1f)), h * (1.1f + 0.1f * sin(t)), w * 0.5f)
    }
}

/** Шапка поездки: отсчёт, маршрут, даты и состав — без картинок, на «сиянии». */
@Composable
fun TripHeader(c: AppContainer, flights: List<Flight>, stays: List<Stay>, now: Long) {
    val moves = flights.sortedBy { it.departureMillis }
    val start = (moves.map { it.departureMillis } + stays.map { it.checkInMillis }).minOrNull()
    val end = (moves.map { it.arrivalMillis ?: it.departureMillis } + stays.map { it.checkOutMillis }).maxOrNull()
    val zone = moves.firstOrNull()?.departureZone ?: stays.firstOrNull()?.zone ?: java.time.ZoneId.systemDefault().id
    val stops = c.itinerary.stops(flights, stays)
    val (label, big) = when {
        start == null -> "поездка" to "Пусто"
        now < start -> "до поездки" to Format.countdown(start, now).removePrefix("через ")
        end != null && now <= end -> "сейчас" to "В пути"
        else -> "поездка" to "Завершена"
    }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 18.dp).clip(RoundedCornerShape(30.dp)).aurora().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        AnimatedContent(
            big,
            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
            label = "countdown",
        ) { value ->
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light, color = Color.White)
        }
        if (stops.isNotEmpty()) {
            Text(
                stops.joinToString("  ·  ") { it.city }, style = MaterialTheme.typography.titleMedium,
                color = Color.White, modifier = Modifier.padding(top = 10.dp),
            )
        }
        val facts = listOfNotNull(
            if (start != null && end != null) "${Format.shortDate(start, zone)} — ${Format.shortDate(end, zone)}" else null,
            moves.count { !it.isTrain }.takeIf { it > 0 }?.let { "$it ${plural(it, "перелёт", "перелёта", "перелётов")}" },
            moves.count { it.isTrain }.takeIf { it > 0 }?.let { "$it ${plural(it, "поезд", "поезда", "поездов")}" },
            stays.size.takeIf { it > 0 }?.let { "$it ${plural(it, "отель", "отеля", "отелей")}" },
        )
        Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
    }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}
