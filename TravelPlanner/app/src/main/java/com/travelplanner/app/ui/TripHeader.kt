package com.travelplanner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelplanner.app.AppContainer
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Stay
import com.travelplanner.app.ui.theme.TripColors

/** Яркая шапка поездки: маршрут с флагами, даты, обратный отсчёт и что входит в поездку. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripHeader(c: AppContainer, flights: List<Flight>, stays: List<Stay>, now: Long) {
    val moves = flights.sortedBy { it.departureMillis }
    val start = (moves.map { it.departureMillis } + stays.map { it.checkInMillis }).minOrNull()
    val end = (moves.map { it.arrivalMillis ?: it.departureMillis } + stays.map { it.checkOutMillis }).maxOrNull()
    val zone = moves.firstOrNull()?.departureZone ?: stays.firstOrNull()?.zone ?: java.time.ZoneId.systemDefault().id
    val stops = c.itinerary.stops(flights, stays)
    val route = stops.joinToString("  →  ") { s -> (s.country?.let { c.ref.country(it)?.flag } ?: "") + " " + s.city }.trim()
    val status = when {
        start == null -> "Добавьте билеты и брони"
        now < start -> "До поездки ${Format.countdown(start, now).removePrefix("через ")}"
        end != null && now <= end -> "Вы в поездке ✈"
        else -> "Поездка завершена"
    }
    Box(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(TripColors.HeaderStart, TripColors.HeaderEnd))),
    ) {
        Icon(
            if (moves.any { !it.isTrain }) Icons.Default.Flight else Icons.Default.Train, null,
            Modifier.size(150.dp).align(Alignment.TopEnd).offset(x = 30.dp, y = (-20).dp).rotate(35f),
            tint = Color.White.copy(alpha = 0.13f),
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(status, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            if (route.isNotBlank()) Text(route, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (start != null && end != null) {
                Text("${Format.shortDate(start, zone)} — ${Format.shortDate(end, zone)}", color = Color.White.copy(alpha = 0.9f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val planes = moves.count { !it.isTrain }
                val trains = moves.count { it.isTrain }
                if (planes > 0) Pill("✈ $planes")
                if (trains > 0) Pill("🚆 $trains")
                if (stays.isNotEmpty()) Pill("🏨 ${stays.size}")
                if (stops.isNotEmpty()) Pill("📍 ${stops.size} ${plural(stops.size, "город", "города", "городов")}")
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text, color = Color.White, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
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
