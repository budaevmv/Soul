package com.travelplanner.app.ui

import androidx.compose.foundation.background
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

    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Погода", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (loading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Загружаю прогноз…")
                }
            }
        } else if (weather.isNullOrEmpty()) {
            item { Text(if (stops.isEmpty()) "Добавьте билеты — появится погода в городах поездки." else "Не удалось загрузить погоду — нужен интернет. Список вещей ниже составлен без учёта погоды.") }
        }
        items(weather.orEmpty()) { w -> WeatherCard(w) }
        item { Text("Что взять с собой", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(sections) { section ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("${section.emoji}  ${section.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    section.items.forEach { item ->
                        val key = "$tripId:${item.hashCode()}"
                        var checked by remember(key) { mutableStateOf(prefs.getBoolean(key, false)) }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                checked = !checked
                                prefs.edit().putBoolean(key, checked).apply()
                            }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked, { checked = it; prefs.edit().putBoolean(key, it).apply() })
                            Text(item, style = MaterialTheme.typography.bodyMedium, color = if (checked) MaterialTheme.colorScheme.outline else Color.Unspecified)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(w: StopWeather) {
    val fmt = DateTimeFormatter.ofPattern("EE d", Locale.forLanguageTag("ru"))
    val warm = w.maxT >= 22
    val colors = if (warm) listOf(Color(0xFFF59E0B), Color(0xFFEF4444)) else if (w.maxT >= 10) listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)) else listOf(Color(0xFF64748B), Color(0xFF1E3A8A))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(colors)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val (emoji, _) = WeatherService.describe(w.days.groupingBy { it.code }.eachCount().maxByOrNull { it.value }?.key)
        Text("$emoji  ${w.city}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "${w.minT.roundToInt()}…${w.maxT.roundToInt()}°C" + (if (w.rainyDays > 0) " · дождь ${w.rainyDays} дн." else " · без осадков") +
                if (w.forecast) "" else " · обычно в эти даты (прогноз появится за 2 недели)",
            color = Color.White.copy(alpha = 0.92f),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            w.days.take(16).forEach { d ->
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(LocalDate.parse(d.date).format(fmt), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Text(WeatherService.describe(d.code).first, style = MaterialTheme.typography.titleMedium)
                    Text("${d.tMax.roundToInt()}°", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${d.tMin.roundToInt()}°", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
