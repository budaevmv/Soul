package com.travelplanner.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelplanner.app.AppContainer
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.PackingList
import com.travelplanner.app.data.Stay
import com.travelplanner.app.data.StopWeather
import com.travelplanner.app.data.WeatherService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Погода в каждом городе поездки + список вещей, который собирается сам из погоды и страны. */
@Composable
fun WeatherPackingTab(c: AppContainer, tripId: Long, flights: List<Flight>, stays: List<Stay>) {
    val context = LocalContext.current
    val stops = remember(flights, stays) { c.itinerary.stops(flights, stays) }
    var weather by remember { mutableStateOf<List<StopWeather>?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(stops) {
        loading = true
        weather = stops.mapNotNull { s ->
            val from = Instant.ofEpochMilli(s.start).atZone(ZoneId.of(s.zone)).toLocalDate()
            val to = Instant.ofEpochMilli(s.end).atZone(ZoneId.of(s.zone)).toLocalDate().coerceAtLeast(from)
            c.weather.forStop(s.city, null, from, to)
        }
        loading = false
    }
    val countries = remember(stops) {
        stops.mapNotNull { it.country }.distinct().map { it to c.ref.country(it) }
    }
    val home = flights.minByOrNull { it.departureMillis }?.let { c.itinerary.departureCountry(it) }
    val abroad = countries.any { it.first != home && it.first != "RU" }
    val start = (flights.map { it.departureMillis } + stays.map { it.checkInMillis }).minOrNull()
    val end = (flights.map { it.arrivalMillis ?: it.departureMillis } + stays.map { it.checkOutMillis }).maxOrNull()
    val tripDays = if (start != null && end != null) ((end - start) / 86_400_000L + 1).toInt() else 3
    val sections = remember(weather, countries) {
        PackingList.build(weather.orEmpty(), countries, tripDays, abroad, flights.any { !it.isTrain }, !Taxi.hasGoogleServices(context))
    }
    val prefs = remember { context.getSharedPreferences("checklist", android.content.Context.MODE_PRIVATE) }

    LazyColumn(contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionLabel("погода в городах поездки", Modifier.padding(top = 6.dp)) }
        if (loading) {
            item {
                Panel {
                    Text("Загружаю прогноз…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
            }
        } else if (weather.isNullOrEmpty()) {
            item {
                Text(
                    if (stops.isEmpty()) "Добавьте билеты — появится погода в городах поездки."
                    else "Нет интернета — прогноз не загрузился. Список вещей ниже составлен без учёта погоды.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(weather.orEmpty()) { i, w -> Appear(i, w.city + w.from) { WeatherCard(w) } }
        item { SectionLabel("что взять с собой", Modifier.padding(top = 16.dp)) }
        itemsIndexed(sections) { i, section ->
            Appear(i + 2, section.title) {
                Panel {
                    Text(section.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
                    section.items.forEach { item ->
                        val key = "$tripId:${item.hashCode()}"
                        var checked by remember(key) { mutableStateOf(prefs.getBoolean(key, false)) }
                        CheckRow(item, checked) {
                            checked = !checked
                            prefs.edit().putBoolean(key, checked).apply()
                        }
                    }
                }
            }
        }
    }
}

/** Пункт списка: круглая галочка, отмеченное плавно приглушается и зачёркивается. */
@Composable
private fun CheckRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    val alpha by animateFloatAsState(if (checked) 0.4f else 1f, tween(250), label = "check")
    Row(Modifier.fillMaxWidth().pressable(onToggle).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Crossfade(checked, label = "checkIcon") { on ->
            Icon(
                if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null, Modifier.size(22.dp),
                tint = if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(alpha),
            textDecoration = if (checked) TextDecoration.LineThrough else null,
        )
    }
}

fun weatherIcon(code: Int?): ImageVector = when (code) {
    0 -> Icons.Outlined.WbSunny
    1, 2 -> Icons.Outlined.WbCloudy
    3, 45, 48 -> Icons.Outlined.Cloud
    in 51..67, in 80..82 -> Icons.Outlined.WaterDrop
    in 71..77, 85, 86 -> Icons.Outlined.AcUnit
    in 95..99 -> Icons.Outlined.Thunderstorm
    else -> Icons.Outlined.Cloud
}

@Composable
private fun WeatherCard(w: StopWeather) {
    val fmt = DateTimeFormatter.ofPattern("EE d", Locale.forLanguageTag("ru"))
    val main = w.days.groupingBy { it.code }.eachCount().maxByOrNull { it.value }?.key
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel(if (w.forecast) "прогноз" else "обычно в эти даты")
                Text(w.city, style = MaterialTheme.typography.headlineSmall)
                Text(
                    WeatherService.describe(main).second.replaceFirstChar { it.uppercase() } +
                        if (w.rainyDays > 0) " · дождь ${w.rainyDays} дн." else " · без осадков",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Icon(weatherIcon(main), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.tertiary)
                Text("${w.maxT.roundToInt()}°", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light)
                Text("ночью ${w.minT.roundToInt()}°", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            w.days.take(16).forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(LocalDate.parse(d.date).format(fmt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(weatherIcon(d.code), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Text("${d.tMax.roundToInt()}°", style = MaterialTheme.typography.titleSmall)
                    Text("${d.tMin.roundToInt()}°", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
